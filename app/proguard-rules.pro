# Keep Retrofit models & API interfaces
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class com.smarthome.data.** { *; }

# Keep OkHttp & Retrofit annotations
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*

# Gson resolves generic types (e.g. HydraCollection<T>) at runtime via TypeToken. Without this,
# R8 can strip the generic superclass info, causing:
# "java.lang.Class cannot be cast to java.lang.reflect.ParameterizedType" in production.
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken

# Keep Firebase Messaging
-keep class com.google.firebase.** { *; }
