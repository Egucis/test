# Room
-keep class uk.co.cabcomply.app.data.db.entity.** { *; }

# Hilt / Dagger generated code is kept automatically by the Hilt Gradle plugin.

# Keep data classes used for JSON backup serialization
-keep class uk.co.cabcomply.app.data.backup.** { *; }
-keepclassmembers class uk.co.cabcomply.app.data.backup.** { *; }
