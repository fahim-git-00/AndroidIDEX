package com.aidex.build

import java.io.File
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

        ZipOutputStream(outputApk.outputStream().buffered()).use { zipOut ->
            val written = mutableSetOf<String>()

            // 1. Copy everything from the skeleton APK.
            ZipInputStream(skeletonApk.inputStream().buffered()).use { zipIn ->
                var entry: ZipEntry? = zipIn.nextEntry
                val buf = ByteArray(64 * 1024)
                while (entry != null) {
                    val name = entry.name
                    if (name !in written && !entry.isDirectory) {
                        zipOut.putNextEntry(ZipEntry(name).apply {
                            method = if (name.endsWith(".png") ||
                                         name.endsWith(".jpg") ||
                                         name.endsWith(".jpeg") ||
                                         name.endsWith(".so")) ZipEntry.STORED else ZipEntry.DEFLATED
                            if (method == ZipEntry.STORED) {
                                val data = zipIn.readBytes()
                                size = data.size.toLong()
                                compressedSize = data.size.toLong()
                                crc = java.util.zip.CRC32().apply { update(data) }.value
                                zipOut.putNextEntry(this)
                                zipOut.write(data)
                                zipOut.closeEntry()
                                entry = zipIn.nextEntry
                                return@while
                            }
                        })
                        zipIn.copyTo(zipOut, buf.size)
                        zipOut.closeEntry()
                        written.add(name)
                    }
                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                }
            }

            // 2. Add classes.dex.
            dexFiles.sortedBy { it.name }.forEach { dex ->
                val name = dex.name
                if (name in written) return@forEach
                BuildLogger.log("  + $name (${dex.length() / 1024} KB)")
                zipOut.putNextEntry(ZipEntry(name).apply { method = ZipEntry.DEFLATED })
                dex.inputStream().use { it.copyTo(zipOut) }
                zipOut.closeEntry()
                written.add(name)
            }
        }

        BuildLogger.log("Packaged: ${outputApk.absolutePath} (${outputApk.length() / 1024} KB)")
    }
}
