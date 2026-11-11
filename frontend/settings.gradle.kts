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
        // 本地存在 ../../coui/build/repository/local（相对 frontend/ 根）时优先解析本地滚动快照，
        // 避免使用参数过时的远端快照；无本地 ../coui 仓库时回退到远端钉死仓库以保证外部环境可编译。
        val localCouiRepo = file("../../coui/build/repository/local")
        exclusiveContent {
            forRepository {
                maven {
                    if (localCouiRepo.isDirectory) {
                        name = "localCouiRepo"
                        url = uri(localCouiRepo)
                    } else {
                        name = "pinnedArtPlusCoui"
                        url = uri("https://raw.githubusercontent.com/Costben/ArtPlus/63bcad6dc055680e2cbc58640abcbf0329b9250d/third_party/coui-repo")
                    }
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
