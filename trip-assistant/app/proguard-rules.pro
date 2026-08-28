# Room and Hilt generate code that is referenced reflectively.
-keep class uk.co.tripassistant.app.data.db.** { *; }

# ML Kit text recognition ships its own consumer rules; this keeps the bundled model classes.
-keep class com.google.mlkit.** { *; }

# Never let a crash report carry recognised screen text or a purchase token (spec section 52).
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
