package io.arusland.util

import org.apache.commons.io.IOUtils
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.concurrent.TimeUnit

fun loadBinaryFile(playbackUrl: URL, fileId: String, ext: String): File {
    val outputFile = File(tempDir, "$fileId.$ext")

    playbackUrl.openStream().use { input ->
        FileOutputStream(outputFile).use { output ->
            IOUtils.copy(input, output)
        }
    }

    return outputFile
}

fun File.isVideo(): Boolean {
    return SUPPORTED_EXTS.find { path.endsWith(it.first) }?.second == "video"
}

fun File.isImage(): Boolean {
    return SUPPORTED_EXTS.find { path.endsWith(it.first) }?.second == "image"
}

/**
 * Check if file is mp4. Not only by its extension.
 */
fun File.isMp4(): Boolean {
    if (this.exists()) {
        if (this.extension == "mp4") {
            return true
        }

        try {
            val process = Runtime.getRuntime().exec("file " + this.absolutePath)
            if (process.waitFor(60, TimeUnit.SECONDS)) {
                return process.inputStream.use {
                    val stdout = it.bufferedReader().readText()
                    stdout.contains(" MP4 ")
                }
            }
        } catch (ex: Exception) {
            return false
        }
    }

    return false
}

fun isFileSupported(path: String): Boolean {
    return SUPPORTED_EXTS.any { path.endsWith(it.first) }
}

private val SUPPORTED_EXTS = listOf(".mp4" to "video",
        ".webm" to "video",
        ".jpg" to "image",
        ".jpeg" to "image",
        ".png" to "image",
        ".bmp" to "image"
)
private val tempDir = File("/tmp")
