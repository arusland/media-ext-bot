package io.arusland.telegram

import org.apache.commons.lang3.StringUtils
import org.slf4j.LoggerFactory
import org.telegram.telegrambots.meta.TelegramBotsApi
import org.telegram.telegrambots.meta.exceptions.TelegramApiException
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession
import java.net.Authenticator
import java.net.PasswordAuthentication

/**
 * @author Ruslan Absalyamov
 * @since 1.0
 */
object MainApp {
    private val log = LoggerFactory.getLogger(MainApp::class.java)

    @JvmStatic
    fun main(args: Array<String>) {
        val socksUsername = System.getProperty("java.net.socks.username")
        val socksPassword = System.getProperty("java.net.socks.password")

        if (StringUtils.isNotBlank(socksUsername) && StringUtils.isNotBlank(socksPassword)) {
            log.warn("using SOCKS: socksUsername: $socksUsername")
            Authenticator.setDefault(ProxyAuth(socksUsername, socksPassword))
        }

        val telegramBotsApi = TelegramBotsApi(DefaultBotSession::class.java)
        try {
            val config = BotConfig.load("application.properties")
            telegramBotsApi.registerBot(MediaExtTelegramBot(config))
        } catch (e: TelegramApiException) {
            e.printStackTrace()
        }
    }

    private class ProxyAuth(socksUsername: String, socksPassword: String) : Authenticator() {
        private val auth: PasswordAuthentication = PasswordAuthentication(socksUsername, socksPassword.toCharArray())

        override fun getPasswordAuthentication(): PasswordAuthentication {
            return auth
        }
    }
}
