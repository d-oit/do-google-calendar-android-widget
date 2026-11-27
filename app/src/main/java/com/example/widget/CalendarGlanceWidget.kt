package com.example.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.color.ColorProvider as DayNightColorProvider
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.material3.ColorProviders
import androidx.glance.semantics.semantics
import androidx.glance.semantics.testTag
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.CalendarApp
import com.example.MainActivity
import com.example.R
import com.example.data.db.AppDatabase
import com.example.data.model.EventEntity
import com.example.data.pref.WidgetPreferences
import com.example.data.pref.WidgetPreferencesManager
import com.example.data.repository.CalendarRepository
import com.example.util.AppLogger
import com.example.util.CalendarColorMapper
import com.example.util.CalendarDateFormatter
import com.example.worker.CalendarSyncScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CalendarGlanceWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = withContext(Dispatchers.IO) {
            loadWidgetSnapshot(context.applicationContext)
        }

        provideContent {
            val glancePrefs = currentState<Preferences>()
            val syncRevision = glancePrefs[KEY_SYNC_REVISION] ?: 0
            val glanceSyncMillis = glancePrefs[KEY_LAST_SYNC_MILLIS] ?: 0L
            val isSyncing = glancePrefs[KEY_IS_SYNCING] ?: false
            val effectiveLastSyncMillis = maxOf(glanceSyncMillis, snapshot.lastSyncMillis)

            val m3Schemes = WidgetThemeHelper.resolveMaterial3ColorSchemes(
                context = context.applicationContext,
                colorTheme = snapshot.preferences.colorTheme
            )
            GlanceTheme(
                colors = ColorProviders(
                    light = m3Schemes.light,
                    dark = m3Schemes.dark
                )
            ) {
                CalendarGlanceWidgetContent(
                    events = snapshot.events,
                    prefs = snapshot.preferences,
                    theme = snapshot.theme,
                    hasPermission = snapshot.hasPermission,
                    syncRevision = syncRevision,
                    lastSyncMillis = effectiveLastSyncMillis,
                    isSyncing = isSyncing
                )
            }
        }
    }

    override fun onCompositionError(
        context: Context,
        glanceId: GlanceId,
        appWidgetId: Int,
        throwable: Throwable
    ) {
        AppLogger.e(AppLogger.TAG_WIDGET, "Glance composition error for appWidgetId=$appWidgetId", throwable)
        try {
            val fallback = RemoteViews(context.packageName, R.layout.widget_calendar_agenda).apply {
                setTextViewText(R.id.widget_date_title, context.getString(R.string.widget_default_title))
                setTextViewText(R.id.widget_empty_text, context.getString(R.string.error_sync_failed))
            }
            AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, fallback)
        } catch (e: Exception) {
            AppLogger.w(AppLogger.TAG_WIDGET, "Failed applying Glance fallback RemoteViews", e)
        }
    }

    private data class GlanceWidgetSnapshot(
        val events: List<EventEntity>,
        val preferences: WidgetPreferences,
        val theme: ResolvedWidgetTheme,
        val hasPermission: Boolean,
        val lastSyncMillis: Long
    )

    private fun loadWidgetSnapshot(context: Context): GlanceWidgetSnapshot {
        val prefsManager = WidgetPreferencesManager.getInstance(context)
        val prefs = prefsManager.getPreferences()
        val theme = WidgetThemeHelper.resolveColors(context, prefs)
        val repo = runCatching { CalendarApp.instance.repository }.getOrElse {
            CalendarRepository(
                context,
                AppDatabase.getInstance(context).eventDao(),
                prefsManager
            )
        }
        val events = try {
            repo.getUpcomingEventsSync()
        } catch (e: Exception) {
            AppLogger.w(AppLogger.TAG_WIDGET, "Failed loading events for Glance widget", e)
            emptyList()
        }
        return GlanceWidgetSnapshot(
            events = events,
            preferences = prefs,
            theme = theme,
            hasPermission = repo.checkCalendarPermission(),
            lastSyncMillis = prefsManager.getLastSuccessfulSyncMillis()
        )
    }

    companion object {
        val KEY_SYNC_REVISION = intPreferencesKey("glance_sync_revision")
        val KEY_LAST_SYNC_MILLIS = longPreferencesKey("glance_last_sync_millis")
        val KEY_IS_SYNCING = booleanPreferencesKey("glance_is_syncing")
    }
}

