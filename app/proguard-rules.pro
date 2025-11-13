# --------------------------
# Keep Kotlin metadata for reflection and serialization
# --------------------------
-keep class kotlin.Metadata { *; }
-keepattributes Signature, *Annotation*

# --------------------------
# Firebase Firestore
# --------------------------
-keep class com.google.firebase.firestore.** { *; }
-keep class com.google.firebase.Timestamp { *; }
-dontwarn com.google.firebase.firestore.**

# --------------------------
# Firestore / Serializable models
# --------------------------
# Keep all classes in your model package intact, including fields and constructors
-keep class com.github.meeplemeet.model.** { *; }
-keepclassmembers class com.github.meeplemeet.model.** {
    public <init>(...);
}
-keepclassmembernames class com.github.meeplemeet.model.** { <fields>; }

# --------------------------
# Maps
# --------------------------
-keep class com.google.android.gms.** { *; }
-keep class com.google.maps.android.** { *; }
-dontwarn com.google.android.gms.**
-dontwarn com.google.maps.android.**

# --------------------------
# Compose essentials
# --------------------------
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.activity.** { *; }
-keep class androidx.savedstate.** { *; }

# --------------------------
# Kotlin runtime essentials
# --------------------------
-keepclassmembers class kotlin.jvm.internal.Intrinsics { *; }

# --------------------------
# R classes
# --------------------------
-keepclassmembers class **.R$* { public static <fields>; }
