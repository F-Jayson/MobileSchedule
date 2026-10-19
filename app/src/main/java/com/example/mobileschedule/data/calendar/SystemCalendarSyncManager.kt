package com.example.mobileschedule.data.calendar

import android.Manifest
import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class CalendarSyncResult(
    val success: Boolean,
    val insertedCount: Int = 0,
    val deletedCount: Int = 0,
    val message: String,
)

@Singleton
class SystemCalendarSyncManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    companion object {
        const val ACCOUNT_NAME = "MobileSchedule_FJNU"
        const val ACCOUNT_TYPE = CalendarContract.ACCOUNT_TYPE_LOCAL
        const val CALENDAR_NAME = "FJNU_Schedule"
        const val CALENDAR_DISPLAY_NAME = "福建师大课表"
        const val CALENDAR_COLOR = 0xFF1976D2.toInt()
        const val CALENDAR_TIMEZONE = "Asia/Shanghai"
        const val BATCH_SIZE = 200

        fun hasPermissions(context: Context): Boolean {
            val read = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CALENDAR,
            ) == PackageManager.PERMISSION_GRANTED
            val write = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_CALENDAR,
            ) == PackageManager.PERMISSION_GRANTED
            return read && write
        }

        fun <T> chunkList(list: List<T>, size: Int = BATCH_SIZE): List<List<T>> {
            if (list.isEmpty()) return emptyList()
            return list.chunked(size)
        }

        fun getEventsSyncUri(): Uri = CalendarContract.Events.CONTENT_URI.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, ACCOUNT_NAME)
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, ACCOUNT_TYPE)
            .build()

        fun buildInsertOperations(
            calendarId: Long,
            events: List<CalendarEventItem>,
        ): List<ContentProviderOperation> {
            val insertUri = getEventsSyncUri()
            return events.map { event ->
                ContentProviderOperation.newInsert(insertUri)
                    .withValue(CalendarContract.Events.CALENDAR_ID, calendarId)
                    .withValue(CalendarContract.Events.TITLE, event.summary)
                    .withValue(CalendarContract.Events.EVENT_LOCATION, event.location ?: "")
                    .withValue(CalendarContract.Events.DESCRIPTION, event.description ?: "")
                    .withValue(CalendarContract.Events.DTSTART, event.toStartEpochMillis())
                    .withValue(CalendarContract.Events.DTEND, event.toEndEpochMillis())
                    .withValue(CalendarContract.Events.EVENT_TIMEZONE, CALENDAR_TIMEZONE)
                    .withValue(CalendarContract.Events.STATUS, CalendarContract.Events.STATUS_CONFIRMED)
                    .withValue(CalendarContract.Events.SYNC_DATA1, event.uid)
                    .build()
            }
        }
    }

    fun hasPermissions(): Boolean = hasPermissions(context)

    fun findCalendarId(): Long? {
        val uri = CalendarContract.Calendars.CONTENT_URI
        val projection = arrayOf(CalendarContract.Calendars._ID)
        val selection = "${CalendarContract.Calendars.ACCOUNT_NAME} = ? AND ${CalendarContract.Calendars.ACCOUNT_TYPE} = ?"
        val selectionArgs = arrayOf(ACCOUNT_NAME, ACCOUNT_TYPE)

        context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getLong(0)
            }
        }
        return null
    }

    fun getOrCreateCalendarId(): Long? {
        val existingId = findCalendarId()
        if (existingId != null) return existingId

        val syncUri = CalendarContract.Calendars.CONTENT_URI.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, ACCOUNT_NAME)
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, ACCOUNT_TYPE)
            .build()

        val values = ContentValues().apply {
            put(CalendarContract.Calendars.ACCOUNT_NAME, ACCOUNT_NAME)
            put(CalendarContract.Calendars.ACCOUNT_TYPE, ACCOUNT_TYPE)
            put(CalendarContract.Calendars.NAME, CALENDAR_NAME)
            put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, CALENDAR_DISPLAY_NAME)
            put(CalendarContract.Calendars.CALENDAR_COLOR, CALENDAR_COLOR)
            put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER)
            put(CalendarContract.Calendars.OWNER_ACCOUNT, ACCOUNT_NAME)
            put(CalendarContract.Calendars.CALENDAR_TIME_ZONE, CALENDAR_TIMEZONE)
            put(CalendarContract.Calendars.SYNC_EVENTS, 1)
            put(CalendarContract.Calendars.VISIBLE, 1)
        }

        val resultUri = context.contentResolver.insert(syncUri, values) ?: return null
        return ContentUris.parseId(resultUri)
    }

    /**
     * Overwrites calendar schedule events with the provided list.
     */
    fun syncEvents(events: List<CalendarEventItem>): CalendarSyncResult {
        if (!hasPermissions()) {
            return CalendarSyncResult(
                success = false,
                message = "未获得系统日历权限，请先授予日历访问权限",
            )
        }

        try {
            val calendarId = getOrCreateCalendarId()
                ?: return CalendarSyncResult(
                    success = false,
                    message = "无法在系统日历中创建或获取“$CALENDAR_DISPLAY_NAME”日历账户",
                )

            // Remove existing events for this calendar to prevent duplicate entries
            val deletedCount = context.contentResolver.delete(
                getEventsSyncUri(),
                "${CalendarContract.Events.CALENDAR_ID} = ?",
                arrayOf(calendarId.toString()),
            )

            if (events.isEmpty()) {
                return CalendarSyncResult(
                    success = true,
                    insertedCount = 0,
                    deletedCount = deletedCount,
                    message = "已清空系统日历中的课表日程",
                )
            }

            val operations = buildInsertOperations(calendarId, events)
            val batches = chunkList(operations, BATCH_SIZE)

            for (batch in batches) {
                context.contentResolver.applyBatch(CalendarContract.AUTHORITY, ArrayList(batch))
            }

            return CalendarSyncResult(
                success = true,
                insertedCount = events.size,
                deletedCount = deletedCount,
                message = "已成功同步 ${events.size} 节课程到系统本地日历",
            )
        } catch (e: Exception) {
            return CalendarSyncResult(
                success = false,
                message = "同步系统日历失败：${e.localizedMessage ?: e.message ?: "未知错误"}",
            )
        }
    }

    /**
     * Clears all course schedule events and deletes the custom local calendar.
     */
    fun clearCalendar(): CalendarSyncResult {
        if (!hasPermissions()) {
            return CalendarSyncResult(
                success = false,
                message = "未获得系统日历权限，请先授予日历访问权限",
            )
        }

        try {
            val calendarId = findCalendarId()
                ?: return CalendarSyncResult(
                    success = true,
                    message = "系统日历中未找到课表日程，无需清理",
                )

            // Delete calendar via sync adapter URI
            val syncUri = ContentUris.withAppendedId(CalendarContract.Calendars.CONTENT_URI, calendarId)
                .buildUpon()
                .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
                .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, ACCOUNT_NAME)
                .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, ACCOUNT_TYPE)
                .build()

            val deletedCalendars = context.contentResolver.delete(syncUri, null, null)

            // Also clean any orphaned events if any remain
            val deletedEvents = context.contentResolver.delete(
                getEventsSyncUri(),
                "${CalendarContract.Events.CALENDAR_ID} = ?",
                arrayOf(calendarId.toString()),
            )

            return CalendarSyncResult(
                success = true,
                deletedCount = deletedEvents,
                message = "已从系统日历中完全移除“$CALENDAR_DISPLAY_NAME”",
            )
        } catch (e: Exception) {
            return CalendarSyncResult(
                success = false,
                message = "移除系统日历课表失败：${e.localizedMessage ?: e.message ?: "未知错误"}",
            )
        }
    }
}
