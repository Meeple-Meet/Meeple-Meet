// Top-level build file where you can add configuration options common to all sub-projects/modules.

buildscript {
    configurations.all {
        resolutionStrategy.eachDependency {
            if (requested.group == "org.apache.commons" && requested.name == "commons-compress") {
                useVersion("1.26.1")
                because("Fixes TarArchiveInputStream.getNextEntry() compatibility issue with Gradle 8.7+")
            }
        }
    }
}

plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.jetbrainsKotlinAndroid) apply false
    alias(libs.plugins.ktfmt) apply false
    alias(libs.plugins.gms) apply false
}

configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.apache.commons" && requested.name == "commons-compress") {
            useVersion("1.26.1")
        }
    }
}
