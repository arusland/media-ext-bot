package io.arusland.youtube.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import org.glassfish.grizzly.http.HttpHeader

@JsonIgnoreProperties(ignoreUnknown = true)
data class VideoInfo(
    val id: String? = null,
    val fulltitle: String? = null,
    val title: String? = null,
    @JsonProperty("upload_date")
    val uploadDate: String? = null,
    @JsonProperty("display_id")
    val displayId: String? = null,
    val duration: Long = 0,
    val description: String? = null,
    val thumbnail: String? = null,
    val license: String? = null,
    @JsonProperty("uploader_id")
    val uploaderId: String? = null,
    val uploader: String? = null,
    @JsonProperty("player_url")
    val playerUrl: String? = null,
    @JsonProperty("webpage_url")
    val webpageUrl: String? = null,
    @JsonProperty("webpage_url_basename")
    val webpageUrlBasename: String? = null,
    val resolution: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    val format: String? = null,
    val ext: String? = null,
    @JsonProperty("http_headers")
    val httpHeader: HttpHeader? = null,
    val categories: ArrayList<String>? = null,
    val tags: ArrayList<String>? = null,
    val formats: ArrayList<VideoFormat>? = null,
    val thumbnails: ArrayList<VideoThumbnail>? = null
)
