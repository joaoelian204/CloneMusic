# Retrofit / OkHttp
-dontwarn okio.**
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepattributes Signature
-keepattributes Exceptions
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keep class kotlin.coroutines.Continuation { *; }

# Gson
-keep class sun.misc.Unsafe { *; }
-keep class com.google.gson.stream.** { *; }
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken { *; }

# Models & DTOs & API
-keep class com.phantombeats.data.remote.api.** { *; }
-keep class com.phantombeats.data.remote.dto.** { *; }
-keep class com.phantombeats.domain.model.** { *; }
-keep enum com.phantombeats.domain.model.** { *; }
-keep class com.phantombeats.data.local.entity.** { *; }

# Player
-keep class androidx.media3.** { *; }
