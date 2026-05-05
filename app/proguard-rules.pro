# Mias ProGuard / R8 Rules for Release

# ─── Hilt / Dagger ───
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }
-keepclassmembers class * { @dagger.hilt.* <fields>; }
-keepclassmembers class * { @javax.inject.* <fields>; }

# ─── Room ───
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-dontwarn androidx.room.paging.**

# ─── Kotlinx Serialization ───
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
}
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class dev.mias.**$$serializer { *; }
-keepclassmembers class dev.mias.** {
    *** Companion;
}

# ─── Ktor ───
-dontwarn io.ktor.**
-keep class io.ktor.** { *; }
-keepclassmembers class io.ktor.** { volatile <fields>; }

# ─── ONNX Runtime ───
-keep class ai.onnxruntime.** { *; }
-keepclassmembers class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

# ─── MediaPipe ───
-keep class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**

# ─── Coroutines ───
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }

# ─── Keep our data models ───
-keep class dev.mias.core.common.model.** { *; }
-keep class dev.mias.core.data.db.entity.** { *; }
-keep class dev.mias.core.modelhub.model.** { *; }
-keep class dev.mias.core.modelhub.db.** { *; }
-keep class dev.mias.core.network.mcp.** { *; }
-keep class dev.mias.core.agent.model.** { *; }
-keep class dev.mias.core.evolution.model.** { *; }
-keep class dev.mias.core.soul.model.** { *; }
-keep class dev.mias.core.thermal.** { *; }

# ─── JNI ───
-keepclasseswithmembers class * { native <methods>; }

# ─── Compose ───
-dontwarn androidx.compose.**