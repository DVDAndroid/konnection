buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath(BuildPlugins.kotlinGradlePlugin)
        classpath(BuildPlugins.androidGradlePlugin)
    }
}

allprojects {
    // the only place where HostManager could be instantiated
    project.extra.apply {
        set("hostManager", hostManager)
    }

    repositories {
        mavenCentral()
    }
}

plugins {
    id("com.louiscad.complete-kotlin") version "1.1.0"
}