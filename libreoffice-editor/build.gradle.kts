import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest
import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    `maven-publish`
}

val distVersion = "1.0.0"
val distSha256 = "45bd269bc17bf80143405110447900122e48307420fbacb668ed25da967244e1"
val distRepo = "nexaxvn/pdf-reader-native"
val distTag = "libreoffice-dist-v$distVersion"
val distAssetName = "libreoffice-dist-$distVersion.tar.gz"
val distDir = file("src/main/assets/dist")
val distMarker = file("src/main/assets/dist/.dist-version")

android {
    namespace = "org.libreoffice.androidlib"
    compileSdk = 36

    defaultConfig {
        minSdk = 29
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    lint {
        targetSdk = 36
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    api("global.nexax:pdf-native-libs:1.0.0")
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun prop(key: String): String? =
    (localProps.getProperty(key)
        ?: project.findProperty(key) as String?
        ?: System.getenv(key))?.takeIf { it.isNotBlank() }

fun sha256(file: File): String {
    val md = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buf = ByteArray(8192)
        while (true) {
            val n = input.read(buf)
            if (n <= 0) break
            md.update(buf, 0, n)
        }
    }
    return md.digest().joinToString("") { "%02x".format(it) }
}

val fetchDist by tasks.registering {
    description = "Download LibreOffice web bundle from GitHub Releases if missing."
    group = "build setup"

    inputs.property("distVersion", distVersion)
    outputs.file(distMarker)

    doLast {
        if (distMarker.isFile && distMarker.readText().trim() == distVersion) {
            return@doLast
        }

        val token = prop("gpr.token") ?: prop("GITHUB_TOKEN")
            ?: error("Missing gpr.token / GITHUB_TOKEN to download $distAssetName from $distRepo")

        val cache = layout.buildDirectory.file("dist-cache/$distAssetName").get().asFile
        cache.parentFile.mkdirs()

        if (!cache.isFile || sha256(cache) != distSha256) {
            logger.lifecycle("Fetching $distAssetName from $distRepo@$distTag")
            val relApi = URI(
                "https://api.github.com/repos/$distRepo/releases/tags/$distTag"
            ).toURL()
            val relConn = relApi.openConnection() as HttpURLConnection
            relConn.setRequestProperty("Authorization", "Bearer $token")
            relConn.setRequestProperty("Accept", "application/vnd.github+json")
            val relJson = relConn.inputStream.bufferedReader().use { it.readText() }

            val assetIdRegex = Regex(
                "\\{[^{}]*\"id\"\\s*:\\s*(\\d+)[^{}]*\"name\"\\s*:\\s*\"${Regex.escape(distAssetName)}\""
            )
            val assetId = assetIdRegex.find(relJson)?.groupValues?.get(1)
                ?: error("Asset $distAssetName not found in release $distTag")

            val dlConn = URI(
                "https://api.github.com/repos/$distRepo/releases/assets/$assetId"
            ).toURL().openConnection() as HttpURLConnection
            dlConn.setRequestProperty("Authorization", "Bearer $token")
            dlConn.setRequestProperty("Accept", "application/octet-stream")
            dlConn.instanceFollowRedirects = false

            val finalConn = if (dlConn.responseCode in 300..399) {
                val location = dlConn.getHeaderField("Location")
                    ?: error("Redirect without Location for asset $assetId")
                dlConn.disconnect()
                (URI(location).toURL().openConnection() as HttpURLConnection).also {
                    it.instanceFollowRedirects = true
                }
            } else {
                dlConn
            }

            cache.outputStream().use { out -> finalConn.inputStream.use { it.copyTo(out) } }
            finalConn.disconnect()

            val actual = sha256(cache)
            check(actual == distSha256) {
                "Checksum mismatch for $distAssetName: expected $distSha256 got $actual"
            }
        }

        if (distDir.isDirectory) {
            distDir.listFiles()?.forEach { it.deleteRecursively() }
        }
        distDir.mkdirs()

        copy {
            from(tarTree(resources.gzip(cache)))
            into(distDir.parentFile)
        }
        distMarker.writeText(distVersion)
    }
}

tasks.named("preBuild").configure { dependsOn(fetchDist) }

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = "global.nexax"
            artifactId = "libreoffice-editor"
            version = "1.0.0"

            afterEvaluate {
                from(components["release"])
            }

            pom {
                name.set("PDF Reader LibreOffice Editor")
                description.set("LibreOffice-based document editor module for PDF Reader.")
                url.set("https://github.com/nexaxvn/pdf-reader-native")
            }
        }
    }

    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/nexaxvn/pdf-reader-native")
            credentials {
                username = prop("gpr.user") ?: prop("GITHUB_ACTOR") ?: ""
                password = prop("gpr.token") ?: prop("GITHUB_TOKEN") ?: ""
            }
        }
    }
}

