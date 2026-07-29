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

# R8 full mode erases the generic signature of the Continuation parameter that suspend
# functions compile down to. Retrofit's suspend support (HttpServiceMethod) casts that
# parameter's generic type straight to ParameterizedType with no instanceof check, so on
# every suspend ApiService call (login included) this blew up with:
# "java.lang.Class cannot be cast to java.lang.reflect.ParameterizedType"
# https://github.com/square/retrofit/issues/3751
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# Keep Firebase Messaging
-keep class com.google.firebase.** { *; }
