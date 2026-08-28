pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "TripAssistant"

// :core is a pure Kotlin/JVM library — no Android dependencies at all. Everything that decides
// whether a trip is worth taking (parsing, validation, economics, rules, entitlement policy)
// lives there so it can be unit tested on the JVM in milliseconds, and so an Uber layout change
// only ever touches the parser package (spec section 62).
include(":core")

// :app is the Android application — capture, OCR, overlay, storage, billing and UI.
include(":app")
