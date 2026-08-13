# Keep line numbers for stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Kotlin Coroutines
-keepclassmembers class kotlinx.coroutines.android.HandlerDispatcher {
    <init>(...);
}

# Room Database
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Moshi / JSON Serialization
-keepattributes *Annotation*
-keep class com.example.data.** { *; }
-keepclassmembers class * {
    @com.squareup.moshi.* <fields>;
    @com.squareup.moshi.* <methods>;
}

# Firebase & Play Services
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**
