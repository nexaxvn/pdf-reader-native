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
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()

        maven {
            name = "GitHubPackagesPdfNative"
            url = uri("https://maven.pkg.github.com/nexaxvn/pdf-reader-native")
            credentials {
                val localProps = java.util.Properties().apply {
                    val f = File(rootDir, "local.properties")
                    if (f.exists()) f.inputStream().use { load(it) }
                }
                username = localProps.getProperty("gpr.user")
                    ?: providers.gradleProperty("gpr.user").orNull
                    ?: System.getenv("GITHUB_ACTOR")
                    ?: ""
                password = localProps.getProperty("gpr.token")
                    ?: providers.gradleProperty("gpr.token").orNull
                    ?: System.getenv("GITHUB_TOKEN")
                    ?: ""
            }
            content {
                includeGroup("global.nexax")
            }
        }
    }
}

rootProject.name = "pdf-reader-native"
include(":libreoffice-editor")
