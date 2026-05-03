# Kid ProGuard / R8 Rules for Release

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
-keep,includedescriptorclasses class dev.kid.**$$serializer { *; }
-keepclassmembers class dev.kid.** {
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
-keep class dev.kid.core.common.model.** { *; }
-keep class dev.kid.core.data.db.entity.** { *; }
-keep class dev.kid.core.modelhub.model.** { *; }
-keep class dev.kid.core.modelhub.db.** { *; }
-keep class dev.kid.core.network.mcp.** { *; }
-keep class dev.kid.core.agent.model.** { *; }
-keep class dev.kid.core.evolution.model.** { *; }
-keep class dev.kid.core.soul.model.** { *; }
-keep class dev.kid.core.thermal.** { *; }

# ─── JNI ───
-keepclasseswithmembers class * { native <methods>; }

# ─── Compose ───
-dontwarn androidx.compose.**