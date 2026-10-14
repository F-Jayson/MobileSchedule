package com.example.mobileschedule.ui.about

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenSourceLicensesTest {

    @Test
    fun libraries_areNotEmpty_andContainRequiredFields() {
        val libraries = OpenSourceLicenseRepository.libraries
        assertTrue("Library list must not be empty", libraries.isNotEmpty())
        assertTrue("Library list must contain at least 10 core dependencies", libraries.size >= 10)

        for (lib in libraries) {
            assertTrue("Library id must not be blank for ${lib.name}", lib.id.isNotBlank())
            assertTrue("Library name must not be blank", lib.name.isNotBlank())
            assertTrue("Library version must not be blank for ${lib.name}", lib.version.isNotBlank())
            assertTrue("Library author must not be blank for ${lib.name}", lib.author.isNotBlank())
            assertTrue("Library license must not be blank for ${lib.name}", lib.license.isNotBlank())
            assertTrue("Library url must start with https:// for ${lib.name}", lib.url.startsWith("https://"))
            assertTrue("Library description must not be blank for ${lib.name}", lib.description.isNotBlank())
            assertTrue("License content must not be blank for ${lib.name}", lib.licenseContent.isNotBlank())
            assertTrue(
                "License content must contain standard licensing clauses for ${lib.name}",
                lib.licenseContent.contains("TERMS AND CONDITIONS") ||
                    lib.licenseContent.contains("Permission is hereby granted")
            )
        }
    }

    @Test
    fun filter_withEmptyOrBlankQuery_returnsAllLibraries() {
        val all = OpenSourceLicenseRepository.libraries
        assertEquals(all, OpenSourceLicenseRepository.filter(""))
        assertEquals(all, OpenSourceLicenseRepository.filter("   "))
    }

    @Test
    fun filter_byName_returnsMatchingLibraries() {
        val roomResults = OpenSourceLicenseRepository.filter("Room")
        assertTrue(roomResults.any { it.id == "room" })

        val composeResults = OpenSourceLicenseRepository.filter("compose")
        assertTrue(composeResults.any { it.id == "jetpack-compose" })
    }

    @Test
    fun filter_byAuthor_returnsMatchingLibraries() {
        val jetbrainsResults = OpenSourceLicenseRepository.filter("JetBrains")
        assertTrue("Must include kotlinx-coroutines", jetbrainsResults.any { it.id == "kotlinx-coroutines" })
        assertTrue("Must include kotlinx-serialization", jetbrainsResults.any { it.id == "kotlinx-serialization" })
        assertTrue("Must include kotlin-stdlib", jetbrainsResults.any { it.id == "kotlin-stdlib" })
        for (item in jetbrainsResults) {
            assertTrue(item.author.contains("JetBrains", ignoreCase = true))
        }
    }

    @Test
    fun filter_byLicense_returnsMatchingLibraries() {
        val apacheResults = OpenSourceLicenseRepository.filter("Apache-2.0")
        assertTrue(apacheResults.isNotEmpty())
        for (item in apacheResults) {
            assertTrue(item.license.contains("Apache", ignoreCase = true))
        }
    }

    @Test
    fun filter_withNonExistentQuery_returnsEmptyList() {
        val emptyResults = OpenSourceLicenseRepository.filter("NonExistentLibraryXYZ987")
        assertTrue(emptyResults.isEmpty())
    }
}
