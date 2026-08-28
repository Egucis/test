// Version set notes (see SPEC_COMPLIANCE.md D5):
//
// Google Play Billing 9.1.0 is compiled with Kotlin 2.2.10 and brings kotlin-stdlib 2.2.10 with
// it, so the project's Kotlin must be at least that — a 2.0.x compiler cannot read 2.2 metadata
// and fails in kspDebugKotlin. Everything else here follows from that one constraint:
//
//   Kotlin 2.2.10        matches the stdlib Play Billing 9.1.0 ships
//   KSP  2.2.10-2.0.2    the KSP2 build for that Kotlin
//   Hilt 2.57.2          its processor targets KSP API 2.0.2 — the same generation as above.
//                        Hilt 2.51.1 targets KSP 1.0.x and cannot work with Kotlin 2.2.
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.2.10" apply false
    id("org.jetbrains.kotlin.jvm") version "2.2.10" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
    id("com.google.devtools.ksp") version "2.2.10-2.0.2" apply false
    id("com.google.dagger.hilt.android") version "2.57.2" apply false
}
