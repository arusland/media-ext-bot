package io.arusland.telegram

import io.arusland.twitter.TwitterHelper
import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.Validate
import org.slf4j.LoggerFactory
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.send.SendDocument
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow
import org.telegram.telegrambots.meta.exceptions.TelegramApiException
import java.io.File
import java.net.MalformedURLException
import java.net.URL

/**
 * @author Ruslan Absalyamov
 * @since 1.0
 */
class MediaExtTelegramBot constructor(config: BotConfig) : TelegramLongPollingBot() {
    private val log = LoggerFactory.getLogger(javaClass)
    private val config: BotConfig = Validate.notNull(config, "config")
    private val adminChatId: Long = config.adminId.toLong()
    private val twitter: TwitterHelper
    private val allowedUsers = mutableSetOf<Long>()
    private val PROPS_DIR = File(System.getProperty("user.home"), ".media-ext")

    init {
        this.twitter = TwitterHelper(File("/tmp"), File(config.ffMpegPath),
                File(config.ffProbePath))
        log.info(String.format("Media-ext bot started"))
        sendMarkdownMessage(adminChatId, "*Bot started!*")
        allowedUsers.addAll(loadAllowedUsers())
    }

    override fun onUpdateReceived(update: Update) {
        log.info("got message: {}", update)

        if (update.hasMessage()) {
            val chatId = update.message.chatId!!
            val userId = update.message.from.id!!.toLong()
            val isAdmin = adminChatId == userId

            if (!isAdmin && !allowedUsers.contains(userId)) {
                try {
                    sendMarkdownMessage(chatId, "⚠️*Sorry, this bot only for his owner :)*️")

                    sendAlertToAdmin(update)
                } catch (e: TelegramApiException) {
                    e.printStackTrace()
                    log.error(e.message, e)
                }

                return
            }

            try {
                if (!isAdmin) {
                    sendAlertToAdmin(update)
                }

                if (update.message.hasText()) {
                    val command = update.message.text.trim()

                    if (command.startsWith("/")) {
                        val cmd = parseCommand(command)
                        val arg = parseArg(command)
                        handleCommand(cmd, arg, chatId, userId, isAdmin)
                    } else {
                        handleUrl(command, chatId)
                    }
                } else {
                    sendMessage(chatId, UNKNOWN_COMMAND)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                log.error(e.message, e)
                try {
                    sendMessage(chatId, "ERROR: " + e.message)
                } catch (e1: TelegramApiException) {
                    e1.printStackTrace()
                    log.error(e.message, e)
                }
            }
        }
    }

    private fun sendAlertToAdmin(update: Update) {
        val message = update.message
        val user = message.from
        val text = cleanMessage(message.text)
        val msg = """*Message from guest:* ` user: ${user.userName} (${user.firstName} ${user.lastName}),
            | userId: ${user.id},  message: ${text}`""".trimMargin()
        sendMarkdownMessage(adminChatId, msg)
    }

    private fun cleanMessage(text: String): String {
        return text.replace("`", "")
                .replace("*", "")
                .replace("_", "")
    }

    private fun handleUrl(command: String, chatId: Long) {
        try {
            val index = command.indexOf(' ')
            val comment = if (index > 0) command.substring(index + 1) else ""
            val urlRaw = if (index > 0) command.substring(0, index) else command

            val url = URL(urlRaw)
            sendMarkdownMessage(chatId, "_Please, wait..._")
            // TODO: make async
            val file = twitter.downloadMediaFrom(url)

            if (file.exists()) {
                sendFile(chatId, file, comment)
                FileUtils.deleteQuietly(file)
            } else {
                sendMarkdownMessage(chatId, "⛔*Media not found* \uD83D\uDE1E")
            }
        } catch (e: MalformedURLException) {
            log.error(e.message, e)
            sendMarkdownMessage(chatId, "⛔*Invalid url*")
        }
    }

    private fun handleCommand(command: String, arg: String, chatId: Long, userId: Long, isAdmin: Boolean) {
        if ("help" == command || "start" == command) {
            handleHelpCommand(chatId, isAdmin)
        } else if (isAdmin) {
            if ("kill" == command) {
                sendMarkdownMessage(chatId, "*Bye bye*")
                System.exit(0)
            } else if ("reboot" == command) {
                if ("me" == arg) {
                    sendMarkdownMessage(chatId, "*Bye bye*")
                    Runtime.getRuntime().exec("reboot")
                } else {
                    sendMarkdownMessage(chatId, "Proper command: /reboot me")
                }
            } else if ("addUser" == command) {
                handleAddUserCommand(chatId, arg)
            } else if ("delUser" == command) {
                handleRemoveUserCommand(chatId, arg)
            } else if ("listUsers" == command) {
                handleListUserCommand(chatId)
            } else {
                sendMessage(chatId, UNKNOWN_COMMAND)
            }
        } else {
            sendMessage(chatId, UNKNOWN_COMMAND)
        }
    }

    private fun handleListUserCommand(chatId: Long) {
        sendMessage(chatId, "Allowed users: $allowedUsers")
    }

    private fun handleAddUserCommand(chatId: Long, userIdRaw: String) {
        allowedUsers.add(userIdRaw.toLong())
        saveAllowedUsers(allowedUsers)
        sendMessage(chatId, "User added: $userIdRaw.\nAll users: $allowedUsers")
    }

    private fun handleRemoveUserCommand(chatId: Long, userIdRaw: String) {
        allowedUsers.remove(userIdRaw.toLong())
        saveAllowedUsers(allowedUsers)
        sendMessage(chatId, "User removed: $userIdRaw.\nAll users: $allowedUsers")
    }

    private fun handleHelpCommand(chatId: Long, isAdmin: Boolean) {
        if (isAdmin) {
            sendMessage(chatId, """Please, send me some url!
                |Or command:
                |  /addUser user_id - add user
                |  /delUser user_id - remove user
                |  /listUsers - show allowed users
                |  /kill - kill me :(
                |  /reboot me - reboot system!⚠️
            """.trimMargin())
        } else {
            sendMarkdownMessage(chatId, "*Please, send me some url*")
        }
    }

    fun sendFile(chatId: Long?, file: File, comment: String) {
        val doc = SendDocument()
        doc.chatId = chatId!!.toString()
        doc.setDocument(file)

        if (comment.isNotBlank()) {
            doc.caption = comment
        }

        try {
            log.info("Sending file: $doc")
            execute(doc)
        } catch (e: TelegramApiException) {
            log.error(e.message, e)
        }

    }

    private fun parseArg(command: String): String {
        val index = indexOfDelim(command)

        return if (index > 0 && index + 1 < command.length) command.substring(index + 1) else ""
    }

    private fun parseCommand(command: String): String {
        val index = indexOfDelim(command)

        return if (index > 0) command.substring(1, index) else command.substring(1)
    }

    private fun indexOfDelim(command: String): Int {
        val index1 = command.indexOf(" ")
        val index2 = command.indexOf("_")

        return minPositive(index1, index2)
    }

    private fun minPositive(index1: Int, index2: Int): Int {
        return if (index1 >= 0) {
            if (index2 >= 0) {
                Math.min(index1, index2)
            } else index1

        } else index2

    }

    override fun getBotUsername(): String {
        return config.botName
    }

    override fun getBotToken(): String {
        return config.botToken
    }

    private fun sendHtmlMessage(chatId: Long?, message: String) {
        sendMessage(chatId, message, false, true)
    }

    private fun sendMarkdownMessage(chatId: Long?, message: String) {
        sendMessage(chatId, message, true, false)
    }

    private fun sendMessage(chatId: Long?, message: String, markDown: Boolean = false, html: Boolean = false) {
        if (message.length > TEXT_MESSAGE_MAX_LENGTH) {
            val part1 = message.substring(0, TEXT_MESSAGE_MAX_LENGTH)
            sendMessage(chatId, part1)
            val part2 = message.substring(TEXT_MESSAGE_MAX_LENGTH)
            sendMessage(chatId, part2)
        } else {
            val sendMessage = SendMessage()

            if (markDown) {
                sendMessage.enableMarkdown(markDown)
            } else if (html) {
                sendMessage.enableHtml(html)
            }
            sendMessage.chatId = chatId!!.toString()
            sendMessage.text = message

            applyKeyboard(sendMessage)

            log.info(String.format("send (length: %d): %s", message.length, message))

            execute<Message, SendMessage>(sendMessage)
        }
    }

    private fun applyKeyboard(sendMessage: SendMessage) {
        val replyKeyboardMarkup = ReplyKeyboardMarkup()
        sendMessage.replyMarkup = replyKeyboardMarkup
        replyKeyboardMarkup.selective = true
        replyKeyboardMarkup.resizeKeyboard = true

        val keyboard = ArrayList<KeyboardRow>()

        val keyboardFirstRow = KeyboardRow()
        keyboardFirstRow.add("/help")

        keyboard.add(keyboardFirstRow)
        replyKeyboardMarkup.keyboard = keyboard
    }

    private fun loadAllowedUsers(): Set<Long> {
        var file = File(PROPS_DIR, ALLOWED_LIST)

        return if (file.exists()) {
            file.readLines(Charsets.UTF_8)
                    .filter { it.isNotBlank() }
                    .map { it.toLong() }
                    .toSet()
        } else {
            emptySet()
        }
    }

    private fun saveAllowedUsers(users: Set<Long>) {
        if (!PROPS_DIR.exists()) {
            PROPS_DIR.mkdirs()
        }

        var file = File(PROPS_DIR, ALLOWED_LIST)
        file.writeText(users.joinToString(System.lineSeparator()), Charsets.UTF_8)
    }

    companion object {
        private const val UNKNOWN_COMMAND = "⚠️Unknown command⚠"
        private const val ALLOWED_LIST = "allowedUsers.txt"
        private const val TEXT_MESSAGE_MAX_LENGTH = 4096
    }
}
