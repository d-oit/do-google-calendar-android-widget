package com.example.util

/**
 * Centralized runtime environment detection utility used to avoid spawning
 * real background loops or infinite animations inside local Robolectric JVM tests.
 */
object RuntimeEnvironment {
    val isRunningInTestEnvironment: Boolean by lazy {
        try {
            Class.forName("org.robolectric.Robolectric") != null
        } catch (_: ClassNotFoundException) {
            false
        }
    }
}
