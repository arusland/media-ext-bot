package io.arusland.telegram

import java.time.Duration
import java.time.LocalDateTime

data class UserRecentMedia(val fileIds: List<MediaFile>, val caption: String)

data class MediaGroup(
    val chatId: Long,
    val fileIds: List<MediaFile> = emptyList(),
    val caption: String = "",
    val updateTime: LocalDateTime = LocalDateTime.now(),
    val sent: Boolean = false
) {
    fun isExpired(timeoutMs: Long): Boolean =
        isNotEmpty() && updateTime.plusNanos(Duration.ofMillis(timeoutMs).toNanos()).isBefore(LocalDateTime.now());

    fun isNotEmpty(): Boolean = !isEmpty()

    fun isEmpty(): Boolean = fileIds.isEmpty()

    fun withNewMedia(mediaFile: MediaFile, comment: String): MediaGroup = copy(
        fileIds = fileIds.toMutableList().apply { add(mediaFile) },
        caption = caption.ifBlank { comment },
        updateTime = LocalDateTime.now(),
        sent = false
    )

    fun withNewMedias(mediaFiles: List<MediaFile>): MediaGroup = copy(
        fileIds = fileIds.toMutableList().apply { addAll(mediaFiles) },
        updateTime = LocalDateTime.now(),
        sent = false
    )

    fun withNewCaption(caption: String) = copy(
        caption = caption,
        updateTime = LocalDateTime.now(),
        sent = false
    )

    fun clearIfExpired(timeout: Long): MediaGroup = if (isExpired(timeout)) of(chatId) else this

    companion object {
        fun of(chatId: Long) = MediaGroup(chatId)
    }
}

data class MediaFile(val fileId: String, val fileType: MediaType) {
    companion object {
        fun image(fileId: String) = MediaFile(fileId, MediaType.Photo)

        fun video(fileId: String) = MediaFile(fileId, MediaType.Video)

        fun audio(fileId: String) = MediaFile(fileId, MediaType.Audio)

        fun document(fileId: String) = MediaFile(fileId, MediaType.Document)

        fun animation(fileId: String) = MediaFile(fileId, MediaType.Animation)
    }
}

enum class MediaType {
    Photo,

    Video,

    Audio,

    Document,

    Animation
}