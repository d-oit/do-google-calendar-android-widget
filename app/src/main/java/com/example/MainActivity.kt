package com.example

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PersistableBundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.db.AppDatabase
import com.example.data.pref.WidgetPreferencesManager
import com.example.data.repository.CalendarRepository
import com.example.data.repository.CalendarSyncDiagnosticsHelper
import com.example.ui.CalendarViewModel
import com.example.ui.ErrorAction
import com.example.ui.UiEvent
import com.example.ui.components.AboutReleaseBottomSheet
import com.example.ui.components.AddEventDialog
import com.example.ui.components.AgendaScheduleContent
import com.example.ui.components.CalendarPermissionRequestCard
import com.example.ui.components.EventDetailDialog
import com.example.ui.components.SyncDiagnosticsBottomSheet
import com.example.ui.components.WidgetCustomizerControls
import com.example.ui.components.WidgetPinChooserDialog
import com.example.ui.components.WidgetPreviewCard
import com.example.ui.components.rememberCalendarPermissionLauncher
import com.example.ui.theme.AgendaWidgetTheme
import com.example.util.AppLogger
import com.example.util.RuntimeEnvironment
import com.example.widget.CalendarAgendaWidgetProvider
import com.example.worker.CalendarSyncScheduler

class MainActivity : ComponentActivity() {

    private val viewModel: CalendarViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val app = applicationContext as? CalendarApp
                val repo = app?.repository ?: CalendarRepository(
                    applicationContext,
                    AppDatabase.getInstance(applicationContext).eventDao()
                )
                val prefs = app?.preferencesManager ?: WidgetPreferencesManager.getInstance(applicationContext)
                return CalendarViewModel(repo, prefs) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val initialTab = handleIntentExtras(intent)

