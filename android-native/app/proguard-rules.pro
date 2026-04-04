# Retrofit / OkHttp
-dontwarn okio.**
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepattributes Signature
-keepattributes Exceptions
-keepattributes *Annotation*

# Gson
-keep class sun.misc.Unsafe { *; }
-keep class com.google.gson.stream.** { *; }

# Models & DTOs & API
-keep class com.phantombeats.data.remote.api.** { *; }
-keep class com.phantombeats.data.remote.dto.** { *; }
-keep class com.phantombeats.domain.model.** { *; }
-keep class com.phantombeats.data.local.entity.** { *; }

# Player
-keep class androidx.media3.** { *; }
