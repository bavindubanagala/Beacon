# Hilt rules
-keepattributes *Annotation*
-keep class com.google.dagger.hilt.** { *; }
-keep class * extends androidx.lifecycle.ViewModel
-keep @dagger.hilt.android.lifecycle.HiltViewModel class *
-keep @dagger.hilt.android.AndroidEntryPoint class *
-keepclassmembers class * {
    @javax.inject.Inject <init>(...);
}

# Firebase & Domain Models
-keep class com.beacon.shared.models.** { *; }
-keepclassmembers class com.beacon.shared.models.** {
    <init>(...);
    private <fields>;
}

# Coroutines
-keep class kotlinx.coroutines.** { *; }
-keep class com.google.android.gms.tasks.Task { *; }

# Room
-keep class androidx.room.RoomDatabase
-keep class * extends androidx.room.RoomDatabase
-keep class androidx.room.Dao
-keep class * extends androidx.room.Dao
-keep class androidx.room.Entity
-keep class * extends androidx.room.Entity
-keep class com.beacon.tracker.db.** { *; }

# OSMDroid
-keep class org.osmdroid.** { *; }

# Strip Android log calls from release builds
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
}
