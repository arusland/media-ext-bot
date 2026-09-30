package io.arusland.telegram

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.telegram.telegrambots.meta.api.objects.MessageEntity

class CaptionTest {
    @Test
    fun testSubstringShiftsAndClipsEntities() {
        // "http://a.b Hello world"
        val caption = Caption(
            "http://a.b Hello world",
            listOf(
                MessageEntity("url", 0, 10),
                MessageEntity("italic", 5, 11),
                MessageEntity("bold", 17, 5)
            )
        )

        val result = caption.substring(11)

        assertEquals("Hello world", result.text)
        assertEquals(listOf("italic" to (0 to 5), "bold" to (6 to 5)), result.entities.simplify())
    }

    @Test
    fun testTrim() {
        val caption = Caption("  Hello  ", listOf(MessageEntity("bold", 0, 9)))

        val result = caption.trim()

        assertEquals("Hello", result.text)
        assertEquals(listOf("bold" to (0 to 5)), result.entities.simplify())
    }

    @Test
    fun testTrimBlank() {
        assertEquals(Caption.EMPTY, Caption("   ", listOf(MessageEntity("bold", 0, 3))).trim())
    }

    @Test
    fun testSurrogatePairs() {
        // emoji takes 2 UTF-16 code units, same as Telegram offsets
        val caption = Caption("x 😀 bold", listOf(MessageEntity("bold", 5, 4)))

        val result = caption.substring(2)

        assertEquals("😀 bold", result.text)
        assertEquals(listOf("bold" to (3 to 4)), result.entities.simplify())
        assertEquals("bold", result.text.substring(3, 7))
    }

    private fun List<MessageEntity>.simplify() = map { it.type to (it.offset to it.length) }
}
