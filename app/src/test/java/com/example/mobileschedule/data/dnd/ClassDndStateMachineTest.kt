package com.example.mobileschedule.data.dnd

import android.media.AudioManager
import com.example.mobileschedule.data.model.CourseArrangement
import com.example.mobileschedule.data.model.CourseOrigin
import com.example.mobileschedule.data.model.SemesterConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class ClassDndStateMachineTest {

    private val monday = LocalDate.of(2026, 9, 7)
    private val config = SemesterConfig(
        firstWeekMonday = monday,
        totalWeeks = 16,
        totalSections = 12,
        sectionTimes = emptyList(), // 1-2: 08:20-10:00, 3-4: 10:20-12:00, 5-6: 14:00-15:40
        revision = 1L,
    )

    private val course1 = CourseArrangement(
        id = 101L,
        semesterId = 1L,
        name = "高等数学",
        teacher = "张教授",
        location = "知明1-101",
        dayOfWeek = 1, // Monday
        startSection = 1,
        endSection = 2,
        weeks = (1..16).toSet(),
        origin = CourseOrigin.Manual,
    )

    private val course2 = CourseArrangement(
        id = 102L,
        semesterId = 1L,
        name = "大学英语",
        teacher = "王老师",
        location = "知明2-202",
        dayOfWeek = 1, // Monday
        startSection = 3,
        endSection = 4,
        weeks = (1..16).toSet(),
        origin = CourseOrigin.Manual,
    )

    private val allCourses = listOf(course1, course2)

    // ==========================================
    // 1. State Machine & Profile Recovery Tests
    // ==========================================

    @Test
    fun enterClassFromNormalRinger_savesNormalProfileAndAppliesSilence() {
        val initial = ClassDndProfileState(
            isDndActive = false,
            originalRingerMode = AudioManager.RINGER_MODE_NORMAL,
            originalFilter = 1, // INTERRUPTION_FILTER_ALL
        )

        val (nextState, effect) = ClassDndStateMachine.transition(
            initial,
            ClassDndEvent.EnterClass(
                currentRingerMode = AudioManager.RINGER_MODE_NORMAL,
                currentFilter = 1,
            ),
        )

        assertTrue("DND should become active", nextState.isDndActive)
        assertEquals(AudioManager.RINGER_MODE_NORMAL, nextState.originalRingerMode)
        assertEquals(1, nextState.originalFilter)

        assertTrue(effect is ClassDndEffect.ApplySilence)
        val silence = effect as ClassDndEffect.ApplySilence
        assertEquals(AudioManager.RINGER_MODE_NORMAL, silence.savedRingerMode)
        assertEquals(1, silence.savedFilter)
    }

    @Test
    fun enterClassFromVibrateRinger_savesVibrateProfileAndAppliesSilence() {
        val initial = ClassDndProfileState(
            isDndActive = false,
            originalRingerMode = AudioManager.RINGER_MODE_NORMAL,
            originalFilter = 1,
        )

        val (nextState, effect) = ClassDndStateMachine.transition(
            initial,
            ClassDndEvent.EnterClass(
                currentRingerMode = AudioManager.RINGER_MODE_VIBRATE,
                currentFilter = 1,
            ),
        )

        assertTrue(nextState.isDndActive)
        assertEquals("Should preserve VIBRATE ringer mode", AudioManager.RINGER_MODE_VIBRATE, nextState.originalRingerMode)

        assertTrue(effect is ClassDndEffect.ApplySilence)
        assertEquals(AudioManager.RINGER_MODE_VIBRATE, (effect as ClassDndEffect.ApplySilence).savedRingerMode)
    }

    @Test
    fun consecutiveClassesWithoutBreak_reEntryPreservesInitialRingerMode() {
        // User was originally in NORMAL mode, then class started
        val inClassState = ClassDndProfileState(
            isDndActive = true,
            originalRingerMode = AudioManager.RINGER_MODE_NORMAL,
            originalFilter = 1,
        )

        // Consecutive class starts; current system ringer is already SILENT
        val (nextState, effect) = ClassDndStateMachine.transition(
            inClassState,
            ClassDndEvent.EnterClass(
                currentRingerMode = AudioManager.RINGER_MODE_SILENT,
                currentFilter = 3, // INTERRUPTION_FILTER_ALARMS
            ),
        )

        assertTrue(nextState.isDndActive)
        assertEquals("Initial NORMAL mode must NOT be overwritten by SILENT", AudioManager.RINGER_MODE_NORMAL, nextState.originalRingerMode)
        assertEquals(1, nextState.originalFilter)
        assertEquals(ClassDndEffect.None, effect)
    }

    @Test
    fun exitClass_restoresExactInitialProfile() {
        val inClassState = ClassDndProfileState(
            isDndActive = true,
            originalRingerMode = AudioManager.RINGER_MODE_VIBRATE,
            originalFilter = 1,
        )

        val (nextState, effect) = ClassDndStateMachine.transition(inClassState, ClassDndEvent.ExitClass)

        assertFalse("DND should become inactive", nextState.isDndActive)
        assertTrue(effect is ClassDndEffect.RestoreProfile)
        val restore = effect as ClassDndEffect.RestoreProfile
        assertEquals(AudioManager.RINGER_MODE_VIBRATE, restore.targetRingerMode)
        assertEquals(1, restore.targetFilter)
    }

    @Test
    fun exitClassWhenNotInDnd_producesNoEffect() {
        val inactiveState = ClassDndProfileState(
            isDndActive = false,
            originalRingerMode = AudioManager.RINGER_MODE_NORMAL,
            originalFilter = 1,
        )

        val (nextState, effect) = ClassDndStateMachine.transition(inactiveState, ClassDndEvent.ExitClass)
        assertFalse(nextState.isDndActive)
        assertEquals(ClassDndEffect.None, effect)
    }

    @Test
    fun manualDisableDuringClass_restoresOriginalProfileImmediately() {
        val inClassState = ClassDndProfileState(
            isDndActive = true,
            originalRingerMode = AudioManager.RINGER_MODE_NORMAL,
            originalFilter = 1,
        )

        val (nextState, effect) = ClassDndStateMachine.transition(inClassState, ClassDndEvent.ManualDisable)

        assertFalse(nextState.isDndActive)
        assertTrue(effect is ClassDndEffect.RestoreProfile)
        val restore = effect as ClassDndEffect.RestoreProfile
        assertEquals(AudioManager.RINGER_MODE_NORMAL, restore.targetRingerMode)
    }

    // ==========================================
    // 2. Schedule Deriver & Session Logic Tests
    // ==========================================

    @Test
    fun deriveAlarms_generatesBothEnterAndExitForTodayCourses() {
        val today = monday // 2026-09-07, Week 1, Monday
        val now = LocalDateTime.of(today, LocalTime.of(7, 30)) // 07:30 before all classes

        val alarms = ClassDndScheduleDeriver.deriveAlarms(
            config = config,
            arrangements = allCourses,
            semesterId = 1L,
            startDate = today,
            daysCount = 1,
            now = now,
        )

        // 2 courses on Monday -> 2 ENTER + 2 EXIT = 4 alarms
        assertEquals(4, alarms.size)

        // Course 1 (08:20 - 10:00)
        assertEquals(DndAlarmType.ENTER, alarms[0].type)
        assertEquals(LocalDateTime.of(today, LocalTime.of(8, 20)), alarms[0].triggerDateTime)
        assertEquals(101L, alarms[0].courseId)

        assertEquals(DndAlarmType.EXIT, alarms[1].type)
        assertEquals(LocalDateTime.of(today, LocalTime.of(10, 0)), alarms[1].triggerDateTime)
        assertEquals(101L, alarms[1].courseId)

        // Course 2 (10:20 - 12:00)
        assertEquals(DndAlarmType.ENTER, alarms[2].type)
        assertEquals(LocalDateTime.of(today, LocalTime.of(10, 20)), alarms[2].triggerDateTime)

        assertEquals(DndAlarmType.EXIT, alarms[3].type)
        assertEquals(LocalDateTime.of(today, LocalTime.of(12, 0)), alarms[3].triggerDateTime)
    }

    @Test
    fun deriveAlarms_skipsWeekendsAndDaysWithoutClasses() {
        val sunday = monday.plusDays(6) // Sunday, no courses scheduled
        val now = LocalDateTime.of(sunday, LocalTime.of(8, 0))

        val alarms = ClassDndScheduleDeriver.deriveAlarms(
            config = config,
            arrangements = allCourses,
            semesterId = 1L,
            startDate = sunday,
            daysCount = 1,
            now = now,
        )

        assertTrue("Weekends or no-course days should produce 0 alarms", alarms.isEmpty())
    }

    @Test
    fun isAnyCourseInSession_accuratelyDetectsInClassVsBreak() {
        val today = monday

        // 08:00 before classes: false
        assertFalse(ClassDndScheduleDeriver.isAnyCourseInSession(config, allCourses, 1L, today, LocalTime.of(8, 0)))

        // 08:30 during Course 1 (08:20 - 10:00): true
        assertTrue(ClassDndScheduleDeriver.isAnyCourseInSession(config, allCourses, 1L, today, LocalTime.of(8, 30)))

        // 10:10 during inter-class break (10:00 - 10:20): false
        assertFalse(ClassDndScheduleDeriver.isAnyCourseInSession(config, allCourses, 1L, today, LocalTime.of(10, 10)))

        // 11:00 during Course 2 (10:20 - 12:00): true
        assertTrue(ClassDndScheduleDeriver.isAnyCourseInSession(config, allCourses, 1L, today, LocalTime.of(11, 0)))

        // 12:30 after classes: false
        assertFalse(ClassDndScheduleDeriver.isAnyCourseInSession(config, allCourses, 1L, today, LocalTime.of(12, 30)))
    }
}
