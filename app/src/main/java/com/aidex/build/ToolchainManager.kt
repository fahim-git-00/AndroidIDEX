package com.aidex.build

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object ToolchainManager {

    data class Artifact(
        val name: String,
        val url: String,
        val fileName: String,
        val approxBytes: Long
    )

    private val ARTIFACTS = listOf(
        Artifact(
            "Kotlin compiler 1.9.24",
            "https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-compiler-embeddable/1.9.24/kotlin-compiler-embeddable-1.9.24.jar",
            "kotlin-compiler-embeddable.jar", 58_000_000L
        ),
        Artifact(
            "Kotlin stdlib 1.9.24",
            "https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-stdlib/1.9.24/kotlin-stdlib-1.9.24.jar",
            "kotlin-stdlib.jar", 1_700_000L
        ),
        Artifact(
            "Kotlin reflect 1.9.24",
            "https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-reflect/1.9.24/kotlin-reflect-1.9.24.jar",
            "kotlin-reflect.jar", 3_200_000L
        ),
        Artifact(
            "Kotlin script runtime 1.9.24",
            "https://repo.maven.apache.org/maven2/org/jetbrains/kotlin/kotlin-script-runtime/1.9.24/kotlin-script-runtime-1.9.24.jar",
            "kotlin-script-runtime.jar", 43_000L
        ),
        Artifact(
            "Kotlin daemon embeddable 1.9.24",
            "https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-daemon-embeddable/1.9.24/kotlin-daemon-embeddable-1.9.24.jar",
            "kotlin-daemon-embeddable.jar", 400_000L
        ),
        Artifact(
            "Trove4j",
            "https://repo1.maven.org/maven2/org/jetbrains/intellij/deps/trove4j/1.0.20200330/trove4j-1.0.20200330.jar",
            "trove4j.jar", 600_000L
        ),
        Artifact(
            "Annotations 13.0",
            "https://repo1.maven.org/maven2/org/jetbrains/annotations/13.0/annotations-13.0.jar",
            "annotations.jar", 17_500L
        ),
        Artifact(
            "ECJ 3.39.0",
            "https://repo1.maven.org/maven2/org/eclipse/jdt/ecj/3.39.0/ecj-3.39.0.jar",
            "ecj.jar", 3_300_000L
        ),
        Artifact(
            "R8 8.7.18",
            "https://maven.google.com/com/android/tools/r8/8.7.18/r8-8.7.18.jar",
            "r8.jar", 17_000_000L
        ),
        Artifact(
            "apksig 8.7.2",
            "https://maven.google.com/com/android/tools/build/apksig/8.7.2/apksig-8.7.2.jar",
            "apksig.jar", 500_000L
        ),
        Artifact(
            "Android platform API 34",
            "https://github.com/Sable/android-platforms/raw/master/android-34/android.jar",
            "android.jar", 26_000_000L
        )
    )

    fun toolchainDir(context: Context): File {
        val dir = File(context.filesDir, "toolchain")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    // Size floors (bytes) — anything below these is a corrupt/partial download.
    private val MIN_SIZES = mapOf(
        "kotlin-compiler-embeddable.jar" to 40_000_000L,
        "kotlin-stdlib.jar" to 1_000_000L,
        "kotlin-reflect.jar" to 2_000_000L,
        "kotlin-script-runtime.jar" to 30_000L,
        "kotlin-daemon-embeddable.jar" to 200_000L,
        "trove4j.jar" to 400_000L,
        "annotations.jar" to 10_000L,
        "ecj.jar" to 2_500_000L,
        "r8.jar" to 8_000_000L,
        "apksig.jar" to 200_000L,
        "android.jar" to 20_000_000L
    )

    fun isReady(context: Context): Boolean {
        return ARTIFACTS.all { a ->
            val f = File(toolchainDir(context), a.fileName)
            val min = MIN_SIZES[a.fileName] ?: 100_000L
            f.exists() && f.length() >= min
        }
    }

    fun missing(context: Context): List<Artifact> = ARTIFACTS.filter { a ->
        val f = File(toolchainDir(context), a.fileName)
        val min = MIN_SIZES[a.fileName] ?: 100_000L
        !f.exists() || f.length() < min
    }

    fun pathOf(context: Context, fileName: String): File =
        File(toolchainDir(context), fileName)

    suspend fun download(
        context: Context,
        onProgress: (downloadedBytes: Long, totalBytes: Long, currentName: String) -> Unit
    ) = withContext(Dispatchers.IO) {
        val dir = toolchainDir(context)
        val missing = missing(context)
        val total = missing.sumOf { it.approxBytes }
        var completed = 0L

        for (artifact in missing) {
            onProgress(completed, total, artifact.name)
            val target = File(dir, artifact.fileName)
            val temp = File(dir, "${artifact.fileName}.part")
            if (temp.exists()) temp.delete()

            downloadFile(artifact.url, temp) { bytesRead ->
                onProgress(completed + bytesRead, total, artifact.name)
            }

            // Validate size after download.
            val min = MIN_SIZES[artifact.fileName] ?: 100_000L
            if (temp.length() < min) {
                temp.delete()
                throw RuntimeException(
                    "Downloaded ${artifact.fileName} is ${temp.length()} B, " +
                    "expected >= $min B. URL may be stale."
                )
            }

            if (target.exists()) target.delete()
            temp.renameTo(target)
            completed += artifact.approxBytes
        }
        onProgress(total, total, "Ready")
    }

    private fun downloadFile(urlStr: String, target: File, onBytes: (Long) -> Unit) {
        val url = URL(urlStr)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "AIDEX/1.0")
        }
        conn.connect()
        val status = conn.responseCode
        if (status !in 200..299) {
            throw RuntimeException("HTTP $status downloading $urlStr")
        }
        conn.inputStream.use { input ->
            target.outputStream().use { output ->
                val buf = ByteArray(64 * 1024)
                var total = 0L
                var lastReport = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    output.write(buf, 0, n)
                    total += n
                    if (total - lastReport > 512 * 1024) {
                        onBytes(total)
                        lastReport = total
                    }
                }
                onBytes(total)
            }
        }
        conn.disconnect()
    }

    fun deleteAll(context: Context) {
        toolchainDir(context).listFiles()?.forEach { it.delete() }
    }
}
