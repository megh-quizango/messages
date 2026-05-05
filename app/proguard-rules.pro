# Add project specific ProGuard rules here.

# Preserve line numbers for crash reporting
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep annotations used by frameworks
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions

# ---- Firebase ----
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# Firebase Crashlytics
-keepattributes SourceFile,LineNumberTable
-keep public class * extends java.lang.Exception

# ---- AdMob / Google Ads ----
-keep class com.google.android.gms.ads.** { *; }
-keep class com.google.ads.** { *; }
-dontwarn com.google.android.gms.ads.**

# ---- Facebook SDK ----
-keep class com.facebook.** { *; }
-dontwarn com.facebook.**
-keep class com.facebook.appevents.** { *; }
-keepclassmembers class * {
    @com.facebook.UiThread *;
}

# ---- Room Database ----
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-dontwarn androidx.room.**

# ---- Picasso ----
-dontwarn com.squareup.okhttp.**
-dontwarn okio.**
-keep class com.squareup.picasso.** { *; }

# ---- Shimmer ----
-keep class com.facebook.shimmer.** { *; }

# ---- Application classes ----
-keep class com.text.messages.sms.messanger.MessagesApp { *; }
-keep class com.text.messages.sms.messanger.receiver.** { *; }
-keep class com.text.messages.sms.messanger.service.** { *; }
-keep class com.text.messages.sms.messanger.data.database.** { *; }
-keep class com.text.messages.sms.messanger.data.model.** { *; }

# ---- Gson ----
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * extends com.google.gson.TypeAdapter { *; }
-keep class * extends com.google.gson.TypeAdapterFactory { *; }
-keep class * extends com.google.gson.JsonSerializer { *; }
-keep class * extends com.google.gson.JsonDeserializer { *; }

# ---- Kotlin Coroutines ----
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# ---- ViewBinding ----
-keep class * implements androidx.viewbinding.ViewBinding {
    public static * inflate(android.view.LayoutInflater);
    public static * inflate(android.view.LayoutInflater, android.view.ViewGroup, boolean);
}

# ---- OkHttp / Conscrypt ----
-dontwarn org.conscrypt.Conscrypt
-dontwarn org.conscrypt.OpenSSLProvider

# ---- General ----
-keep class * extends android.app.Activity
-keep class * extends android.app.Service
-keep class * extends android.content.BroadcastReceiver
-keep class * extends android.content.ContentProvider
