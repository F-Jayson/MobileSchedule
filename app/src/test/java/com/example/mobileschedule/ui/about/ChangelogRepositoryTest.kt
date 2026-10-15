package com.example.mobileschedule.ui.about

import com.example.mobileschedule.data.model.AppUpdateInfo
import org.junit.Assert.*
import org.junit.Test

class ChangelogRepositoryTest {

    @Test
    fun testBundledChangelogs_containsExpectedVersions() {
        val changelogs = ChangelogRepository.bundledChangelogs
        assertTrue("Bundled changelogs should have at least 3 historical releases", changelogs.size >= 3)

        val versions = changelogs.map { it.versionName }
        assertTrue("Should include v1.2.0", versions.contains("1.2.0"))
        assertTrue("Should include v1.1.0", versions.contains("1.1.0"))
        assertTrue("Should include v1.0.0", versions.contains("1.0.0"))

        changelogs.forEach { entry ->
            assertTrue("Version name should not be blank", entry.versionName.isNotBlank())
            assertTrue("Release date should not be blank", entry.releaseDate.isNotBlank())
            assertTrue("Release title should not be blank", entry.releaseTitle.isNotBlank())
            assertTrue("Highlights should not be empty", entry.highlights.isNotEmpty())
            assertTrue("Full notes should not be blank", entry.fullNotes.isNotBlank())
            assertFalse("Bundled entries should not be marked as remote new", entry.isRemoteNew)
            assertNull("Bundled entries should not have remoteUpdateInfo", entry.remoteUpdateInfo)
        }
    }

    @Test
    fun testParseHighlights_cleansBulletsAndPrefixes() {
        val raw = """
            # v1.3.0 更新说明
            • 优化桌面小组件刷新性能
            - 支持批量编辑课程周次
            * 增加课程导出 PDF 格式
            1. 修复偶发闪烁问题
            > 注意事项：请务必备份数据
            ```
            code block
            ```
            常规文本说明项
        """.trimIndent()

        val highlights = ChangelogRepository.parseHighlights(raw)
        assertEquals(5, highlights.size)
        assertEquals("优化桌面小组件刷新性能", highlights[0])
        assertEquals("支持批量编辑课程周次", highlights[1])
        assertEquals("增加课程导出 PDF 格式", highlights[2])
        assertEquals("修复偶发闪烁问题", highlights[3])
        assertEquals("常规文本说明项", highlights[4])
    }

    @Test
    fun testMergeWithRemoteUpdate_nullRemote_returnsBundled() {
        val result = ChangelogRepository.mergeWithRemoteUpdate(null, currentVersion = "1.2.0")
        assertEquals(ChangelogRepository.bundledChangelogs, result)
    }

    @Test
    fun testMergeWithRemoteUpdate_sameOrOlderVersion_returnsBundled() {
        val remoteInfoSame = AppUpdateInfo(
            currentVersionName = "1.2.0",
            latestVersionName = "1.2.0",
            releaseTitle = "v1.2.0",
            releaseNotes = "无新更新",
            downloadUrl = null,
            browserUrl = "https://example.com",
            hasUpdate = false,
        )

        val result = ChangelogRepository.mergeWithRemoteUpdate(remoteInfoSame, currentVersion = "1.2.0")
        assertEquals(ChangelogRepository.bundledChangelogs, result)
    }

    @Test
    fun testMergeWithRemoteUpdate_newerVersion_prependsRemoteEntryWithBadge() {
        val remoteNew = AppUpdateInfo(
            currentVersionName = "1.2.0",
            latestVersionName = "1.3.0",
            releaseTitle = "全新日程流与同步",
            releaseNotes = "• 新增日程流同步\n• 提升加载性能",
            downloadUrl = "https://example.com/app-v1.3.0.apk",
            browserUrl = "https://example.com/releases/v1.3.0",
            hasUpdate = true,
            apkSize = 15200000L,
            publishedAt = "2026-10-20",
        )

        val result = ChangelogRepository.mergeWithRemoteUpdate(remoteNew, currentVersion = "1.2.0")
        assertEquals(ChangelogRepository.bundledChangelogs.size + 1, result.size)

        val first = result.first()
        assertEquals("1.3.0", first.versionName)
        assertEquals("2026-10-20", first.releaseDate)
        assertEquals("全新日程流与同步", first.releaseTitle)
        assertTrue(first.isRemoteNew)
        assertEquals(remoteNew, first.remoteUpdateInfo)
        assertTrue(first.highlights.contains("新增日程流同步"))
        assertTrue(first.highlights.contains("提升加载性能"))

        // Ensure bundled versions follow
        assertEquals("1.2.0", result[1].versionName)
        assertEquals("1.1.0", result[2].versionName)
        assertEquals("1.0.0", result[3].versionName)
    }
}
