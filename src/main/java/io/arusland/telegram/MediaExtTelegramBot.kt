package io.arusland.telegram

import io.arusland.twitter.TwitterHelper
import io.arusland.util.isFileSupported
import io.arusland.util.isImage
import io.arusland.util.isVideo
import io.arusland.util.loadBinaryFile
import org.apache.commons.io.FileUtils
import org.apache.commons.io.FilenameUtils
import org.apache.commons.lang3.Validate
import org.slf4j.LoggerFactory
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.send.*
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.media.InputMediaPhoto
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow
import org.telegram.telegrambots.meta.exceptions.TelegramApiException
import java.io.File
import java.net.MalformedURLException
import java.net.URL
import java.util.*
import kotlin.collections.ArrayList

/**
 * @author Ruslan Absalyamov
 * @since 1.0
 */
class MediaExtTelegramBot constructor(config: BotConfig) : TelegramLongPollingBot() {
    private val log = LoggerFactory.getLogger(javaClass)
    private val config: BotConfig = Validate.notNull(config, "config")
    private val adminChatId: Long = config.adminId
    private val twitter: TwitterHelper
    private val allowedUsers = mutableSetOf<Long>()
    private val bannedUsers = mutableSetOf<Long>()
    private val PROPS_DIR = File(System.getProperty("user.home"), ".media-ext")
    private val PROPS_FILE = File(PROPS_DIR, "media-ext.properties")
    private val writeConfig = BotConfig.load(PROPS_FILE.path, false)

    private var lastMessage: Message? = null
    private var lastMessageTime: Date = Date()

    init {
        this.twitter = TwitterHelper(File("/tmp"), File(config.ffMpegPath),
                File(config.ffProbePath))
        log.info(String.format("Media-ext bot started"))
        sendMarkdownMessage(adminChatId, "*Bot started!*")
        allowedUsers.addAll(writeConfig.allowedUsersIds)
        bannedUsers.addAll(writeConfig.bannedUsersIds)
    }

