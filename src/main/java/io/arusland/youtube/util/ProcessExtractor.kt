package io.arusland.youtube.util

import java.io.IOException
import java.io.InputStream
import java.util.regex.Pattern


class StreamProcessExtractor(
    private val buffer: StringBuffer,
    private val stream: InputStream,
    private val callback: DownloadProgressCallback?
) :
    Thread() {
    private val p =
        Pattern.compile("\\[download\\]\\s+(?<percent>\\d+\\.\\d)% .* ETA (?<minutes>\\d+):(?<seconds>\\d+)")

    override fun run() {
        try {
            val currentLine = StringBuilder()
            var nextChar: Int
            while (stream.read().also { nextChar = it } != -1) {
                buffer.append(nextChar.toChar())
                if (nextChar == '\r'.toInt() && callback != null) {
                    processOutputLine(currentLine.toString())
                    currentLine.setLength(0)
                    continue
                }
                currentLine.append(nextChar.toChar())
            }
        } catch (ignored: IOException) {
        }
    }

    private fun processOutputLine(line: String) {
        val m = p.matcher(line)
        if (m.matches()) {
            val progress = m.group(GROUP_PERCENT).toFloat()
            val eta = convertToSeconds(m.group(GROUP_MINUTES), m.group(GROUP_SECONDS)).toLong()
            callback?.onProgressUpdate(progress, eta)
        }
    }

    private fun convertToSeconds(minutes: String, seconds: String): Int {
        return minutes.toInt() * 60 + seconds.toInt()
    }

    companion object {
        private const val GROUP_PERCENT = "percent"
        private const val GROUP_MINUTES = "minutes"
        private const val GROUP_SECONDS = "seconds"
    }

    init {
        start()
    }
}

interface DownloadProgressCallback {
    fun onProgressUpdate(progress: Float, etaInSeconds: Long)
}