@Composable
fun CalendarGlanceWidgetContent(
    events: List<EventEntity>,
    prefs: WidgetPreferences,
    theme: ResolvedWidgetTheme,
    hasPermission: Boolean,
    syncRevision: Int = 0,
    lastSyncMillis: Long = 0L,
    isSyncing: Boolean = false
) {
    val context = LocalContext.current
    val size: DpSize = LocalSize.current
    val isNarrow = size.width in 1.dp..190.dp

    // Resolve both Material 3 light and dark palettes so Android's launcher can switch
    // seamlessly between Light and Dark mode via DayNight ColorProviders.
    val lightTheme = if (!theme.isDark) {
        theme
    } else {
        WidgetThemeHelper.resolveColors(context, prefs, forceDark = false)
    }
    val darkTheme = if (theme.isDark) {
        theme
    } else {
        WidgetThemeHelper.resolveColors(context, prefs, forceDark = true)
    }

    val rootBgProvider = DayNightColorProvider(
        day = Color(lightTheme.rootBgColor).copy(alpha = lightTheme.rootAlpha / 255f),
        night = Color(darkTheme.rootBgColor).copy(alpha = darkTheme.rootAlpha / 255f)
    )
    val itemBgProvider = DayNightColorProvider(
        day = Color(lightTheme.itemBgColor).copy(alpha = lightTheme.itemAlpha / 255f),
        night = Color(darkTheme.itemBgColor).copy(alpha = darkTheme.itemAlpha / 255f)
    )
    val accentProvider = DayNightColorProvider(
        day = Color(lightTheme.accentColor),
        night = Color(darkTheme.accentColor)
    )
    val chipBgProvider = DayNightColorProvider(
        day = Color(lightTheme.chipBgColor).copy(alpha = lightTheme.chipBgAlpha / 255f),
        night = Color(darkTheme.chipBgColor).copy(alpha = darkTheme.chipBgAlpha / 255f)
    )
    val titleColorProvider = DayNightColorProvider(
        day = Color(lightTheme.titleColor),
        night = Color(darkTheme.titleColor)
    )
    val subtitleColorProvider = DayNightColorProvider(
        day = Color(lightTheme.subtitleColor),
        night = Color(darkTheme.subtitleColor)
    )
    val dividerColorProvider = DayNightColorProvider(
        day = Color(lightTheme.dividerColor),
        night = Color(darkTheme.dividerColor)
    )

    val dateTitle = CalendarDateFormatter.formatWidgetHeaderDate(isNarrow = isNarrow)
        .ifBlank { context.getString(R.string.widget_default_title) }

    val openAppIntent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val addEventIntent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra("OPEN_ADD_EVENT", true)
    }

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(rootBgProvider)
            .cornerRadius(20.dp)
            .padding(12.dp)
            .semantics { testTag = "glance_widget_root" }
    ) {
        // Header row with 48dp accessibility touch targets
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!isNarrow) {
                Image(
                    provider = ImageProvider(R.drawable.ic_widget_calendar),
                    contentDescription = context.getString(R.string.title_google_calendar),
                    colorFilter = ColorFilter.tint(accentProvider),
                    modifier = GlanceModifier
                        .size(20.dp)
                        .clickable(actionStartActivity(openAppIntent))
                )
                Spacer(modifier = GlanceModifier.width(8.dp))
            }

            Column(
                modifier = GlanceModifier
                    .defaultWeight()
                    .clickable(actionStartActivity(openAppIntent))
            ) {
                Text(
                    text = dateTitle,
                    style = TextStyle(
                        color = titleColorProvider,
                        fontSize = prefs.density.titleSp.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1,
                    modifier = GlanceModifier.semantics { testTag = "glance_date_title" }
                )

                val syncStatusText = if (isSyncing) {
                    context.getString(R.string.widget_sync_in_progress)
                } else {
                    CalendarDateFormatter.formatLastSyncIndicator(
                        context = context,
                        lastSyncMillis = lastSyncMillis,
                        compact = isNarrow
                    )
                }
                val syncDotProvider = when {
                    isSyncing -> accentProvider
                    lastSyncMillis > 0L -> DayNightColorProvider(
                        day = Color(0xFF137333),
                        night = Color(0xFF81C995)
                    )
                    else -> subtitleColorProvider
                }
                Row(
                    modifier = GlanceModifier
                        .padding(top = 1.dp)
                        .semantics { testTag = "glance_sync_status" },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = GlanceModifier
                            .size(6.dp)
                            .cornerRadius(3.dp)
                            .background(syncDotProvider)
                    ) {}
                    Spacer(modifier = GlanceModifier.width(4.dp))
                    Text(
                        text = syncStatusText,
                        style = TextStyle(
                            color = subtitleColorProvider,
                            fontSize = (prefs.density.timeSp - 2f).coerceIn(9f, 11f).sp
                        ),
                        maxLines = 1,
                        modifier = GlanceModifier.semantics { testTag = "glance_sync_time_text" }
                    )
                }
            }

            // 48dp accessible touch target for Add Event
            Box(
                modifier = GlanceModifier
                    .size(48.dp)
                    .clickable(actionStartActivity(addEventIntent))
                    .semantics { testTag = "glance_add_event_button" },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = GlanceModifier
                        .size(36.dp)
                        .background(chipBgProvider)
                        .cornerRadius(18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_widget_add),
                        contentDescription = context.getString(R.string.widget_new_event_desc),
                        colorFilter = ColorFilter.tint(accentProvider),
                        modifier = GlanceModifier.size(18.dp)
                    )
                }
            }

            // 48dp accessible touch target for Manual Refresh
            Box(
                modifier = GlanceModifier
                    .size(48.dp)
                    .clickable(actionRunCallback<RefreshCalendarGlanceAction>())
                    .semantics { testTag = "glance_refresh_button" },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = GlanceModifier
                        .size(36.dp)
                        .background(chipBgProvider)
                        .cornerRadius(18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_widget_refresh),
                        contentDescription = context.getString(R.string.widget_refresh_desc),
                        colorFilter = ColorFilter.tint(accentProvider),
                        modifier = GlanceModifier.size(18.dp)
                    )
                }
            }
        }

        // Divider
        Spacer(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(1.dp)
                .background(dividerColorProvider)
        )
        Spacer(modifier = GlanceModifier.height(8.dp))

        if (events.isEmpty()) {
            val emptyMessage = if (!hasPermission) {
                context.getString(R.string.status_tap_to_connect)
            } else {
                context.getString(R.string.widget_no_events)
            }
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .clickable(
                        if (!hasPermission) {
                            actionStartActivity(openAppIntent)
                        } else {
                            actionRunCallback<RefreshCalendarGlanceAction>()
                        }
                    )
                    .semantics { testTag = "glance_empty_state" },
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = GlanceModifier
                        .size(44.dp)
                        .cornerRadius(22.dp)
                        .background(chipBgProvider)
                        .semantics { testTag = "glance_empty_illustration" },
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_widget_calendar),
                        contentDescription = context.getString(R.string.widget_no_events),
                        colorFilter = ColorFilter.tint(accentProvider),
                        modifier = GlanceModifier.size(22.dp)
                    )
                }
                Spacer(modifier = GlanceModifier.height(8.dp))
                Text(
                    text = emptyMessage,
                    style = TextStyle(
                        color = titleColorProvider,
                        fontSize = (prefs.density.titleSp - 1f).coerceAtLeast(11f).sp,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 2,
                    modifier = GlanceModifier
                        .padding(horizontal = 8.dp)
                        .semantics { testTag = "glance_empty_text" }
                )
                if (hasPermission && size.height >= 145.dp) {
                    Spacer(modifier = GlanceModifier.height(8.dp))
                    Row(
                        modifier = GlanceModifier
                            .background(chipBgProvider)
                            .cornerRadius(16.dp)
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                            .clickable(actionStartActivity(addEventIntent))
                            .semantics { testTag = "glance_empty_add_action" },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            provider = ImageProvider(R.drawable.ic_widget_add),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(accentProvider),
                            modifier = GlanceModifier.size(14.dp)
                        )
                        Spacer(modifier = GlanceModifier.width(4.dp))
                        Text(
                            text = context.getString(R.string.widget_empty_action_add),
                            style = TextStyle(
                                color = accentProvider,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .semantics { testTag = "glance_agenda_list" }
            ) {
                items(events, itemId = { it.id.hashCode().toLong() }) { event ->
                    GlanceAgendaItemRow(
                        event = event,
                        prefs = prefs,
                        lightTheme = lightTheme,
                        darkTheme = darkTheme,
                        itemBgProvider = itemBgProvider,
                        chipBgProvider = chipBgProvider,
                        accentProvider = accentProvider,
                        titleColorProvider = titleColorProvider,
                        subtitleColorProvider = subtitleColorProvider,
                        isNarrow = isNarrow
                    )
                }
            }
        }
    }
}

