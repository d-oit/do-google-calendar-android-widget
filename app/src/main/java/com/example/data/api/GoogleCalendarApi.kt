package com.example.data.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class GoogleCalendarListResponse(
    @param:Json(name = "items") val items: List<GoogleCalendarListItem>? = null
)

@JsonClass(generateAdapter = true)
data class GoogleCalendarListItem(
    @param:Json(name = "id") val id: String,
    @param:Json(name = "summary") val summary: String? = null,
    @param:Json(name = "description") val description: String? = null,
    @param:Json(name = "primary") val primary: Boolean? = null,
    @param:Json(name = "backgroundColor") val backgroundColor: String? = null,
    @param:Json(name = "foregroundColor") val foregroundColor: String? = null,
    @param:Json(name = "colorId") val colorId: String? = null
)

@JsonClass(generateAdapter = true)
data class GoogleEventsResponse(
    @param:Json(name = "items") val items: List<GoogleEventItem>? = null
)

@JsonClass(generateAdapter = true)
data class GoogleEventItem(
    @param:Json(name = "id") val id: String,
    @param:Json(name = "summary") val summary: String? = null,
    @param:Json(name = "description") val description: String? = null,
    @param:Json(name = "location") val location: String? = null,
    @param:Json(name = "start") val start: GoogleEventTime? = null,
    @param:Json(name = "end") val end: GoogleEventTime? = null,
    @param:Json(name = "htmlLink") val htmlLink: String? = null,
    @param:Json(name = "colorId") val colorId: String? = null
)

@JsonClass(generateAdapter = true)
data class GoogleEventTime(
    @param:Json(name = "dateTime") val dateTime: String? = null,
    @param:Json(name = "date") val date: String? = null,
    @param:Json(name = "timeZone") val timeZone: String? = null
)

interface GoogleCalendarApiService {

    @GET("users/me/calendarList")
    suspend fun getCalendarList(
        @Header("Authorization") authHeader: String
    ): GoogleCalendarListResponse

    @GET("calendars/{calendarId}/events")
    suspend fun getUpcomingEventsWithAuth(
        @Header("Authorization") authHeader: String,
        @Path("calendarId") calendarId: String = "primary",
        @Query("timeMin") timeMin: String,
        @Query("timeMax") timeMax: String? = null,
        @Query("singleEvents") singleEvents: Boolean = true,
        @Query("orderBy") orderBy: String = "startTime",
        @Query("maxResults") maxResults: Int = 100
    ): GoogleEventsResponse

    @GET("calendars/{calendarId}/events")
    suspend fun getUpcomingEvents(
        @Path("calendarId") calendarId: String = "primary",
        @Query("timeMin") timeMin: String,
        @Query("singleEvents") singleEvents: Boolean = true,
        @Query("orderBy") orderBy: String = "startTime",
        @Query("maxResults") maxResults: Int = 50,
        @Query("key") apiKey: String? = null
    ): GoogleEventsResponse

    companion object {
        /**
         * Standard Google Calendar event colorIds mapped to their official hex codes.
         */
        val GOOGLE_EVENT_COLOR_MAP = mapOf(
            "1" to "#7986CB",  // Lavender
            "2" to "#33B679",  // Sage
            "3" to "#8E24AA",  // Grape
            "4" to "#E67C73",  // Flamingo
            "5" to "#F6BF26",  // Banana
            "6" to "#F4511E",  // Tangerine
            "7" to "#039BE5",  // Peacock
            "8" to "#616161",  // Graphite
            "9" to "#3F51B5",  // Blueberry
            "10" to "#0B8043", // Basil
            "11" to "#D50000"  // Tomato
        )

        fun create(): GoogleCalendarApiService {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
            val client = OkHttpClient.Builder()
                .addInterceptor(logging)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()

            return Retrofit.Builder()
                .baseUrl("https://www.googleapis.com/calendar/v3/")
                .client(client)
                .addConverterFactory(MoshiConverterFactory.create())
                .build()
                .create(GoogleCalendarApiService::class.java)
        }
    }
}
