# Retrofit / OkHttp
-dontwarn okio.**
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-keep class okhttp3.** { *; }
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
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keep class com.google.gson.stream.** { *; }
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken { *; }

# Mozilla Rhino / DukTape (YouTube Extractor Cipher Resolution)
-keep class org.mozilla.javascript.** { *; }
-dontwarn org.mozilla.javascript.**
-keep class com.squareup.duktape.** { *; }
-dontwarn com.squareup.duktape.**

# WebView / Javascript Interface (Used by some extractors)
-keepattributes JavascriptInterface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Models & DTOs & API
-keep class com.phantombeats.data.remote.api.** { *; }
-keep class com.phantombeats.data.remote.dto.** { *; }
-keep class com.phantombeats.domain.model.** { *; }
-keep enum com.phantombeats.domain.model.** { *; }
-keep class com.phantombeats.data.local.entity.** { *; }

# YouTube Extractor (MaxRave)
-keep class com.maxrave.** { *; }
-dontwarn com.maxrave.**

# Player
-keep class androidx.media3.** { *; }

# JSoup / Re2j optional attributes (Used by NewPipeExtractor)
-dontwarn com.google.re2j.**
-dontwarn org.jsoup.**
-dontwarn javax.script.**
  
# Hilt / Dagger  
-keep class dagger.hilt.** { *; }  
-dontwarn dagger.hilt.**  
-keep class **.Hilt_* { *; }  
-dontwarn **.Hilt_* 
