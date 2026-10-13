package com.example.mobileschedule.data.updater

import com.example.mobileschedule.data.model.VersionComparator
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionComparatorTest {

    @Test
    fun testIsNewerBasic() {
        assertTrue(VersionComparator.isNewer("1.0.1", "1.0.0"))
        assertTrue(VersionComparator.isNewer("v1.1.0", "1.0.0"))
        assertTrue(VersionComparator.isNewer("2.0", "1.9.9"))
        assertTrue(VersionComparator.isNewer("1.0.0.1", "1.0.0"))
    }

    @Test
    fun testIsNotNewer() {
        assertFalse(VersionComparator.isNewer("1.0.0", "1.0.0"))
        assertFalse(VersionComparator.isNewer("v1.0.0", "1.0.0"))
        assertFalse(VersionComparator.isNewer("1.0.0", "1.0.1"))
        assertFalse(VersionComparator.isNewer("0.9.9", "1.0.0"))
    }

    @Test
    fun testSuffixHandling() {
        assertTrue(VersionComparator.isNewer("1.0.1-beta", "1.0.0"))
        assertFalse(VersionComparator.isNewer("1.0.0-rc1", "1.0.0"))
    }
}
