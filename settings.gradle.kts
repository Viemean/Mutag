val localProps = file("local.properties")
if (!localProps.exists()) {
    val candidateSdk = java.io.File(System.getProperty("user.home"), "Android/Sdk")
    if (candidateSdk.exists()) {
        localProps.writeText("sdk.dir=${candidateSdk.absolutePath}\n")
    }
}

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
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
    }
}

rootProject.name = "MuTag"
include(":core-model")
include(":core-provider")
include(":core-tagger")
include(":app")
