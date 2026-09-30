package io.arusland.telegram

import io.arusland.youtube.model.DownloadStatus
import org.slf4j.LoggerFactory
import java.util.Locale

/**
 * Edits a single status message with the current operation and a progress bar.
 *
 * Telegram rate-limits message edits and rejects edits which don't change the text,
 * so progress-only updates are throttled, while stage changes are shown immediately.
 */
class StatusMessageUpdater(
    private val minIntervalMs: Long = 2000,
    private val edit: (String) -> Unit
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private var lastStage: String? = null
    private var lastText: String? = null
    private var lastEditTime = 0L

    @Synchronized
    fun update(status: DownloadStatus) {
        val now = System.currentTimeMillis()
        val stageChanged = status.stage != lastStage

        if (!stageChanged && now - lastEditTime < minIntervalMs) {
            return
        }

        lastStage = status.stage
        setText(format(status), now)
    }

    /**
     * Shows the text immediately, bypassing throttling.
     */
    @Synchronized
    fun show(text: String) {
        lastStage = null
        setText(text, System.currentTimeMillis())
    }

    private fun setText(text: String, now: Long) {
        if (text == lastText) {
            return
        }

        try {
            edit(text)
            lastText = text
            lastEditTime = now
        } catch (e: Exception) {
            log.warn("Failed to update status message: {}", e.message)
        }
    }

    companion object {
        private const val BAR_LENGTH = 12

        fun format(status: DownloadStatus): String {
            val percent = status.percent ?: return "⏳ *${status.stage}*"
            val filled = (percent.coerceIn(0f, 100f) / 100 * BAR_LENGTH).toInt()
            val bar = "▓".repeat(filled) + "░".repeat(BAR_LENGTH - filled)
            val eta = status.etaSeconds?.let { " · ETA ${formatSeconds(it)}" } ?: ""

            return "⏳ *${status.stage}*\n`$bar` ${"%.1f".format(Locale.ROOT, percent)}%$eta"
        }

        private fun formatSeconds(seconds: Long): String {
            val h = seconds / 3600
            val m = seconds % 3600 / 60
            val s = seconds % 60

            return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
        }
    }
}
