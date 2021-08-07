package io.arusland.youtube.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class VideoFormat(
    val asr: Long = 0,
    val tbr: Long = 0,
    val abr: Long = 0,
    val format: String? = null,
    @JsonProperty("format_id")
    val formatId: String? = null,
    @JsonProperty("format_note")
    val formatNote: String? = null,
    val ext: String? = null,
    val preference: Long = 0,
    val vcodec: String? = null,
    val acodec: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    val filesize: Long = 0,
    val fps: Int = 0,
    val url: String? = null
)
