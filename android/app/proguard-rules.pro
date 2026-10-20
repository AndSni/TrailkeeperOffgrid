# Gson serializes every Room entity (data/local/Entities.kt) and the
# model/ DTOs via reflection - for backup export/import (Backup.kt) and
# device-to-device sharing (ShareBundle.kt). R8 renaming/stripping a
# field here wouldn't crash, it would silently corrupt exported JSON,
# so these are kept whole rather than relying on Gson's generic rules
# to infer what's safe to touch.
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.asnidev.trailkeeperoffgrid.data.local.** { *; }
-keep class com.asnidev.trailkeeperoffgrid.model.** { *; }

-keep class com.google.gson.** { *; }
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-dontwarn sun.misc.**

# MapLibre's AAR ships its own proguard.txt, applied automatically.
# Coil ships no consumer rules at all, but we only use plain
# coil-compose (no extra decoder artifacts), which needs none.
