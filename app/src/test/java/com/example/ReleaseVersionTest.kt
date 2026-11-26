package com.example

import java.io.File
import java.util.Properties
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies release version metadata integrity between version.properties and BuildConfig.
 * Ensures Semantic Versioning 2.0.0 compliance starting from 0.1.0.
 */
class ReleaseVersionTest {

    private val semVerRegex = Regex("""^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-[0-9A-Za-z.-]+)?$""")

    @Test
    fun `buildConfig versionName follows Semantic Versioning 2_0_0`() {
        assertTrue(
            "Expected BuildConfig.VERSION_NAME (${BuildConfig.VERSION_NAME}) to be valid SemVer",
            semVerRegex.matches(BuildConfig.VERSION_NAME)
        )
        assertTrue(
            "Expected BuildConfig.VERSION_CODE (${BuildConfig.VERSION_CODE}) to be positive",
            BuildConfig.VERSION_CODE >= 1
        )
    }

    @Test
    fun `version_properties matches BuildConfig versionName and versionCode`() {
        val candidates = listOf(
            File("version.properties"),
            File("../version.properties")
        )
        val versionFile = candidates.firstOrNull { it.exists() }
        assertTrue("Expected version.properties to exist at repository root", versionFile != null)

        val props = Properties().apply {
            versionFile!!.inputStream().use { load(it) }
        }
        val major = props.getProperty("VERSION_MAJOR")?.trim()
        val minor = props.getProperty("VERSION_MINOR")?.trim()
        val patch = props.getProperty("VERSION_PATCH")?.trim()
        val code = props.getProperty("VERSION_CODE")?.trim()?.toIntOrNull()

        assertEquals("$major.$minor.$patch", BuildConfig.VERSION_NAME)
        assertEquals(code, BuildConfig.VERSION_CODE)
    }
}
