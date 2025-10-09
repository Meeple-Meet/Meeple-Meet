// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.jetbrainsKotlinAndroid) apply false
    alias(libs.plugins.ktfmt) apply false
    alias(libs.plugins.gms) apply false
    id("org.sonarqube") version "5.1.0.4882"
}

sonar {
    properties {
        property("sonar.projectKey", "Meeple-Meet_Meeple-Meet")
        property("sonar.organization", "meeple-meet")
        property("sonar.host.url", "https://sonarcloud.io")

        // Coverage configuration
        property("sonar.coverage.jacoco.xmlReportPaths",
            "app/build/reports/jacoco/jacocoTestReport/jacocoTestReport.xml")

        // Lint configuration - use pattern to match any lint report
        property("sonar.androidLint.reportPaths",
            "app/build/reports/lint-results-debug.xml,app/build/reports/lint-results.xml")

        // Exclude only binary files from analysis (not res/ to allow lint analysis)
        property("sonar.exclusions",
            "**/*.png," +
                "**/*.jpg," +
                "**/*.jpeg," +
                "**/*.gif," +
                "**/*.webp," +
                "**/*.ttf," +
                "**/*.otf," +
                "**/*.woff," +
                "**/*.woff2," +
                "**/*.eot," +
                "**/*.svg," +
                "**/res/drawable/**," +
                "**/res/mipmap*/**," +
                "**/res/font/**")

        // Exclude test files from main analysis
        property("sonar.test.exclusions",
            "**/src/androidTest/**," +
                "**/src/debug/**," +
                "**/src/test/**")

        // Set source encoding
        property("sonar.sourceEncoding", "UTF-8")
    }
}