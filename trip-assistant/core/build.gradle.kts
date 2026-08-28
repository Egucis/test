import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
}

// Deliberately dependency-free (stdlib only). :core must never gain an Android dependency —
// that is what keeps the decision logic unit testable in milliseconds and keeps an Uber layout
// change confined to the parser package (spec section 62).
dependencies {
    testImplementation(kotlin("test"))
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.test {
    useJUnitPlatform()
    testLogging { events("passed", "failed", "skipped") }
}
