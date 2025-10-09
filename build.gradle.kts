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

        property("sonar.exclusions", "**/*.png,**/*.jpg,**/*.jpeg,**/*.gif,**/*.webp,**/*.ttf,**/*.otf,**/*.woff,**/*.woff2,**/*.eot,**/*.svg")
        property("sonar.test.exclusions", "**/androidTest/**,**/debug/**,**/test/**")

        property("sonar.sourceEncoding", "UTF-8")
    }
}