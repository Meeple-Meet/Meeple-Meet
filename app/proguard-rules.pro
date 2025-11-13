# --------------------------
# Kotlin metadata & serialization
# --------------------------
-keepclassmembers class kotlin.Metadata { *; }
-keepclassmembers class com.github.meeplemeet.model.** {
    @kotlinx.serialization.Serializable *;
}
-keepattributes Signature, *Annotation*

# --------------------------
# Google Maps (principaux packages seulement)
# --------------------------
-keep class com.google.android.gms.maps.** { *; }
-keep class com.google.maps.android.** { *; }
-dontwarn com.google.android.gms.maps.**
-dontwarn com.google.maps.android.**

# --------------------------
# Firebase Auth
# --------------------------
-keep class com.google.firebase.auth.** { *; }

# --------------------------
# Firebase Firestore
# --------------------------
-keep class com.google.firebase.firestore.** { *; }
-keep class com.google.firebase.Timestamp { *; }
-dontwarn com.google.firebase.firestore.**

# --------------------------
# Google Sign-In / credentials
# --------------------------
-keep class com.google.android.gms.auth.api.signin.** { *; }
-dontwarn com.google.android.gms.auth.api.signin.**

# --------------------------
# Compose essentials
# --------------------------
-keep class androidx.compose.runtime.** { *; }

# --------------------------
# Kotlin stdlib reflection
# --------------------------
-keepclassmembers class kotlin.jvm.internal.Intrinsics { *; }

# --------------------------
# R classes
# --------------------------
-keepclassmembers class **.R$* { public static <fields>; }
