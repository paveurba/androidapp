# Keep Retrofit models & API interfaces
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class com.smarthome.data.** { *; }
-keep class com.smarthome.data.network.** { *; }

# Keep OkHttp & Retrofit annotations
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*

# Keep Firebase Messaging
-keep class com.google.firebase.** { *; }
