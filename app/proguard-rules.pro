# Aturan R8 untuk Uhuyy Player.
# Compose, Media3 dan AndroidX sudah bawa aturan sendiri (consumer rules).
# Baris di bawah cuma pengaman tambahan.

-keep class com.uhuyy.player.** { *; }
-dontwarn org.checkerframework.**
-dontwarn javax.annotation.**
-dontwarn org.jetbrains.annotations.**

# Hapus log debug di release
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
}
