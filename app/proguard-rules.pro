# R8 / ProGuard rules for Agenda Widget release builds

# Preserve line numbers for crash diagnostics while anonymizing source filenames
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Jetpack Glance ActionCallback implementations are instantiated reflectively by Glance
-keep class * implements androidx.glance.appwidget.action.ActionCallback {
    public <init>();
}

# Glance AppWidget receivers & widgets
-keep class com.example.widget.CalendarGlanceWidget { *; }
-keep class com.example.widget.CalendarGlanceWidgetReceiver { *; }

# Classic AppWidgetProvider & RemoteViewsService
-keep class com.example.widget.CalendarAgendaWidgetProvider { *; }
-keep class com.example.widget.CalendarAgendaWidgetService { *; }

# WorkManager CoroutineWorker instantiated via reflection
-keep class com.example.worker.CalendarSyncWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Room Database, Entities, and DAOs
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao interface *
-keep class com.example.data.db.AppDatabase_Impl { *; }
-keep class com.example.data.dao.EventDao_Impl { *; }

# Keep sealed error & update result metadata intact for diagnostics
-keep class com.example.data.model.AppError** { *; }
-keep class com.example.data.model.OperationResult** { *; }
-keep class com.example.data.update.SemVer { *; }
