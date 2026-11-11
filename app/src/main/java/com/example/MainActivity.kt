package com.example

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.EventEntity
import com.example.data.repository.CalendarRepository
import com.example.ui.CalendarUiState
import com.example.ui.CalendarViewModel
import com.example.ui.components.AddEventDialog
import com.example.ui.components.CalendarPermissionRequestCard
import com.example.ui.components.EventDetailDialog
import com.example.ui.components.EventItemCard
import com.example.ui.components.EventListLazyColumn
import com.example.ui.components.FriendlyEmptyEventsState
import com.example.ui.components.WidgetCustomizerControls
import com.example.ui.components.WidgetPreviewCard
import com.example.ui.components.rememberCalendarPermissionLauncher
import com.example.ui.theme.MyApplicationTheme
import com.example.widget.CalendarAgendaWidgetProvider
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: CalendarViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val app = applicationContext as? CalendarApp
                val repo = app?.repository ?: CalendarRepository(
                    applicationContext,
                    com.example.data.db.AppDatabase.getInstance(applicationContext).eventDao()
                )
                val prefs = app?.preferencesManager ?: com.example.data.pref.WidgetPreferencesManager.getInstance(applicationContext)
                return CalendarViewModel(repo, prefs) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleIntentExtras()

        if (!isRunningInTestEnvironment()) {
            setContent {
                MyApplicationTheme {
                    CalendarMainScreen(viewModel = viewModel)
                }
            }
        }
    }

    private fun handleIntentExtras() {
        if (intent.getBooleanExtra("OPEN_ADD_EVENT", false)) {
            viewModel.openAddEventDialog()
        }
        val eventId = intent.getStringExtra(CalendarAgendaWidgetProvider.EXTRA_EVENT_ID)
        if (!eventId.isNullOrEmpty()) {
            val event = viewModel.uiState.value.events.find { it.id == eventId }
            if (event != null) {
                viewModel.selectEvent(event)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarMainScreen(
    viewModel: CalendarViewModel
) {
    val context = LocalContext.current
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(0) }

    val handleGoogleSignIn: () -> Unit = {
        coroutineScope.launch {
            try {
                val credentialManager = androidx.credentials.CredentialManager.create(context)
                val googleIdOption = com.google.android.libraries.identity.googleid.GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId("667310255229-9shoocqfrpvprl1ec0rftqabo3uect8p.apps.googleusercontent.com")
                    .setAutoSelectEnabled(false)
                    .build()

                val request = androidx.credentials.GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

                val result = credentialManager.getCredential(context, request)
                val credential = result.credential
                if (credential is androidx.credentials.CustomCredential &&
                    credential.type == com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    val googleIdTokenCredential = com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.createFrom(credential.data)
                    android.widget.Toast.makeText(context, "Connected as ${googleIdTokenCredential.id}", android.widget.Toast.LENGTH_SHORT).show()
                    viewModel.onPermissionResult(true)
                }
            } catch (_: Exception) {
                android.widget.Toast.makeText(
                    context,
                    "In this emulator, please grant calendar permission or add account in Android Settings.",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    val calendarPermissionLauncher = rememberCalendarPermissionLauncher { isGranted ->
        viewModel.onPermissionResult(isGranted)
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (!uiState.hasCalendarPermission && !isRunningInTestEnvironment()) {
            calendarPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_CALENDAR,
                    Manifest.permission.WRITE_CALENDAR
                )
            )
        }
    }

    val rotationAngle = if (uiState.isSyncing && !isRunningInTestEnvironment()) {
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
        topBar = {
            TopAppBar(
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
                        Column {
                            Text(
                                text = "Google Calendar",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Connected",
                                    tint = if (uiState.hasCalendarPermission && uiState.availableCalendars.isNotEmpty()) Color(0xFF34A853) else Color(0xFFFBBC04),
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = when {
                                        !uiState.hasCalendarPermission -> "Tap to connect Google Calendar"
                                        uiState.availableCalendars.isNotEmpty() -> uiState.syncedAccountName ?: "${uiState.availableCalendars.size} Calendars Synced"
                                        else -> "Local Device Calendar"
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
                        onClick = { viewModel.onSyncNow() },
                        enabled = !uiState.isSyncing,
                        modifier = Modifier.testTag("topbar_sync_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Sync with Google Calendar",
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
                modifier = Modifier.testTag("add_event_fab")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Event"
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
                // Wide Screen: Foldable unfolded & Tablets (>=600dp) -> Dual Pane Layout
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .testTag("dual_pane_container"),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Left Pane: Live Widget Preview + Status
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .testTag("left_pane_content"),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            CalendarPermissionRequestCard(
                                hasCalendarPermission = uiState.hasCalendarPermission,
                                syncedAccount = uiState.syncedAccountName ?: "Google Calendar",
                                onRequestPermission = {
                                    calendarPermissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.READ_CALENDAR,
                                            Manifest.permission.WRITE_CALENDAR
                                        )
                                    )
                                },
                                onSync = { viewModel.onSyncNow() },
                                onGoogleSignIn = handleGoogleSignIn
                            )
                        }

                        item {
                            WidgetPreviewCard(
                                events = uiState.events,
                                preferences = uiState.widgetPreferences,
                                isSyncing = uiState.isSyncing,
                                onRefresh = { viewModel.onSyncNow() },
                                onPinWidget = { viewModel.pinWidgetToHomeScreen(context) },
                                onAddEvent = { viewModel.openAddEventDialog() }
                            )
                        }
                    }

                    // Right Pane: Customizer Controls + Agenda List
                    Column(
                        modifier = Modifier
                            .weight(1.2f)
                            .fillMaxHeight()
                            .testTag("right_pane_content"),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        WidgetCustomizerControls(
                            preferences = uiState.widgetPreferences,
                            availableCalendars = uiState.availableCalendars,
                            onUpdatePreferences = { viewModel.updateWidgetPreferences(it) }
                        )

                        AgendaScheduleContent(
                            uiState = uiState,
                            onSearchQueryChanged = { viewModel.onSearchQueryChanged(it) },
                            onCategorySelected = { viewModel.onCategorySelected(it) },
                            onSelectEvent = { viewModel.selectEvent(it) },
                            onDeleteEvent = { viewModel.deleteEvent(it) },
                            onOpenAddEvent = { viewModel.openAddEventDialog() },
                            onSyncNow = { viewModel.onSyncNow() },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            } else {
                // Compact Screen: Phones (<600dp) -> Tabbed Navigation
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
                            text = { Text("Agenda (${uiState.events.size})", fontWeight = FontWeight.SemiBold) },
                            icon = { Icon(Icons.Default.ViewAgenda, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            modifier = Modifier.testTag("tab_agenda")
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { Text("Widget Setup", fontWeight = FontWeight.SemiBold) },
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
                                    syncedAccount = uiState.syncedAccountName ?: "Google Calendar",
                                    onRequestPermission = {
                                        calendarPermissionLauncher.launch(
                                            arrayOf(
                                                Manifest.permission.READ_CALENDAR,
                                                Manifest.permission.WRITE_CALENDAR
                                            )
                                        )
                                    },
                                    onSync = { viewModel.onSyncNow() },
                                    onGoogleSignIn = handleGoogleSignIn
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
                                modifier = Modifier.weight(1f)
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            contentPadding = PaddingValues(vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            item {
                                WidgetPreviewCard(
                                    events = uiState.events,
                                    preferences = uiState.widgetPreferences,
                                    isSyncing = uiState.isSyncing,
                                    onRefresh = { viewModel.onSyncNow() },
                                    onPinWidget = { viewModel.pinWidgetToHomeScreen(context) },
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

            // Centered CircularProgressIndicator that displays while CalendarRepository is actively fetching data
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

    // Add Event Dialog
    if (uiState.isAddEventDialogOpen) {
        AddEventDialog(
            onDismiss = { viewModel.closeAddEventDialog() },
            onConfirm = { title, location, start, end, color ->
                viewModel.addEvent(title, location, start, end, color)
            }
        )
    }

    // Event Detail Dialog
    uiState.selectedEventForDetails?.let { event ->
        EventDetailDialog(
            event = event,
            onDismiss = { viewModel.selectEvent(null) },
            onDelete = { viewModel.deleteEvent(event.id) }
        )
    }
}



@Composable
fun AgendaScheduleContent(
    uiState: CalendarUiState,
    onSearchQueryChanged: (String) -> Unit,
    onCategorySelected: (String) -> Unit,
    onSelectEvent: (EventEntity) -> Unit,
    onDeleteEvent: (String) -> Unit,
    onOpenAddEvent: () -> Unit,
    onSyncNow: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        Spacer(modifier = Modifier.height(12.dp))

        // Search field
        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = onSearchQueryChanged,
            placeholder = { Text("Search upcoming events...") },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            trailingIcon = {
                if (uiState.searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChanged("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("agenda_search_input")
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Category chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("All", "Today", "Tomorrow", "Work").forEach { category ->
                FilterChip(
                    selected = uiState.activeCategory == category,
                    onClick = { onCategorySelected(category) },
                    label = { Text(category) },
                    modifier = Modifier.testTag("category_chip_$category")
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        val timeframe = when (uiState.activeCategory) {
            "Today" -> "today"
            "Tomorrow" -> "tomorrow"
            "Work" -> "work categories"
            else -> if (uiState.widgetPreferences.daysHorizon == 1) "today" else "the next ${uiState.widgetPreferences.daysHorizon} days"
        }

        // LazyColumn component displaying events with title, time, color indicator
        // or a friendly empty state component inside the LazyColumn when no events are scheduled.
        // Wrapped with PullToRefreshBox to force-sync calendar data from ContentProvider.
        EventListLazyColumn(
            events = uiState.filteredEvents,
            timeframe = timeframe,
            isRefreshing = uiState.isSyncing,
            onRefresh = onSyncNow,
            isSearchActive = uiState.searchQuery.isNotBlank(),
            searchQuery = uiState.searchQuery,
            hasCalendarPermission = uiState.hasCalendarPermission,
            syncedAccountName = uiState.syncedAccountName,
            availableCalendarsCount = uiState.availableCalendars.size,
            onEventClick = onSelectEvent,
            onDeleteEvent = onDeleteEvent,
            onAddEvent = onOpenAddEvent,
            onSyncNow = onSyncNow,
            modifier = Modifier.weight(1f)
        )
    }
}

private fun isRunningInTestEnvironment(): Boolean {
    return try {
        Class.forName("org.robolectric.Robolectric") != null
    } catch (_: Throwable) {
        false
    }
}
