# Media3 and Compose ship their own consumer rules. Nothing reflective in this app.
-dontwarn org.checkerframework.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn com.google.j2objc.annotations.**

# Enum names are saved to prefs / smart.json and read back with valueOf(): keep them stable across updates.
-keepclassmembernames enum bot.paradox.music.data.** { <fields>; }
