package com.example.data.update

import com.example.BuildConfig
import com.example.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class SemVer(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val preRelease: String? = null
) : Comparable<SemVer> {

    override fun compareTo(other: SemVer): Int {
        if (major != other.major) return major.compareTo(other.major)
        if (minor != other.minor) return minor.compareTo(other.minor)
        if (patch != other.patch) return patch.compareTo(other.patch)
        return when {
            preRelease == null && other.preRelease == null -> 0
            preRelease == null -> 1 // stable > pre-release
            other.preRelease == null -> -1
            else -> preRelease.compareTo(other.preRelease)
        }
    }

    override fun toString(): String =
        if (preRelease.isNullOrBlank()) "$major.$minor.$patch" else "$major.$minor.$patch-$preRelease"

    companion object {
        private val SEMVER_REGEX = Regex("""^v?(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-([0-9A-Za-z.-]+))?$""")

        fun parse(raw: String): SemVer? {
            val match = SEMVER_REGEX.matchEntire(raw.trim()) ?: return null
            val major = match.groupValues[1].toIntOrNull() ?: return null
            val minor = match.groupValues[2].toIntOrNull() ?: return null
            val patch = match.groupValues[3].toIntOrNull() ?: return null
            val pre = match.groupValues[4].takeIf { it.isNotBlank() }
            return SemVer(major, minor, patch, pre)
        }
    }
}

sealed interface UpdateCheckResult {
    data class UpToDate(val currentVersion: String) : UpdateCheckResult
    data class UpdateAvailable(
        val currentVersion: String,
        val latestVersion: String,
        val releaseNotes: String,
        val releaseUrl: String
    ) : UpdateCheckResult
    data object NotConfigured : UpdateCheckResult
    data class Error(val message: String) : UpdateCheckResult
}

fun interface ReleaseFeedFetcher {
    fun fetchLatestReleaseJson(apiUrl: String): String
}

/**
 * On-demand, zero-tracking GitHub Releases update checker.
 * Strictly communicates over HTTPS with api.github.com only when explicitly triggered by the user,
 * validates returned release URLs against https://github.com/<owner>/<repo>/releases, and never
 * downloads or executes code (Google Play compliant).
 */
class AppUpdateChecker(
    private val currentVersionName: String = BuildConfig.VERSION_NAME,
    private val repositorySlug: String = BuildConfig.GITHUB_REPOSITORY,
    private val fetcher: ReleaseFeedFetcher = DefaultGitHubReleaseFetcher()
) {
    suspend fun checkForUpdates(): UpdateCheckResult = withContext(Dispatchers.IO) {
        val slug = repositorySlug.trim()
        if (!isValidRepositorySlug(slug)) {
            AppLogger.i(AppLogger.TAG_UPDATE, "GitHub repository slug not configured; skipping update check")
            return@withContext UpdateCheckResult.NotConfigured
        }

        val currentSemVer = SemVer.parse(currentVersionName)
            ?: return@withContext UpdateCheckResult.Error("Invalid installed version format")

        val apiUrl = "https://api.github.com/repos/$slug/releases/latest"
        try {
            val jsonBody = fetcher.fetchLatestReleaseJson(apiUrl)
            val json = JSONObject(jsonBody)
            val tagName = json.optString("tag_name", "").trim()
            val htmlUrl = json.optString("html_url", "").trim()
            val body = json.optString("body", "").trim()

            val latestSemVer = SemVer.parse(tagName)
                ?: return@withContext UpdateCheckResult.Error("Unrecognized release version tag")

            val fallbackUrl = "https://github.com/$slug/releases/tag/$tagName"
            val safeReleaseUrl = if (isTrustedReleaseUrl(htmlUrl, slug)) htmlUrl else fallbackUrl

            if (latestSemVer > currentSemVer) {
                AppLogger.i(AppLogger.TAG_UPDATE, "Update available: $latestSemVer (current=$currentSemVer)")
                UpdateCheckResult.UpdateAvailable(
                    currentVersion = currentSemVer.toString(),
                    latestVersion = latestSemVer.toString(),
                    releaseNotes = body.take(800),
                    releaseUrl = safeReleaseUrl
                )
            } else {
                AppLogger.d(AppLogger.TAG_UPDATE, "App is up to date ($currentSemVer)")
                UpdateCheckResult.UpToDate(currentVersion = currentSemVer.toString())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.w(AppLogger.TAG_UPDATE, "Failed checking GitHub Releases for updates", e)
            UpdateCheckResult.Error(e.message?.takeIf { it.isNotBlank() } ?: "Unable to reach GitHub Releases")
        }
    }

    companion object {
        private val REPO_SLUG_REGEX = Regex("""^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$""")

        fun isValidRepositorySlug(slug: String): Boolean {
            val trimmed = slug.trim()
            if (trimmed.isEmpty()) return false
            if (trimmed.equals("OWNER/REPO", ignoreCase = true)) return false
            return REPO_SLUG_REGEX.matches(trimmed)
        }

        fun isTrustedReleaseUrl(url: String, slug: String): Boolean {
            if (!url.startsWith("https://github.com/", ignoreCase = true)) return false
            return try {
                val parsed = URL(url)
                parsed.protocol.equals("https", ignoreCase = true) &&
                    parsed.host.equals("github.com", ignoreCase = true) &&
                    parsed.path.startsWith("/$slug/releases", ignoreCase = true)
            } catch (_: Exception) {
                false
            }
        }
    }
}

class DefaultGitHubReleaseFetcher : ReleaseFeedFetcher {
    override fun fetchLatestReleaseJson(apiUrl: String): String {
        val parsed = URL(apiUrl)
        require(parsed.protocol.equals("https", ignoreCase = true)) { "HTTPS is required" }
        require(parsed.host.equals("api.github.com", ignoreCase = true)) { "Untrusted update host" }

        val connection = (parsed.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8_000
            readTimeout = 8_000
            instanceFollowRedirects = false
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "AgendaWidget-Android/${BuildConfig.VERSION_NAME}")
        }

        return try {
            val statusCode = connection.responseCode
            if (statusCode == HttpURLConnection.HTTP_NOT_FOUND) {
                throw IllegalStateException("No published GitHub Releases found yet")
            }
            if (statusCode !in 200..299) {
                throw IllegalStateException("GitHub API responded with HTTP $statusCode")
            }
            BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8)).use { reader ->
                reader.readText()
            }
        } finally {
            connection.disconnect()
        }
    }
}
