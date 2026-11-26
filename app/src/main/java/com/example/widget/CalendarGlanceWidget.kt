package com.example.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
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
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
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

            GlanceTheme {
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

    val rootBg = Color(theme.rootBgColor).copy(alpha = theme.rootAlpha / 255f)
    val itemBg = Color(theme.itemBgColor).copy(alpha = theme.itemAlpha / 255f)
    val accent = Color(theme.accentColor)
    val chipBg = Color(theme.chipBgColor).copy(alpha = theme.chipBgAlpha / 255f)
    val titleColor = Color(theme.titleColor)
    val subtitleColor = Color(theme.subtitleColor)
    val dividerColor = Color(theme.dividerColor)

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
            .background(ColorProvider(rootBg))
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
                    colorFilter = ColorFilter.tint(ColorProvider(accent)),
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
                        color = ColorProvider(titleColor),
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
                val syncDotColor = when {
                    isSyncing -> accent
                    lastSyncMillis > 0L -> if (theme.isDark) Color(0xFF81C995) else Color(0xFF137333)
                    else -> subtitleColor
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
                            .background(ColorProvider(syncDotColor))
                    ) {}
                    Spacer(modifier = GlanceModifier.width(4.dp))
                    Text(
                        text = syncStatusText,
                        style = TextStyle(
                            color = ColorProvider(subtitleColor),
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
                        .background(ColorProvider(chipBg))
                        .cornerRadius(18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_widget_add),
                        contentDescription = context.getString(R.string.widget_new_event_desc),
                        colorFilter = ColorFilter.tint(ColorProvider(accent)),
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
                        .background(ColorProvider(chipBg))
                        .cornerRadius(18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_widget_refresh),
                        contentDescription = context.getString(R.string.widget_refresh_desc),
                        colorFilter = ColorFilter.tint(ColorProvider(accent)),
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
                .background(ColorProvider(dividerColor))
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
                if (size.height >= 160.dp) {
                    Image(
                        provider = ImageProvider(R.drawable.img_empty_calendar),
                        contentDescription = context.getString(R.string.empty_calendar_illustration_desc),
                        contentScale = ContentScale.Crop,
                        modifier = GlanceModifier
                            .width(96.dp)
                            .height(72.dp)
                            .cornerRadius(14.dp)
                            .semantics { testTag = "glance_empty_illustration" }
                    )
                } else {
                    Image(
                        provider = ImageProvider(R.drawable.ic_widget_calendar),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(ColorProvider(accent)),
                        modifier = GlanceModifier.size(28.dp)
                    )
                }
                Spacer(modifier = GlanceModifier.height(6.dp))
                Text(
                    text = emptyMessage,
                    style = TextStyle(
                        color = ColorProvider(subtitleColor),
                        fontSize = prefs.density.timeSp.sp
                    ),
                    modifier = GlanceModifier.semantics { testTag = "glance_empty_text" }
                )
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
                        itemBg = itemBg,
                        accentColor = accent,
                        titleColor = titleColor,
                        subtitleColor = subtitleColor
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
    itemBg: Color,
    accentColor: Color,
    titleColor: Color,
    subtitleColor: Color
) {
    val context = LocalContext.current
    val eventColorInt = try {
        CalendarColorMapper.parseColorInt(event.colorHex)
    } catch (_: Exception) {
        AndroidColor.parseColor("#1A73E8")
    }
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
                    color = ColorProvider(accentColor),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                ),
                modifier = GlanceModifier.padding(top = 2.dp, bottom = 4.dp)
            )
        }

        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(ColorProvider(itemBg))
                .cornerRadius(12.dp)
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .clickable(actionStartActivity(openEventIntent)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = GlanceModifier
                    .width(4.dp)
                    .height(28.dp)
                    .background(ColorProvider(Color(eventColorInt)))
                    .cornerRadius(2.dp)
            ) {}

            Spacer(modifier = GlanceModifier.width(10.dp))

            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = event.title,
                    style = TextStyle(
                        color = ColorProvider(titleColor),
                        fontSize = prefs.density.titleSp.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1
                )

                val timeStr = CalendarDateFormatter.formatEventTime(
                    startMillis = event.startMillis,
                    endMillis = event.endMillis,
                    isAllDay = event.isAllDay,
                    context = context
                )
                val subtitle = if (prefs.showLocations && !event.location.isNullOrBlank()) {
                    "$timeStr • ${event.location}"
                } else {
                    timeStr
                }
                Text(
                    text = subtitle,
                    style = TextStyle(
                        color = ColorProvider(subtitleColor),
                        fontSize = prefs.density.timeSp.sp
                    ),
                    maxLines = 1
                )
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
