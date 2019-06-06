package io.arusland.util

import org.apache.commons.io.IOUtils
import java.io.File
import java.io.FileOutputStream
import java.net.URL

fun loadBinaryFile(playbackUrl: URL, fileId: String, ext: String): File {
    val outputFile = File(tempDir, "$fileId.$ext")

    FileOutputStream(outputFile).use { os ->
        os.write(IOUtils.toByteArray(playbackUrl))
    }

    return outputFile
}

fun File.isVideo(): Boolean {
    return SUPPORTED_EXTS.find { path.endsWith(it.first) }?.second == "video"
}

fun File.isImage(): Boolean {
    return SUPPORTED_EXTS.find { path.endsWith(it.first) }?.second == "image"
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
