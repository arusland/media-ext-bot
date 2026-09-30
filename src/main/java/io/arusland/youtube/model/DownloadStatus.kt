package io.arusland.youtube.model

/**
 * Current stage of a video download.
 *
 * @param percent progress of the current stage in percents (0..100), null when unknown
 * @param etaSeconds estimated time left for the current stage, null when unknown
 */
data class DownloadStatus(val stage: String, val percent: Float? = null, val etaSeconds: Long? = null)
