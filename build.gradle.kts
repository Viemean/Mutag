plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.ktlint) apply false
}

allprojects {
    configurations.all {
        resolutionStrategy.force("androidx.profileinstaller:profileinstaller:1.4.1")
        resolutionStrategy.force("androidx.concurrent:concurrent-futures:1.2.0")
    }
}

subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        version.set("1.8.0")
        android.set(true)
        ignoreFailures.set(true)
        filter {
            exclude { it.file.path.contains("/build/") }
        }
        reporters {
            reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.PLAIN)
        }
    }

    tasks.matching {
        it.name.contains("Lint", ignoreCase = true) && !it.name.contains("ktlint", ignoreCase = true)
    }.configureEach {
        enabled = false
    }
}
