pluginManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/")
        maven("https://maven.kikugie.dev/snapshots") { name = "KikuGie Snapshots" }
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.8.3"
}

stonecutter {
    create(rootProject) {
        versions("1.21.1", "1.21.4", "1.21.5", "1.21.8", "1.21.10")
        vcsVersion = "1.21.1"
    }
}

rootProject.name = "moar"
