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
        // COUI is consumed from the local fork: publish it with
        //   cd ../coui && ./gradlew :coui-ui:publishAndroidPublicationToLocalRepository \
        //       :coui-ui:publishKotlinMultiplatformPublicationToLocalRepository ...
        maven { url = uri("../../coui/build/repository/local") }
    }
}

rootProject.name = "Cobo"
include(":app")
