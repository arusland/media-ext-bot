package io.arusland.youtube.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class VideoThumbnail(
    val url: String? = null,
    val id: String? = null
)