@Composable
private fun GlanceAgendaItemRow(
    event: EventEntity,
    prefs: WidgetPreferences,
    lightTheme: ResolvedWidgetTheme,
    darkTheme: ResolvedWidgetTheme,
    itemBgProvider: ColorProvider,
    chipBgProvider: ColorProvider,
    accentProvider: ColorProvider,
    titleColorProvider: ColorProvider,
    subtitleColorProvider: ColorProvider,
    isNarrow: Boolean
) {
    val context = LocalContext.current
    val eventBarProvider = DayNightColorProvider(
        day = Color(WidgetThemeHelper.resolveEventBarColor(event.colorHex, lightTheme)),
        night = Color(WidgetThemeHelper.resolveEventBarColor(event.colorHex, darkTheme))
    )
    val eventTimeProvider = DayNightColorProvider(
        day = Color(WidgetThemeHelper.resolveEventTimeColor(event.colorHex, lightTheme)),
        night = Color(WidgetThemeHelper.resolveEventTimeColor(event.colorHex, darkTheme))
    )
    val liveCueTextProvider = DayNightColorProvider(
        day = Color.White,
        night = Color(0xFF0A192F)
    )
    val timingCue = CalendarDateFormatter.formatEventTimingCue(
        context = context,
        startMillis = event.startMillis,
        endMillis = event.endMillis,
        isAllDay = event.isAllDay
    )
    val openEventIntent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra(CalendarAgendaWidgetProvider.EXTRA_EVENT_ID, event.id)
        putExtra(CalendarAgendaWidgetProvider.EXTRA_EVENT_TITLE, event.title)
    }

    Column(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
    ) {
        if (prefs.showDayHeaders && event.isFirstOfDay && event.dayHeader.isNotBlank()) {
            val localizedHeader = CalendarDateFormatter.formatLocalizedDayHeader(context, event.startMillis)
            Text(
                text = localizedHeader,
                style = TextStyle(
                    color = accentProvider,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                ),
                modifier = GlanceModifier.padding(top = 2.dp, bottom = 4.dp)
            )
        }

        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(itemBgProvider)
                .cornerRadius(12.dp)
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .clickable(actionStartActivity(openEventIntent)),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = GlanceModifier
                    .padding(top = 2.dp)
                    .width(4.dp)
                    .height(28.dp)
                    .background(eventBarProvider)
                    .cornerRadius(2.dp)
            ) {}

            Spacer(modifier = GlanceModifier.width(10.dp))

            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = event.title,
                    style = TextStyle(
                        color = titleColorProvider,
                        fontSize = prefs.density.titleSp.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = prefs.titleLineLimit.maxLines,
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .semantics { testTag = "glance_event_title_${event.id}" }
                )

                val timeStr = CalendarDateFormatter.formatEventTime(
                    startMillis = event.startMillis,
                    endMillis = event.endMillis,
                    isAllDay = event.isAllDay,
                    context = context,
                    timeDisplayMode = prefs.timeDisplayMode
                )
                if (timeStr.isNotEmpty()) {
                    Text(
                        text = timeStr,
                        style = TextStyle(
                            color = eventTimeProvider,
                            fontSize = prefs.density.timeSp.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1,
                        modifier = GlanceModifier
                            .padding(top = 2.dp)
                            .semantics { testTag = "glance_event_time_${event.id}" }
                    )
                }

                if (prefs.showCalendarName && event.calendarId.isNotBlank()) {
                    Text(
                        text = event.calendarId,
                        style = TextStyle(
                            color = subtitleColorProvider,
                            fontSize = (prefs.density.timeSp - 2f).coerceAtLeast(9f).sp
                        ),
                        maxLines = 1,
                        modifier = GlanceModifier
                            .padding(top = 1.dp)
                            .semantics { testTag = "glance_event_calendar_${event.id}" }
                    )
                }

                if (prefs.showLocations && !event.location.isNullOrBlank()) {
                    Text(
                        text = event.location,
                        style = TextStyle(
                            color = subtitleColorProvider,
                            fontSize = (prefs.density.timeSp - 1f).coerceAtLeast(9f).sp
                        ),
                        maxLines = 1,
                        modifier = GlanceModifier
                            .padding(top = 1.dp)
                            .semantics { testTag = "glance_event_location_${event.id}" }
                    )
                }
            }

            if (timingCue != null && !isNarrow) {
                Spacer(modifier = GlanceModifier.width(6.dp))
                Box(
                    modifier = GlanceModifier
                        .background(if (timingCue.isLive) accentProvider else chipBgProvider)
                        .cornerRadius(8.dp)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        .semantics { testTag = "glance_event_cue_${event.id}" }
                ) {
                    Text(
                        text = timingCue.label,
                        style = TextStyle(
                            color = if (timingCue.isLive) liveCueTextProvider else accentProvider,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

class RefreshCalendarGlanceAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val appContext = context.applicationContext
        AppLogger.d(AppLogger.TAG_SYNC, "Glance manual refresh tapped for glanceId=$glanceId")
        try {
            updateAppWidgetState(
                context = appContext,
                definition = PreferencesGlanceStateDefinition,
                glanceId = glanceId
            ) { prefs ->
                prefs.toMutablePreferences().apply {
                    this[CalendarGlanceWidget.KEY_IS_SYNCING] = true
                }
            }
            CalendarGlanceWidget().update(appContext, glanceId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.w(AppLogger.TAG_WIDGET, "Failed setting Glance syncing state before sync", e)
        }
        CalendarSyncScheduler.enqueueImmediateSync(appContext, forceRefresh = true)
    }
}

object GlanceWidgetUpdater {
    @Volatile
    internal var testUpdateObserver: ((glanceIds: List<GlanceId>, syncTimestampMillis: Long) -> Unit)? = null

    suspend fun updateAllGlanceWidgets(
        context: Context,
        syncTimestampMillis: Long = System.currentTimeMillis()
    ): Int {
        val appContext = context.applicationContext
        return try {
            val manager = GlanceAppWidgetManager(appContext)
            val glanceIds = manager.getGlanceIds(CalendarGlanceWidget::class.java)
            testUpdateObserver?.invoke(glanceIds, syncTimestampMillis)

            val glanceWidget = CalendarGlanceWidget()
            for (glanceId in glanceIds) {
                updateAppWidgetState(
                    context = appContext,
                    definition = PreferencesGlanceStateDefinition,
                    glanceId = glanceId
                ) { prefs ->
                    prefs.toMutablePreferences().apply {
                        val nextRev = (this[CalendarGlanceWidget.KEY_SYNC_REVISION] ?: 0) + 1
                        this[CalendarGlanceWidget.KEY_SYNC_REVISION] = nextRev
                        this[CalendarGlanceWidget.KEY_LAST_SYNC_MILLIS] = syncTimestampMillis
                        this[CalendarGlanceWidget.KEY_IS_SYNCING] = false
                    }
                }
                glanceWidget.update(appContext, glanceId)
            }
            AppLogger.d(
                AppLogger.TAG_SYNC,
                "GlanceAppWidgetManager triggered update for ${glanceIds.size} Glance widget(s)"
            )
            glanceIds.size
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.w(AppLogger.TAG_WIDGET, "Failed updating Glance widgets via GlanceAppWidgetManager", e)
            0
        }
    }
}
