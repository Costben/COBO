pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
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
        // COUI 17：优先本机 ../coui/build/repository/local，
        // 外部环境或指定 -Pcoui.useRemote=true / -Pcobo.useRemoteCoui=true 时回退到 Costben/coui17 的 maven-repo 孤儿分支。
        val localCouiRepo = rootDir.parentFile.resolve("coui/build/repository/local")
        val forceRemoteCoui = providers.gradleProperty("cobo.useRemoteCoui").orNull.equals("true", ignoreCase = true) ||
            providers.gradleProperty("coui.useRemote").orNull.equals("true", ignoreCase = true)
        exclusiveContent {
            forRepository {
                maven {
                    if (localCouiRepo.isDirectory && !forceRemoteCoui) {
                        name = "localCouiRepo"
                        url = uri(localCouiRepo)
                    } else {
                        name = "coui17MavenRepo"
                        url = uri("https://raw.githubusercontent.com/Costben/coui17/maven-repo/coui-repo")
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
include(":shared")