    override fun onUpdateReceived(update: Update) {
        log.info("got message: {}", update)

        if (update.hasMessage()) {
            val chatId = update.message.chatId!!
            val userId = update.message.from.id!!.toLong()
            val isAdmin = adminChatId == userId

            if (!isAdmin) {
                if (writeConfig.allowAnon && !allowedUsers.contains(userId)) {
                    allowedUsers.add(userId)
                    saveProps()
                }

                if (!allowedUsers.contains(userId) || bannedUsers.contains(userId)) {
                    try {
                        sendMarkdownMessage(chatId, "⚠️*Sorry, this bot only for his owner :)*️")

                        sendAlertToAdmin(update)
                    } catch (e: TelegramApiException) {
                        e.printStackTrace()
                        log.error(e.message, e)
                    }

                    return
                }
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
                    } else if (command.startsWith("http")) {
                        handleUrl(command, chatId, getLastComment())
                    }
                } else if (update.message.hasVideo()) {
                    val video = update.message.video
                    sendVideo(chatId, video.fileId, getLastComment())
                } else if (update.message.hasPhoto()) {
                    val maxPhoto = update.message.photo.maxBy { it.height * it.width }?.fileId!!
                    sendImages(chatId, arrayOf(maxPhoto), getLastComment())
                } else {
                    sendMessage(chatId, UNKNOWN_COMMAND)
                }

                lastMessage = update.message
                lastMessageTime = Date()
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

    private fun getLastComment(): String {
        val lastMessage = lastMessage
        if ((Date().time - lastMessageTime.time) < 1000 && lastMessage != null) {
            return if (lastMessage.hasText()) lastMessage.text else lastMessage.caption
        }

        return ""
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

    private fun handleUrl(command: String, chatId: Long, lastComment: String) {
        try {
            val index = command.indexOf(' ')
            val comment = if (index > 0) command.substring(index + 1) else lastComment
            val urlRaw = if (index > 0) command.substring(0, index) else command
            val url = URL(urlRaw)

            sendMarkdownMessage(chatId, "_Please, wait..._")

            when {
                urlRaw.startsWith("https://twitter.com") -> handleTwitterUrl(url, chatId, comment)
                isFileSupported(urlRaw) -> handleBinaryUrl(url, chatId, comment)
                else -> sendMessage(chatId, UNKNOWN_COMMAND)
            }
        } catch (e: MalformedURLException) {
            log.error(e.message, e)
            sendMarkdownMessage(chatId, "⛔*Invalid url*")
        }
    }

    private fun handleBinaryUrl(url: URL, chatId: Long, comment: String) {
        val ext = FilenameUtils.getExtension(url.path)
        val file = loadBinaryFile(url, System.nanoTime().toString(), ext)
        sendFile(chatId, file, comment)
        FileUtils.deleteQuietly(file)
    }

    private fun sendFile(chatId: Long, file: File, comment: String) {
        if (file.isVideo()) {
            sendVideo(chatId, file, comment)
        } else if (file.isImage()) {
            sendImage(chatId, file, comment)
        } else {
            sendDocument(chatId, file, comment)
        }
    }

    private fun sendDocument(chatId: Long, file: File, comment: String) {
        val doc = SendDocument()
        doc.chatId = chatId.toString()
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

    private fun handleTwitterUrl(url: URL, chatId: Long, comment: String) {
        // TODO: make async
        val media = twitter.downloadMediaFrom(url)
        val file = media.first
        val info = media.second
        val finalComment = if (comment == "@" && !info.text.isNullOrEmpty()) info.text else comment

        if (file != null) {
            if (file.exists()) {
                sendVideo(chatId, file, finalComment)
                FileUtils.deleteQuietly(file)
            } else {
                sendMarkdownMessage(chatId, "⛔*Media not found* \uD83D\uDE1E")
            }
        } else if (info.imageUrls.isNotEmpty()) {
            val files = info.imageUrls.mapIndexed { index, url ->
                loadBinaryFile(URL(url), info.tweetId + index.toString(), "jpg")
            }

            sendImages(chatId, files, finalComment)

            files.forEach { FileUtils.deleteQuietly(it) }
        } else {
            sendMarkdownMessage(chatId, "⛔*Media not found* \uD83D\uDE1E")
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
                handleAddUserCommand(chatId, arg.toLong())
            } else if ("delUser" == command) {
                handleRemoveUserCommand(chatId, arg.toLong())
            } else if ("listUsers" == command) {
                handleListUserCommand(chatId)
            } else if ("toggleAnon" == command) {
                handleToggleAnonCommand(chatId)
            } else {
                sendMessage(chatId, UNKNOWN_COMMAND)
            }
        } else {
            sendMessage(chatId, UNKNOWN_COMMAND)
        }
    }

    private fun handleToggleAnonCommand(chatId: Long) {
        writeConfig.allowAnon = !writeConfig.allowAnon

        sendMessage(chatId, "Anon is allowed: ${writeConfig.allowAnon}")
    }

    private fun handleListUserCommand(chatId: Long) {
        sendMessage(chatId, "Allowed users: $allowedUsers\nBanned users: $bannedUsers")
    }

    private fun handleAddUserCommand(chatId: Long, userId: Long) {
        allowedUsers.add(userId)
        bannedUsers.remove(userId)
        saveProps()
        sendMessage(chatId, "User added: $userId.\nAllowed users: $allowedUsers\nBanned users: $bannedUsers")
    }

    private fun handleRemoveUserCommand(chatId: Long, userId: Long) {
        allowedUsers.remove(userId)
        bannedUsers.add(userId)
        saveProps()
        sendMessage(chatId, "User banned: $userId.\nAllowed users: $allowedUsers\nBanned users: $bannedUsers")
    }

    private fun handleHelpCommand(chatId: Long, isAdmin: Boolean) {
        if (isAdmin) {
            sendMessage(chatId, """Please, send me some url!
                |Or command:
                |  /addUser user_id - add user
                |  /delUser user_id - remove user
                |  /listUsers - show allowed users
                |  /toggleAnon - (dis)allow anon users
                |  /kill - kill me :(
                |  /reboot me - reboot system!⚠️
                |
                |  Anon is allowed: ${writeConfig.allowAnon}
            """.trimMargin())
        } else {
            sendMarkdownMessage(chatId, """*Please, send me some twitter or direct media url*
                | `twitter_url @` - Media and caption from tweet
                | `twitter_url My own caption` - Media from tweet with custom caption
                |
                | Examples:
                |   `https://twitter.com/ziggush/status/1086543849605001216`
                |   `https://foo.bar/i/image.jpeg`
                |   `https://twitter.com/ziggush/status/1086543849605001216 Your caption here`
                |   `https://dump.video/i/7I75fT.mp4 LOL!`
            """.trimMargin())
        }
    }

    private fun sendVideo(chatId: Long, fileId: String, comment: String) {
        val video = SendVideo()
        video.chatId = chatId.toString()
        video.setVideo(fileId)

        if (comment.isNotBlank()) {
            video.caption = comment
        }

        try {
            log.info("Sending file: $video")
            execute(video)
        } catch (e: TelegramApiException) {
            log.error(e.message, e)
        }
    }

    private fun sendVideo(chatId: Long, file: File, comment: String) {
        val video = SendVideo()
        video.chatId = chatId.toString()
        video.setVideo(file)

        if (comment.isNotBlank()) {
            video.caption = comment
        }

        try {
            log.info("Sending file: $video")
            execute(video)
        } catch (e: TelegramApiException) {
            log.error(e.message, e)
        }
    }

    private fun sendImages(chatId: Long, files: Array<String>, comment: String) {
        val doc = SendMediaGroup()
        doc.chatId = chatId.toString()
        doc.media = files.map { imageIds -> InputMediaPhoto().setMedia(imageIds) }

        if (comment.isNotBlank()) {
            doc.media.first().caption = comment
        }

        try {
            log.info("Sending photos: $doc")
            execute(doc)
        } catch (e: TelegramApiException) {
            log.error(e.message, e)
        }
    }

    private fun sendImages(chatId: Long, files: List<File>, comment: String) {
        if (files.size == 1) {
            sendImage(chatId, files.first(), comment)
            return
        }

        val doc = SendMediaGroup()
        doc.chatId = chatId.toString()
        doc.media = files.map { file -> InputMediaPhoto().setMedia(file, file.name) }

        if (comment.isNotBlank()) {
            doc.media.first().caption = comment
        }

        try {
            log.info("Sending photos: $doc")
            execute(doc)
        } catch (e: TelegramApiException) {
            log.error(e.message, e)
        }
    }

    private fun sendImage(chatId: Long, file: File, comment: String) {
        val image = SendPhoto()
        image.chatId = chatId.toString()
        image.setPhoto(file)

        if (comment.isNotBlank()) {
            image.caption = comment
        }

        try {
            log.info("Sending photo: $image")
            execute(image)
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
            sendMessage.disableWebPagePreview()

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


    private fun loadIds(fileName: String): Set<Long> {
        var file = File(PROPS_DIR, fileName)

        return if (file.exists()) {
            file.readLines(Charsets.UTF_8)
                    .filter { it.isNotBlank() }
                    .map { it.toLong() }
                    .toSet()
        } else {
            emptySet()
        }
    }

    private fun saveProps() {
        if (!PROPS_DIR.exists()) {
            PROPS_DIR.mkdirs()
        }

        writeConfig.allowedUsersIds = allowedUsers.toList()
        writeConfig.bannedUsersIds = bannedUsers.toList()

        writeConfig.save(PROPS_FILE.canonicalPath)
    }

    companion object {
        private const val UNKNOWN_COMMAND = "⚠️Unknown command⚠"
        private const val TEXT_MESSAGE_MAX_LENGTH = 4096
    }
}
