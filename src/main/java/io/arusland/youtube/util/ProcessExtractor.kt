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

    override fun run() {
        try {
            val currentLine = StringBuilder()
            var nextChar: Int
            while (stream.read().also { nextChar = it } != -1) {
                buffer.append(nextChar.toChar())
                if (nextChar == '\r'.code || nextChar == '\n'.code) {
                    if (callback != null && currentLine.isNotEmpty()) {
                        processOutputLine(currentLine.toString())
                    }
                    currentLine.setLength(0)
                    continue
                }
                currentLine.append(nextChar.toChar())
            }
            if (callback != null && currentLine.isNotEmpty()) {
                processOutputLine(currentLine.toString())
            }
        } catch (ignored: IOException) {
        }
    }

    private fun processOutputLine(line: String) {
        val callback = this.callback ?: return
        val m = PROGRESS.matcher(line)
        if (m.find()) {
            val progress = m.group(GROUP_PERCENT).toFloat()
            val eta = m.group(GROUP_ETA)?.let { parseEta(it) } ?: -1
            callback.onProgressUpdate(progress, eta)
            return
        }

        when {
            line.startsWith("[download] Destination:") -> callback.onStage(STAGE_DOWNLOADING)
            line.startsWith("[Merger]") -> callback.onStage(STAGE_MERGING)
            line.startsWith("[ExtractAudio]") -> callback.onStage(STAGE_EXTRACTING_AUDIO)
            line.startsWith("[Fixup") -> callback.onStage(STAGE_FIXING)
        }
    }

    companion object {
        const val STAGE_DOWNLOADING = "downloading"
        const val STAGE_MERGING = "merging"
        const val STAGE_EXTRACTING_AUDIO = "extracting_audio"
        const val STAGE_FIXING = "fixing"

        private const val GROUP_PERCENT = "percent"
        private const val GROUP_ETA = "eta"
        private val PROGRESS =
            Pattern.compile("^\\[download]\\s+(?<percent>\\d+(?:\\.\\d+)?)%(?:.*ETA (?<eta>\\d+(?::\\d+)+))?")

        /**
         * Parses "SS", "MM:SS" or "HH:MM:SS" into seconds.
         */
        fun parseEta(eta: String): Long = eta.split(':').fold(0L) { acc, part -> acc * 60 + part.toLong() }
    }

    init {
        start()
    }
}

interface DownloadProgressCallback {
    /**
     * @param etaInSeconds -1 when unknown
     */
    fun onProgressUpdate(progress: Float, etaInSeconds: Long)

    fun onStage(stage: String) {}
}
