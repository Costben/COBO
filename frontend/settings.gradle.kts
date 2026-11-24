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
                // zxing 发布在 Maven Central，不在 Google Maven；不排除的话会被这个仓库拦下来。
                excludeGroup("com.google.zxing")
            }
        }
        mavenCentral()
        // 本地存在 ../../coui/build/repository/local（相对 frontend/ 根）且未指定强制远端时优先解析本地快照；
        // 外部环境无本地 ../coui 仓库时回退到本仓库 third-party 孤儿分支的钉死快照以保证开箱即编。
        val localCouiRepo = file("../../coui/build/repository/local")
        val forceRemoteCoui = providers.gradleProperty("cobo.useRemoteCoui").orNull.equals("true", ignoreCase = true)
        exclusiveContent {
            forRepository {
                maven {
                    if (localCouiRepo.isDirectory && !forceRemoteCoui) {
                        name = "localCouiRepo"
                        url = uri(localCouiRepo)
                    } else {
                        name = "pinnedCoboCoui"
                        url = uri("https://raw.githubusercontent.com/Costben/COBO/512831ef8f848b7a9a96bebb5eada27a80212436/coui-repo")
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
