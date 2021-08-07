package io.arusland.youtube.model

/**
 * Youtube response
 */
data class YoutubeResponse(
    val command: String,
    val options: Map<String, String?>,
    val directory: String?,
    val exitCode: Int,
    val elapsedTime: Int,
    val out: String,
    val err: String
)
