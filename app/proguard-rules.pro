# =============================================================================
# locationjoystick — ProGuard / R8 rules
# =============================================================================

# -----------------------------------------------------------------------------
# Hilt / Dagger (ships its own consumer rules; only the entry points below
# aren't covered by those — reflection-free injection needs no blanket keep)
# -----------------------------------------------------------------------------
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
-keepclassmembers @kotlinx.serialization.Serializable class * {
    static ** $serializer;
    static ** Companion;
    ** serializer();
    ** serializer(kotlinx.serialization.modules.SerializersModule);
}

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
# MapLibre (JNI + reflection-accessed classes only — the SDK is too large
# to keep in full without gutting shrink/obfuscation)
# -----------------------------------------------------------------------------
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
# OkHttp (ships its own consumer rules)
# -----------------------------------------------------------------------------
-dontwarn okhttp3.**
-dontwarn okio.**
# OkHttp internal platform detection
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# -----------------------------------------------------------------------------
# Retrofit (ships its own consumer rules)
# -----------------------------------------------------------------------------
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
# ZXing (QR code scanning and generation) — keep only the specific classes
# reflection/native code touches, not the whole library
# -----------------------------------------------------------------------------
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
# BarcodeView/CaptureManager are custom Views, already covered by the
# "extends android.view.View" rule above.
-dontwarn com.journeyapps.barcodescanner.**

# -----------------------------------------------------------------------------
# CameraX (used by QR scanner; ships its own consumer rules)
# -----------------------------------------------------------------------------
-dontwarn androidx.camera.**

# -----------------------------------------------------------------------------
# Kotlin coroutines (ships its own consumer rules)
# -----------------------------------------------------------------------------
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

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
# DataStore (ships its own consumer rules)
# -----------------------------------------------------------------------------
-dontwarn androidx.datastore.**

# -----------------------------------------------------------------------------
# Compose (ships its own consumer rules; runtime kept by AGP)
# -----------------------------------------------------------------------------
-dontwarn androidx.compose.**

# -----------------------------------------------------------------------------
# Gson + Retrofit/Gson converter (OSRM response deserialization)
# Gson uses reflection to map JSON fields to data class properties.
# Without these rules R8 strips field names and deserialization silently returns nulls.
# -----------------------------------------------------------------------------
-dontwarn com.google.gson.**
# Keep OSRM Retrofit interface and response models (Gson-mapped via retrofit-converter-gson)
-keep interface com.locationjoystick.core.routing.OsrmApi { *; }
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
