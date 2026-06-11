plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt.android) apply false
}

subprojects {
    configurations.all {
        resolutionStrategy.eachDependency {
            if (requested.name == "kotlin-metadata-jvm" || requested.name == "kotlinx-metadata-jvm") {
                useTarget("org.jetbrains.kotlin:kotlin-metadata-jvm:${libs.versions.kotlinMetadataJvm.get()}")
            }
        }
    }
}
