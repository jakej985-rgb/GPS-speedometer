# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /usr/local/android-sdk/tools/proguard/proguard-android.txt

# Room Database
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# osmdroid
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
