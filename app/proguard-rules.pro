# =============================================================================
# locationjoystick — ProGuard / R8 rules
# =============================================================================

# -----------------------------------------------------------------------------
# Hilt / Dagger
# -----------------------------------------------------------------------------
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class dagger.** { *; }
-keepclassmembers class * {
    @javax.inject.Inject <init>(...);
    @javax.inject.Inject <fields>;
}
-keepclassmembers class * extends dagger.hilt.android.lifecycle.HiltViewModel {
    @javax.inject.Inject <init>(...);
}

# -----------------------------------------------------------------------------
# Room
# -----------------------------------------------------------------------------
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep @androidx.room.Database class * { *; }
-keep @androidx.room.TypeConverter class * { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase {
    abstract *;
}
# Room uses reflection for migration classes
-keep class * extends androidx.room.migration.Migration { *; }
-keepclassmembers class * {
    @androidx.room.Query *;
    @androidx.room.Insert *;
    @androidx.room.Update *;
    @androidx.room.Delete *;
    @androidx.room.Transaction *;
    @androidx.room.RawQuery *;
}

# -----------------------------------------------------------------------------
# kotlinx.serialization
# -----------------------------------------------------------------------------
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# Keep @Serializable annotated classes and their companions
-keep,includedescriptorclasses class **$$serializer { *; }
-keepclassmembers class * {
    *** Companion;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
# Keep serialization descriptors and factories
-keep class kotlinx.serialization.** { *; }
-keepclassmembers @kotlinx.serialization.Serializable class * {
    static ** $serializer;
    static ** Companion;
    ** serializer();
    ** serializer(kotlinx.serialization.modules.SerializersModule);
}
# Keep data classes used for export/import (model package already kept below)
-keep @kotlinx.serialization.Serializable class * { *; }

# -----------------------------------------------------------------------------
# Model package (domain models — serialized to/from JSON)
# -----------------------------------------------------------------------------
-keep class com.locationjoystick.core.model.** { *; }

# -----------------------------------------------------------------------------
# Android base classes
# -----------------------------------------------------------------------------
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.view.View

# -----------------------------------------------------------------------------
# MapLibre (JNI + reflection-accessed classes)
# -----------------------------------------------------------------------------
-keep class org.maplibre.android.** { *; }
-keep interface org.maplibre.android.** { *; }
-dontwarn org.maplibre.android.**
# Native peer classes accessed via JNI
-keepclassmembers class org.maplibre.android.maps.** { *; }
-keepclassmembers class org.maplibre.android.style.** { *; }
-keepclassmembers class org.maplibre.android.geometry.** { *; }
-keepclassmembers class org.maplibre.android.location.** { *; }
# Keep JNI method names
-keepclasseswithmembernames class * {
    native <methods>;
}

# -----------------------------------------------------------------------------
# OkHttp
# -----------------------------------------------------------------------------
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keep class okio.** { *; }
# OkHttp internal platform detection
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# -----------------------------------------------------------------------------
# Retrofit
# -----------------------------------------------------------------------------
-keep class retrofit2.** { *; }
-keep interface retrofit2.** { *; }
-keepattributes Signature
-keepattributes Exceptions
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn retrofit2.**
# Retrofit uses reflection on method return types
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeInvisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations
-keepattributes RuntimeInvisibleParameterAnnotations

# -----------------------------------------------------------------------------
# ZXing (QR code scanning and generation)
# -----------------------------------------------------------------------------
-keep class com.google.zxing.** { *; }
-keep interface com.google.zxing.** { *; }
-dontwarn com.google.zxing.**
# Core decoder used for QR scanning
-keep class com.google.zxing.MultiFormatReader { *; }
-keep class com.google.zxing.qrcode.QRCodeReader { *; }
-keep class com.google.zxing.qrcode.QRCodeWriter { *; }
-keep class com.google.zxing.common.** { *; }
-keep class com.google.zxing.qrcode.** { *; }
# BarcodeFormat enum used in QrEncoder
-keep enum com.google.zxing.BarcodeFormat { *; }
-keep class com.google.zxing.EncodeHintType { *; }
-keep class com.journeyapps.barcodescanner.** { *; }
-dontwarn com.journeyapps.barcodescanner.**

# -----------------------------------------------------------------------------
# CameraX (used by QR scanner)
# -----------------------------------------------------------------------------
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# -----------------------------------------------------------------------------
# Kotlin coroutines
# -----------------------------------------------------------------------------
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**
-keep class kotlinx.coroutines.** { *; }

# -----------------------------------------------------------------------------
# Kotlin stdlib / reflection
# -----------------------------------------------------------------------------
-dontwarn kotlin.**
-keep class kotlin.Metadata { *; }
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}
-keepattributes EnclosingMethod
-keepattributes InnerClasses

# -----------------------------------------------------------------------------
# DataStore
# -----------------------------------------------------------------------------
-keep class androidx.datastore.** { *; }
-dontwarn androidx.datastore.**

# -----------------------------------------------------------------------------
# Compose (runtime kept by AGP; annotation classes need explicit keep)
# -----------------------------------------------------------------------------
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# -----------------------------------------------------------------------------
# Gson + Retrofit/Gson converter (OSRM response deserialization)
# Gson uses reflection to map JSON fields to data class properties.
# Without these rules R8 strips field names and deserialization silently returns nulls.
# -----------------------------------------------------------------------------
-keep class com.google.gson.** { *; }
-keep interface com.google.gson.** { *; }
-dontwarn com.google.gson.**
# Keep OSRM response models (Gson-mapped via retrofit-converter-gson)
-keep class com.locationjoystick.core.routing.OsrmRouteResponse { *; }
-keep class com.locationjoystick.core.routing.OsrmRoute { *; }
-keep class com.locationjoystick.core.routing.OsrmGeometry { *; }
-keep class com.locationjoystick.core.routing.OsrmCoordinate { *; }
# Generic Gson model safety: keep any class whose fields are accessed by Gson
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# -----------------------------------------------------------------------------
# Suppress noisy warnings from transitive dependencies
# -----------------------------------------------------------------------------
-dontwarn java.lang.invoke.**
-dontwarn sun.misc.Unsafe
-dontwarn sun.nio.ch.**
