package io.arusland.telegram

import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.MessageEntity

/**
 * Caption text with its formatting (bold, italic, links...). Entity offsets are in UTF-16 code units.
 */
data class Caption(val text: String = "", val entities: List<MessageEntity> = emptyList()) {
    fun isBlank(): Boolean = text.isBlank()

    fun isNotBlank(): Boolean = text.isNotBlank()

    /**
     * Returns entities for sending, null when there is no formatting.
     */
    fun entitiesOrNull(): List<MessageEntity>? = entities.ifEmpty { null }

    fun substring(start: Int, end: Int = text.length): Caption {
        if (start == 0 && end == text.length) {
            return this
        }

        val newEntities = entities.mapNotNull { entity ->
            val from = maxOf(entity.offset, start)
            val to = minOf(entity.offset + entity.length, end)

            if (to > from) entity.copy(from - start, to - from) else null
        }

        return Caption(text.substring(start, end), newEntities)
    }

    fun trim(): Caption {
        val start = text.indexOfFirst { !it.isWhitespace() }

        if (start < 0) {
            return EMPTY
        }

        return substring(start, text.indexOfLast { !it.isWhitespace() } + 1)
    }

    private fun MessageEntity.copy(offset: Int, length: Int) =
        MessageEntity(type, offset, length, url, user, language, customEmojiId, null)

    companion object {
        val EMPTY = Caption()

        fun ofText(message: Message) = Caption(message.text ?: "", message.entities.orEmpty())

        fun ofCaption(message: Message) = Caption(message.caption ?: "", message.captionEntities.orEmpty())
    }
}
