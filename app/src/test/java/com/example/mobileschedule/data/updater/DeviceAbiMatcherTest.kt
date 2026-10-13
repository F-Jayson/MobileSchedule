package com.example.mobileschedule.data.updater

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceAbiMatcherTest {

    private val sampleAssets = listOf(
        UpdateAsset("arm64-v8a", "mobileschedule-v1.1.0-arm64-v8a-debug.apk", "https://url/arm64.apk", 1000L),
        UpdateAsset("armeabi-v7a", "mobileschedule-v1.1.0-armeabi-v7a-debug.apk", "https://url/armv7.apk", 1000L),
        UpdateAsset("universal", "mobileschedule-v1.1.0-universal-debug.apk", "https://url/universal.apk", 2000L),
        UpdateAsset("x86_64", "mobileschedule-v1.1.0-x86_64-debug.apk", "https://url/x86_64.apk", 1000L),
    )

    @Test
    fun testNormalizeAbi() {
        assertEquals("arm64-v8a", DeviceAbiMatcher.normalizeAbi("mobileschedule-arm64-v8a.apk"))
        assertEquals("arm64-v8a", DeviceAbiMatcher.normalizeAbi("aarch64"))
        assertEquals("armeabi-v7a", DeviceAbiMatcher.normalizeAbi("mobileschedule-armeabi-v7a-debug.apk"))
        assertEquals("x86_64", DeviceAbiMatcher.normalizeAbi("mobileschedule-x86_64.apk"))
        assertEquals("universal", DeviceAbiMatcher.normalizeAbi("mobileschedule-universal.apk"))
    }

    @Test
    fun testMatchArm64Device() {
        val supported = arrayOf("arm64-v8a", "armeabi-v7a", "armeabi")
        val matched = DeviceAbiMatcher.findBestAsset(sampleAssets, supported)
        assertEquals("arm64-v8a", matched?.abi)
        assertEquals("https://url/arm64.apk", matched?.downloadUrl)
    }

    @Test
    fun testMatchArm32Device() {
        val supported = arrayOf("armeabi-v7a", "armeabi")
        val matched = DeviceAbiMatcher.findBestAsset(sampleAssets, supported)
        assertEquals("armeabi-v7a", matched?.abi)
        assertEquals("https://url/armv7.apk", matched?.downloadUrl)
    }

    @Test
    fun testMatchX86_64Device() {
        val supported = arrayOf("x86_64", "arm64-v8a")
        val matched = DeviceAbiMatcher.findBestAsset(sampleAssets, supported)
        assertEquals("x86_64", matched?.abi)
        assertEquals("https://url/x86_64.apk", matched?.downloadUrl)
    }

    @Test
    fun testFallbackToUniversalWhenNativeAbiMissing() {
        val subset = listOf(
            UpdateAsset("universal", "mobileschedule-universal.apk", "https://url/universal.apk", 2000L),
            UpdateAsset("x86_64", "mobileschedule-x86_64.apk", "https://url/x86_64.apk", 1000L),
        )
        val supported = arrayOf("arm64-v8a", "armeabi-v7a")
        val matched = DeviceAbiMatcher.findBestAsset(subset, supported)
        assertEquals("universal", matched?.abi)
        assertEquals("https://url/universal.apk", matched?.downloadUrl)
    }

    @Test
    fun testEmptyAssetsReturnsNull() {
        val matched = DeviceAbiMatcher.findBestAsset(emptyList(), arrayOf("arm64-v8a"))
        assertNull(matched)
    }
}
