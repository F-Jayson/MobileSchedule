package com.example.mobileschedule.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadMirrorTest {

    private val sampleRawUrl = "https://github.com/F-Jayson/MobileSchedule/releases/download/v1.0.0/app-debug.apk"

    @Test
    fun testGhProxyPrefix() {
        val url = DownloadMirror.GH_PROXY.buildDownloadUrl(sampleRawUrl)
        assertEquals("https://gh-proxy.com/$sampleRawUrl", url)
    }

    @Test
    fun testGhproxyNetPrefix() {
        val url = DownloadMirror.GHPROXY_NET.buildDownloadUrl(sampleRawUrl)
        assertEquals("https://ghproxy.net/$sampleRawUrl", url)
    }

    @Test
    fun testOfficialNoPrefix() {
        val url = DownloadMirror.OFFICIAL.buildDownloadUrl(sampleRawUrl)
        assertEquals(sampleRawUrl, url)
    }

    @Test
    fun testCustomPrefixWithSlash() {
        val url = DownloadMirror.CUSTOM.buildDownloadUrl(sampleRawUrl, "https://my-proxy.com/")
        assertEquals("https://my-proxy.com/$sampleRawUrl", url)
    }

    @Test
    fun testCustomPrefixWithoutSlash() {
        val url = DownloadMirror.CUSTOM.buildDownloadUrl(sampleRawUrl, "https://my-proxy.com")
        assertEquals("https://my-proxy.com/$sampleRawUrl", url)
    }

    @Test
    fun testCustomPrefixEmptyFallsBackToRaw() {
        val url = DownloadMirror.CUSTOM.buildDownloadUrl(sampleRawUrl, "")
        assertEquals(sampleRawUrl, url)
    }

    @Test
    fun testBlogDownloadUrlRelativePath() {
        val url = DownloadMirror.BLOG.buildDownloadUrl("/api/app/schedule/download")
        assertEquals("https://blog.fjayson.com/api/app/schedule/download", url)
    }

    @Test
    fun testBlogDownloadUrlCustomOrigin() {
        val url = DownloadMirror.BLOG.buildDownloadUrl("api/app/schedule/download", blogOrigin = "https://myblog.com")
        assertEquals("https://myblog.com/api/app/schedule/download", url)
    }

    @Test
    fun testBlogDownloadUrlAbsoluteHttpUrl() {
        val directUrl = "https://files.fjayson.com/public/MobileSchedule.apk"
        val url = DownloadMirror.BLOG.buildDownloadUrl(directUrl)
        assertEquals(directUrl, url)
    }

    @Test
    fun testFromKey() {
        assertEquals(DownloadMirror.BLOG, DownloadMirror.fromKey("blog"))
        assertEquals(DownloadMirror.GH_PROXY, DownloadMirror.fromKey("gh_proxy"))
        assertEquals(DownloadMirror.GHPROXY_NET, DownloadMirror.fromKey("ghproxy_net"))
        assertEquals(DownloadMirror.OFFICIAL, DownloadMirror.fromKey("official"))
        assertEquals(DownloadMirror.CUSTOM, DownloadMirror.fromKey("custom"))
        assertEquals(DownloadMirror.DEFAULT, DownloadMirror.fromKey("invalid_key"))
        assertEquals(DownloadMirror.DEFAULT, DownloadMirror.fromKey(null))
        assertEquals(DownloadMirror.BLOG, DownloadMirror.DEFAULT)
    }
}
