rootProject.name = "ProQueue"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        maven { url = uri("https://maven.myket.ir") }
        maven { url = uri("https://mirror-maven.runflare.com/maven2") }
        maven { url = uri("https://mirror-maven.runflare.com/gradle-plugins/") }
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        // Myket's in-app billing client is only published on JitPack; the
        // filter keeps every other dependency off it.
        maven {
            url = uri("https://jitpack.io")
            content { includeGroup("com.github.myketstore") }
        }
        maven { url = uri("https://maven.myket.ir") }
        maven { url = uri("https://mirror-maven.runflare.com/maven2") }
        maven { url = uri("https://mirror-maven.runflare.com/gradle-plugins/") }
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

include(":composeApp")
