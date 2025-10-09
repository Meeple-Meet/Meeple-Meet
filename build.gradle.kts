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

        // Lint configuration
        property("sonar.androidLint.reportPaths",
            "app/build/reports/lint-results-debug.xml")

        // Exclude binary and resource files from analysis
        property("sonar.exclusions",
            "**/res/**," +
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
                "**/*.svg")

        // Exclude test files from main analysis
        property("sonar.test.exclusions",
            "**/src/androidTest/**," +
                "**/src/debug/**," +
                "**/src/test/**")

        // Set source encoding
        property("sonar.sourceEncoding", "UTF-8")
    }
}