        if (!RuntimeEnvironment.isRunningInTestEnvironment) {
            setContent {
                AgendaWidgetTheme {
                    CalendarMainScreen(
                        viewModel = viewModel,
                        initialTab = initialTab
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntentExtras(intent)
    }

    private fun handleIntentExtras(incomingIntent: Intent?): Int {
        if (incomingIntent == null) return 0

        if (incomingIntent.getBooleanExtra("OPEN_ADD_EVENT", false)) {
            viewModel.openAddEventDialog()
        }

        val eventId = incomingIntent.getStringExtra(CalendarAgendaWidgetProvider.EXTRA_EVENT_ID)
        if (!eventId.isNullOrEmpty()) {
            viewModel.openEventById(eventId)
        }

        val appWidgetId = incomingIntent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        if (incomingIntent.action == AppWidgetManager.ACTION_APPWIDGET_CONFIGURE &&
            appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID
        ) {
            val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            setResult(RESULT_OK, resultValue)
            return 1
        }
        return 0
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarMainScreen(
    viewModel: CalendarViewModel,
    initialTab: Int = 0
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab by remember(initialTab) { mutableIntStateOf(initialTab) }
    val snackbarHostState = remember { SnackbarHostState() }

    val calendarPermissionLauncher = rememberCalendarPermissionLauncher { isGranted ->
        viewModel.onPermissionResult(isGranted)
        if (isGranted) {
            CalendarSyncScheduler.schedulePeriodicSync(context)
        }
    }

    // Lifecycle resume sync & midnight/timezone rollover listener
    DisposableEffect(lifecycleOwner, viewModel, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && !RuntimeEnvironment.isRunningInTestEnvironment) {
                viewModel.onLifecycleResume()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        val dateChangeReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                viewModel.refreshDayBoundaryIfNeeded()
            }
        }
        var receiverRegistered = false
        if (!RuntimeEnvironment.isRunningInTestEnvironment) {
            try {
                val filter = IntentFilter().apply {
                    addAction(Intent.ACTION_DATE_CHANGED)
                    addAction(Intent.ACTION_TIME_CHANGED)
                    addAction(Intent.ACTION_TIMEZONE_CHANGED)
                }
                ContextCompat.registerReceiver(
                    context,
                    dateChangeReceiver,
                    filter,
                    ContextCompat.RECEIVER_NOT_EXPORTED
                )
                receiverRegistered = true
            } catch (e: Exception) {
                AppLogger.w(AppLogger.TAG_UI, "Unable to register date rollover BroadcastReceiver", e)
            }
        }

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (receiverRegistered) {
                runCatching { context.unregisterReceiver(dateChangeReceiver) }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!uiState.hasCalendarPermission && !RuntimeEnvironment.isRunningInTestEnvironment) {
            calendarPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_CALENDAR,
                    Manifest.permission.WRITE_CALENDAR
                )
            )
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.uiEvents.collect { event ->
            when (event) {
                is UiEvent.ShowSnackbar -> {
                    val result = snackbarHostState.showSnackbar(
                        message = event.message,
                        actionLabel = event.actionLabel,
                        withDismissAction = true,
                        duration = if (event.isError) SnackbarDuration.Long else SnackbarDuration.Short
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        when (event.action) {
                            ErrorAction.RETRY_SYNC -> viewModel.onSyncNow()
                            ErrorAction.OPEN_SETTINGS -> openAppPermissionSettings(context)
                            ErrorAction.NONE -> Unit
                        }
                    }
                    viewModel.dismissError()
                }
            }
        }
    }

    val rotationAngle = if (uiState.isSyncing && !RuntimeEnvironment.isRunningInTestEnvironment) {
        val infiniteTransition = rememberInfiniteTransition(label = "sync_rotation")
        val angle by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 800, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "rotation"
        )
        angle
    } else {
        0f
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .navigationBarsPadding()
                    .testTag("main_snackbar_host")
            ) { snackbarData ->
                Snackbar(
                    snackbarData = snackbarData,
                    containerColor = if (uiState.error != null) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.inverseSurface
                    },
                    contentColor = if (uiState.error != null) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.inverseOnSurface
                    },
                    actionColor = if (uiState.error != null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.inversePrimary
                    }
                )
            }
        },
        topBar = {
            TopAppBar(
                modifier = Modifier.statusBarsPadding(),
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.CalendarToday,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { viewModel.openDiagnosticsSheet() }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                .testTag("topbar_account_status_button")
                        ) {
                            Text(
                                text = stringResource(R.string.title_google_calendar),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = stringResource(
                                        if (uiState.hasCalendarPermission) {
                                            R.string.diagnostics_value_granted
                                        } else {
                                            R.string.diagnostics_value_missing
                                        }
                                    ),
                                    tint = if (uiState.hasCalendarPermission && uiState.availableCalendars.isNotEmpty()) {
                                        MaterialTheme.colorScheme.tertiary
                                    } else {
                                        MaterialTheme.colorScheme.secondary
                                    },
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = when {
                                        !uiState.hasCalendarPermission -> stringResource(R.string.status_tap_to_connect)
                                        uiState.availableCalendars.isNotEmpty() -> uiState.syncedAccountName
                                            ?: stringResource(
                                                R.string.diagnostics_section_calendars,
                                                uiState.availableCalendars.size
                                            )
                                        else -> stringResource(R.string.status_local_calendar)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.openAboutSheet() },
                        modifier = Modifier.testTag("topbar_about_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = stringResource(R.string.about_open_desc),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    IconButton(
                        onClick = { viewModel.openDiagnosticsSheet() },
                        modifier = Modifier.testTag("topbar_diagnostics_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Build,
                            contentDescription = stringResource(R.string.diagnostics_open_desc),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    IconButton(
                        onClick = { viewModel.onSyncNow() },
                        enabled = !uiState.isSyncing,
                        modifier = Modifier.testTag("topbar_sync_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.action_sync_calendar),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(24.dp)
                                .then(if (uiState.isSyncing) Modifier.rotate(rotationAngle) else Modifier)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.openAddEventDialog() },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape,
                modifier = Modifier
                    .navigationBarsPadding()
                    .testTag("add_event_fab")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.action_add_event)
                )
            }
        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            val isWideScreen = maxWidth >= 600.dp

            if (isWideScreen) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .testTag("dual_pane_container"),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Left scrollable pane: Permission card + Live Widget Preview + Customizer Controls
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .testTag("left_pane_content"),
                        contentPadding = PaddingValues(bottom = 88.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            CalendarPermissionRequestCard(
                                hasCalendarPermission = uiState.hasCalendarPermission,
                                syncedAccount = uiState.syncedAccountName ?: stringResource(R.string.title_google_calendar),
                                onRequestPermission = {
                                    calendarPermissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.READ_CALENDAR,
                                            Manifest.permission.WRITE_CALENDAR
                                        )
                                    )
                                },
                                onSync = { viewModel.onSyncNow() }
                            )
                        }

                        item {
                            WidgetPreviewCard(
                                events = uiState.events,
                                preferences = uiState.widgetPreferences,
                                isSyncing = uiState.isSyncing,
                                lastSyncTime = uiState.lastSyncTime,
                                onRefresh = { viewModel.onSyncNow() },
                                onPinWidget = { viewModel.openPinWidgetChooser() },
                                onAddEvent = { viewModel.openAddEventDialog() }
                            )
                        }

                        item {
                            WidgetCustomizerControls(
                                preferences = uiState.widgetPreferences,
                                availableCalendars = uiState.availableCalendars,
                                onUpdatePreferences = { viewModel.updateWidgetPreferences(it) }
                            )
                        }
                    }

                    // Right full-height pane: Dedicated scrollable Agenda Schedule
                    Column(
                        modifier = Modifier
                            .weight(1.15f)
                            .fillMaxHeight()
                            .testTag("right_pane_content")
                    ) {
                        AgendaScheduleContent(
                            uiState = uiState,
                            onSearchQueryChanged = { viewModel.onSearchQueryChanged(it) },
                            onCategorySelected = { viewModel.onCategorySelected(it) },
                            onSelectEvent = { viewModel.selectEvent(it) },
                            onDeleteEvent = { viewModel.deleteEvent(it) },
                            onOpenAddEvent = { viewModel.openAddEventDialog() },
                            onSyncNow = { viewModel.onSyncNow() },
                            onOpenDiagnostics = { viewModel.openDiagnosticsSheet() },
                            onExpandFiltersQuickFix = { viewModel.applyFilterQuickFix() },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("compact_container")
                ) {
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text("${stringResource(R.string.tab_agenda)} (${uiState.events.size})", fontWeight = FontWeight.SemiBold) },
                            icon = { Icon(Icons.Default.ViewAgenda, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            modifier = Modifier.testTag("tab_agenda")
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { Text(stringResource(R.string.tab_widget_setup), fontWeight = FontWeight.SemiBold) },
                            icon = { Icon(Icons.Default.Widgets, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            modifier = Modifier.testTag("tab_widget")
                        )
                    }

                    if (selectedTab == 0) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp)
                        ) {
                            if (!uiState.hasCalendarPermission) {
                                Spacer(modifier = Modifier.height(12.dp))
                                CalendarPermissionRequestCard(
                                    hasCalendarPermission = uiState.hasCalendarPermission,
                                    syncedAccount = uiState.syncedAccountName ?: stringResource(R.string.title_google_calendar),
                                    onRequestPermission = {
                                        calendarPermissionLauncher.launch(
                                            arrayOf(
                                                Manifest.permission.READ_CALENDAR,
                                                Manifest.permission.WRITE_CALENDAR
                                            )
                                        )
                                    },
                                    onSync = { viewModel.onSyncNow() }
                                )
                            }
                            AgendaScheduleContent(
                                uiState = uiState,
                                onSearchQueryChanged = { viewModel.onSearchQueryChanged(it) },
                                onCategorySelected = { viewModel.onCategorySelected(it) },
                                onSelectEvent = { viewModel.selectEvent(it) },
                                onDeleteEvent = { viewModel.deleteEvent(it) },
                                onOpenAddEvent = { viewModel.openAddEventDialog() },
                                onSyncNow = { viewModel.onSyncNow() },
                                onOpenDiagnostics = { viewModel.openDiagnosticsSheet() },
                                onExpandFiltersQuickFix = { viewModel.applyFilterQuickFix() },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            item {
                                WidgetPreviewCard(
                                    events = uiState.events,
                                    preferences = uiState.widgetPreferences,
                                    isSyncing = uiState.isSyncing,
                                    lastSyncTime = uiState.lastSyncTime,
                                    onRefresh = { viewModel.onSyncNow() },
                                    onPinWidget = { viewModel.openPinWidgetChooser() },
                                    onAddEvent = { viewModel.openAddEventDialog() }
                                )
                            }

                            item {
                                WidgetCustomizerControls(
                                    preferences = uiState.widgetPreferences,
                                    availableCalendars = uiState.availableCalendars,
                                    onUpdatePreferences = { viewModel.updateWidgetPreferences(it) }
                                )
                            }
                        }
                    }
                }
            }

            if (uiState.isSyncing || uiState.isFetching) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("fetching_progress_container"),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                        shadowElevation = 6.dp,
                        tonalElevation = 6.dp,
                        modifier = Modifier.size(56.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("circular_progress_indicator"),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 3.5.dp
                            )
                        }
                    }
                }
            }
        }
    }

    // Unified Create / Edit Event Dialog
    if (uiState.isAddEventDialogOpen) {
        val editing = uiState.editingEvent
        AddEventDialog(
            initialEvent = editing,
            availableCalendars = uiState.availableCalendars,
            onDismiss = { viewModel.closeAddEventDialog() },
            onConfirm = { title, location, description, start, end, isAllDay, color, targetCalId ->
                if (editing != null) {
                    viewModel.updateEvent(
                        eventId = editing.id,
                        title = title,
                        location = location,
                        description = description,
                        startMillis = start,
                        endMillis = end,
                        isAllDay = isAllDay,
                        colorHex = color,
                        targetCalendarId = targetCalId
                    )
                } else {
                    viewModel.addEvent(
                        title = title,
                        location = location,
                        startMillis = start,
                        endMillis = end,
                        colorHex = color,
                        description = description,
                        isAllDay = isAllDay,
                        targetCalendarId = targetCalId
                    )
                }
            }
        )
    }

    // Event Detail Dialog
    uiState.selectedEventForDetails?.let { event ->
        EventDetailDialog(
            event = event,
            onDismiss = { viewModel.selectEvent(null) },
            onEdit = { viewModel.openEditEventDialog(event) },
            onDelete = { viewModel.deleteEvent(event.id) }
        )
    }

    // Widget Pin Style Chooser (Jetpack Glance vs Classic Scrollable)
    if (uiState.isPinChooserOpen) {
        WidgetPinChooserDialog(
            onDismiss = { viewModel.closePinWidgetChooser() },
            onSelectWidgetType = { widgetType ->
                viewModel.pinWidgetToHomeScreen(context, widgetType)
            }
        )
    }

    // About & GitHub Releases Update Sheet
    if (uiState.isAboutSheetOpen) {
        AboutReleaseBottomSheet(
            updateCheckState = uiState.updateCheckState,
            onDismiss = { viewModel.closeAboutSheet() },
            onCheckForUpdates = { viewModel.checkForUpdates() },
            onOpenReleaseUrl = { url ->
                if (!openSafeHttpsUrl(context, url)) {
                    viewModel.notifyMessage(context.getString(R.string.msg_open_link_failed), isError = true)
                }
            },
            onOpenDiagnostics = { viewModel.openDiagnosticsSheet() },
            onOpenAppLanguageSettings = { openAppLanguageSettings(context) }
        )
    }

    // Calendar Sync Diagnostics & Repair Sheet
    if (uiState.isDiagnosticsSheetOpen) {
        SyncDiagnosticsBottomSheet(
            diagnostics = uiState.syncDiagnostics,
            isRunningDiagnostics = uiState.isRunningDiagnostics,
            isRepairingSync = uiState.isRepairingSync,
            onDismiss = { viewModel.closeDiagnosticsSheet() },
            onRefreshDiagnostics = { viewModel.runDiagnostics() },
            onRepairAndForceSync = { viewModel.repairAndForceAccountSync(context) },
            onExpandFiltersQuickFix = { viewModel.applyFilterQuickFix() },
            onOpenSyncSettings = {
                if (!openAndroidSyncSettings(context)) {
                    viewModel.notifyMessage(
                        context.getString(R.string.diagnostics_open_settings_failed),
                        isError = true
                    )
                }
            },
            onOpenGoogleCalendar = {
                if (!openGoogleCalendarApp(context)) {
                    viewModel.notifyMessage(
                        context.getString(R.string.msg_open_calendar_app_failed),
                        isError = true
                    )
                }
            },
            onCopyReport = { reportText ->
                copySensitiveTextToClipboard(context, reportText)
                viewModel.notifyMessage(context.getString(R.string.diagnostics_report_copied))
            }
        )
    }
}

