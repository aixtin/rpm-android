plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    // AGP 9 compiles Kotlin itself and must not have this plugin applied. Declaring it here only puts
    // this Kotlin version on the build classpath, so AGP compiles with the same Kotlin the Compose
    // compiler plugin below is built for.
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

// A machine can move every build directory off the repo's drive by setting rpm.buildRoot in its own
// GRADLE_USER_HOME/gradle.properties. Unset, each project builds into its own build/ folder.
providers.gradleProperty("rpm.buildRoot").orNull?.let { root ->
    allprojects {
        layout.buildDirectory.set(file("$root/${if (this == rootProject) "root" else name}"))
    }
}
