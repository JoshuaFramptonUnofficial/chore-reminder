// Top-level build file. Plugins are declared here (apply false) and applied in :app.
// NOTE: do NOT add org.jetbrains.kotlin.android -- AGP 9 has Kotlin built in and
// rejects that plugin outright.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
}
