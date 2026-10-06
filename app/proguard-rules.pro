# kotlinx.serialization: keep generated serializers for @Serializable classes.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.chorereminder.**$$serializer { *; }
-keepclassmembers class com.chorereminder.** {
    *** Companion;
}
-keepclasseswithmembers class com.chorereminder.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Room entities are reflected over by the generated DAO implementations.
-keep class com.chorereminder.data.** { *; }

# WorkManager instantiates workers reflectively.
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
