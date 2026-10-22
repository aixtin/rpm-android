# R8 rules for the phone app. The libraries bring their own (Media3, OkHttp, Coil, kotlinx.serialization,
# ML Kit); these cover what reflection in rpm's own code reaches.

# kotlinx.serialization: the generated serializers of rpm's wire classes are looked up by name.
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-keepclassmembers @kotlinx.serialization.Serializable class com.rpm.player.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class com.rpm.player.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.rpm.player.**$$serializer { *; }

# Stack traces in crash reports keep their line numbers.
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile

# ML Kit (artists' faces) finds its parts through registrars named in its manifest and made by reflection.
# Without these, FaceDetection.getClient() fails with a NullPointerException in the shrunk build.
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_face_bundled.** { *; }
-keep class * implements com.google.firebase.components.ComponentRegistrar { <init>(); }
-keep class com.google.firebase.components.** { *; }
