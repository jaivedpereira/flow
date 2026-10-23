# Flow — keep Compose and reflection-free classes as-is; R8 handles the rest.
-dontwarn org.jetbrains.annotations.**
-keep class androidx.compose.** { *; }