private fun openAppPermissionSettings(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        AppLogger.w(AppLogger.TAG_UI, "Failed opening app settings directly, opening system settings", e)
        runCatching {
            context.startActivity(Intent(Settings.ACTION_SETTINGS).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
        }
    }
}

private fun openAppLanguageSettings(context: Context) {
    try {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Intent(Settings.ACTION_APP_LOCALE_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        AppLogger.w(AppLogger.TAG_UI, "Unable to open per-app locale settings", e)
        openAppPermissionSettings(context)
    }
}

private fun openSafeHttpsUrl(context: Context, url: String): Boolean {
    val parsed = runCatching { Uri.parse(url) }.getOrNull() ?: return false
    if (!parsed.scheme.equals("https", ignoreCase = true) || parsed.host.isNullOrBlank()) {
        return false
    }
    return try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, parsed).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        )
        true
    } catch (e: Exception) {
        AppLogger.w(AppLogger.TAG_UI, "Failed launching release URL in browser", e)
        false
    }
}

private fun openAndroidSyncSettings(context: Context): Boolean {
    return try {
        val syncIntent = Intent(Settings.ACTION_SYNC_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(syncIntent)
        true
    } catch (e: Exception) {
        AppLogger.w(AppLogger.TAG_UI, "ACTION_SYNC_SETTINGS unavailable, falling back to Settings", e)
        try {
            context.startActivity(Intent(Settings.ACTION_SETTINGS).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
            true
        } catch (fallbackErr: Exception) {
            AppLogger.e(AppLogger.TAG_UI, "Unable to open Android Settings", fallbackErr)
            false
        }
    }
}

private fun openGoogleCalendarApp(context: Context): Boolean {
    return try {
        val launchIntent = context.packageManager
            .getLaunchIntentForPackage(CalendarSyncDiagnosticsHelper.GOOGLE_CALENDAR_PACKAGE)
            ?.apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
        if (launchIntent != null) {
            context.startActivity(launchIntent)
            return true
        }
        val viewIntent = Intent(Intent.ACTION_VIEW, Uri.parse("content://com.android.calendar/time")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(viewIntent)
        true
    } catch (e: Exception) {
        AppLogger.w(AppLogger.TAG_UI, "Unable to open Google Calendar app on device", e)
        false
    }
}

private fun copySensitiveTextToClipboard(context: Context, text: String) {
    try {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        val clip = ClipData.newPlainText("Calendar Sync Diagnostics", text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            clip.description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }
        clipboard.setPrimaryClip(clip)
    } catch (e: Exception) {
        AppLogger.w(AppLogger.TAG_UI, "Unable to copy diagnostics report to clipboard", e)
    }
}
