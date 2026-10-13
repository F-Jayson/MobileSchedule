package com.example.mobileschedule

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.mobileschedule.data.local.AppDatabase
import com.example.mobileschedule.data.repository.OfflineScheduleRepository
import com.example.mobileschedule.data.repository.ScheduleManagementService
import com.example.mobileschedule.ui.settings.SettingsHomeViewModel
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PreferenceWriteOrderingTest {
    @Test fun quickImportKeepsTheLastSelectionWhenAnEarlierWriteIsSlow() = verifyOrder("quick_import")
    @Test fun automaticUpdateKeepsTheLastSelectionWhenAnEarlierWriteIsSlow() = verifyOrder("auto_check_update")

    private fun verifyOrder(key: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val base = instrumentation.targetContext
        val real = base.getSharedPreferences("preference-write-ordering-$key", Context.MODE_PRIVATE)
        real.edit().clear().commit()
        val firstStarted = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val secondStarted = CountDownLatch(1)
        val completed = CountDownLatch(2)
        val writes = AtomicInteger()
        val delayed = object : SharedPreferences by real {
            override fun edit(): SharedPreferences.Editor {
                val editor = real.edit()
                return object : SharedPreferences.Editor by editor {
                    // Keep the real putBoolean/commit path while delaying the first disk write.
                    override fun putBoolean(name: String?, value: Boolean): SharedPreferences.Editor {
                        editor.putBoolean(name, value)
                        return this
                    }
                    override fun commit(): Boolean {
                        if (writes.getAndIncrement() == 0) {
                            firstStarted.countDown()
                            check(releaseFirst.await(10, TimeUnit.SECONDS))
                        } else secondStarted.countDown()
                        return editor.commit().also { completed.countDown() }
                    }
                }
            }
        }
        val context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                if (name == "app_preferences") delayed else super.getSharedPreferences(name, mode)
        }
        val database = Room.inMemoryDatabaseBuilder(base, AppDatabase::class.java).build()
        val store = ViewModelStore()
        lateinit var viewModel: SettingsHomeViewModel
        fun select(value: Boolean) = instrumentation.runOnMainSync {
            if (key == "quick_import") viewModel.setQuickImport(value) else viewModel.setAutoUpdate(value)
        }
        try {
            instrumentation.runOnMainSync {
                viewModel = SettingsHomeViewModel(OfflineScheduleRepository(database), context,
                    ScheduleManagementService(database))
                store.put("preferences", viewModel)
            }
            select(true)
            assertTrue(firstStarted.await(5, TimeUnit.SECONDS))
            select(false)
            secondStarted.await(500, TimeUnit.MILLISECONDS)
            releaseFirst.countDown()
            assertTrue(completed.await(5, TimeUnit.SECONDS))
            assertFalse("The user's last disabled selection must survive storage delays", real.getBoolean(key, true))
        } finally {
            releaseFirst.countDown()
            instrumentation.runOnMainSync { store.clear() }
            database.close()
            real.edit().clear().commit()
        }
    }
}
