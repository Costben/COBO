pluginManagement {
    repositories {
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
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        // COUI is consumed from the local fork as the rolling 1.2.0-SNAPSHOT, which its git hooks
        // republish on every commit; run ../coui/.githooks/publish-rolling-snapshot.sh to publish by hand.
        maven { url = uri("../../coui/build/repository/local") }
    }
}

rootProject.name = "Cobo"
include(":app")
