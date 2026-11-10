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
        exclusiveContent {
            forRepository {
                maven {
                    name = "pinnedArtPlusCoui"
                    url = uri("https://raw.githubusercontent.com/Costben/ArtPlus/63bcad6dc055680e2cbc58640abcbf0329b9250d/third_party/coui-repo")
                }
            }
            filter {
                includeGroup("io.github.suqi8.coui.kmp")
            }
        }
    }
}

rootProject.name = "Cobo"
include(":app")
