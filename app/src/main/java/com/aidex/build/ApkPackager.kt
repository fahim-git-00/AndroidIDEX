package com.aidex.build

import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Merges the AAPT2-linked skeleton APK with the compiled classes.dex,
 * producing a single unsigned APK ready for signing.
 */
object ApkPackager {

    fun packageApk(
        skeletonApk: File,
        dexDir: File,
        outputApk: File
    ) {
        require(skeletonApk.exists()) { "Skeleton APK missing: ${skeletonApk.absolutePath}" }

        val dexFiles = dexDir.listFiles { f -> f.extension == "dex" }?.toList().orEmpty()
        require(dexFiles.isNotEmpty()) { "No .dex files found in ${dexDir.absolutePath}" }

        BuildLogger.log("Packaging: skeleton + ${dexFiles.size} dex file(s)")

        if (outputApk.exists()) outputApk.delete()
        outputApk.parentFile?.mkdirs()

        val written = mutableSetOf<String>()
        val buffer = ByteArray(64 * 1024)

        ZipOutputStream(outputApk.outputStream().buffered()).use { zipOut ->
            // 1. Copy every entry from the skeleton APK.
            ZipInputStream(skeletonApk.inputStream().buffered()).use { zipIn ->
                var entry: ZipEntry? = zipIn.nextEntry
                while (entry != null) {
                    val name = entry.name
                    if (!entry.isDirectory && name !in written) {
                        val shouldStore = name.endsWith(".png", true) ||
                                name.endsWith(".jpg", true) ||
                                name.endsWith(".jpeg", true) ||
                                name.endsWith(".so", true)
                        if (shouldStore) {
                            val data = zipIn.readBytes()
                            val crc = CRC32().apply { update(data) }
                            val out = ZipEntry(name).apply {
                                method = ZipEntry.STORED
                                size = data.size.toLong()
                                compressedSize = data.size.toLong()
                                this.crc = crc.value
                            }
                            zipOut.putNextEntry(out)
                            zipOut.write(data)
                            zipOut.closeEntry()
                        } else {
                            zipOut.putNextEntry(ZipEntry(name).apply {
                                method = ZipEntry.DEFLATED
                            })
                            var n: Int
                            while (zipIn.read(buffer).also { n = it } > 0) {
                                zipOut.write(buffer, 0, n)
                            }
                            zipOut.closeEntry()
                        }
                        written.add(name)
                    }
                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                }
            }

            // 2. Add classes.dex (and classes2.dex, etc.).
            dexFiles.sortedBy { it.name }.forEach { dex ->
                val name = dex.name
                if (name in written) return@forEach
                BuildLogger.log("  + $name (${dex.length() / 1024} KB)")
                zipOut.putNextEntry(ZipEntry(name).apply { method = ZipEntry.DEFLATED })
                dex.inputStream().use { input ->
                    var n: Int
                    while (input.read(buffer).also { n = it } > 0) {
                        zipOut.write(buffer, 0, n)
                    }
                }
                zipOut.closeEntry()
                written.add(name)
            }
        }

        BuildLogger.log("Packaged: ${outputApk.absolutePath} (${outputApk.length() / 1024} KB)")
    }
}
