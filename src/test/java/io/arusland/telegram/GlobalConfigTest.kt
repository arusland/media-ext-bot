package io.arusland.telegram

import org.junit.jupiter.api.Test

class GlobalConfigTest {
    @Test
    fun testConfig() {
        val config = GlobalConfig(configs = listOf(GlobalUserConfig(userId = 1233445566,
                sendTo = listOf(SendToChat(chatId = "1234567890", name = "Alert Channel")))))

        config.saveTo()
    }
}
