package io.arusland.telegram

import io.arusland.twitter.TwitterHelper
import io.arusland.util.*
import io.arusland.youtube.YoutubeHelper
import org.apache.commons.io.FileUtils
import org.apache.commons.io.FilenameUtils
import org.apache.commons.lang3.Validate
import org.slf4j.LoggerFactory
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.send.*
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.PhotoSize
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.media.InputMediaPhoto
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow
import org.telegram.telegrambots.meta.exceptions.TelegramApiException
import java.io.File
import java.net.MalformedURLException
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

/**
 * @author Ruslan Absalyamov
 * @since 1.0
 */
class MediaExtTelegramBot constructor(config: BotConfig) : TelegramLongPollingBot(), UserCommandApi {
    private val log = LoggerFactory.getLogger(javaClass)
    private val config: BotConfig = Validate.notNull(config, "config")
    private val adminChatId: Long = config.adminId
    private val ffmpegUtils = FfMpegUtils(config.ffMpegPath, config.ffProbePath)
    private val tempDir = File("/tmp")
    private val twitterHelper: TwitterHelper = TwitterHelper(tempDir, ffmpegUtils)
    private val youtubeHelper = YoutubeHelper(tempDir, ffmpegUtils)
    private val allowedUsers = mutableSetOf<Long>()
    private val bannedUsers = mutableSetOf<Long>()
    private val PROPS_DIR = File(System.getProperty("user.home"), ".media-ext")
    private val PROPS_FILE = File(PROPS_DIR, "media-ext.properties")
    private val writeConfig = BotConfig.load(PROPS_FILE.path, false)
    private val globalConfig = GlobalConfig.loadFrom()
    private val userCommands = ConcurrentHashMap<Long, UserCommand>()
    private val userRecentMedia = ConcurrentHashMap<String, UserRecentMedia>()

    init {
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
            val userContext = UserContext.get(userId)

            if (!isAdmin) {
                if (writeConfig.allowAnon && !allowedUsers.contains(userId)) {
                    allowedUsers.add(userId)
                    saveProps()
                }

                if (!allowedUsers.contains(userId) || bannedUsers.contains(userId)) {
                    try {
                        sendMarkdownMessage(chatId, "⚠️*Sorry, this bot only for his owner :)*️")

                        sendAlertToAdmin(update, false)
                    } catch (e: TelegramApiException) {
                        e.printStackTrace()
                        log.error(e.message, e)
                    }

                    return
                }
            }

            try {
                if (!isAdmin) {
                    sendAlertToAdmin(update, true)
                }

                val userCommand = userCommands[userId]

                if (userCommand != null) {
                    if (!userCommand.execute(update)) {
                        userCommands.remove(userId)
                    }
                } else if (update.message.hasText()) {
                    val command = update.message.text.trim()

                    if (command.startsWith("/")) {
                        val cmd = parseCommand(command)
                        val arg = parseArg(command)
                        handleCommand(cmd, arg, chatId, userId, isAdmin)
                    } else if (command.startsWith("http")) {
                        handleUrl(command, chatId, userContext.getLastComment())
                    } else {
                        handlePlainText(command, chatId, userContext.getLastComment())
                    }
                } else if (update.message.hasVideo()) {
                    val video = update.message.video
                    sendVideo(chatId, video.fileId, userContext.getLastComment())
                } else if (update.message.hasPhoto()) {
                    val maxPhotoId = update.message.photo.mostBig().fileId
                    sendImagesById(chatId, listOf(maxPhotoId), userContext.getLastComment())
                } else if (update.message.hasDocument()) {
                    sendDocument(chatId, update.message.document.fileId, userContext.getLastComment())
                } else {
                    sendMessage(chatId, MESSAGE_UNKNOWN_COMMAND)
                }

                userContext.setLastMessage(update.message)
            } catch (e: Exception) {
                e.printStackTrace()
                log.error(e.message, e)
                try {
                    sendMessage(chatId, "Error: Something got wrong!")
                    sendErrorMessageToAdmin(e.message ?: "", update, true)
                } catch (e1: TelegramApiException) {
                    e1.printStackTrace()
                    log.error(e.message, e)
                }
            }
        }
    }

    private fun handlePlainText(command: String, chatId: Long, lastComment: String) {
        val mc = PATTERN_URL.matcher(command)

        if (mc.find()) {
            val url = mc.group(1)

            handleUrl(url, chatId, lastComment)
        } else {
            sendMessage(chatId, command)
        }
    }

    private fun sendAlertToAdmin(update: Update, allowed: Boolean) {
        val message = update.message
        val user = message.from
        val text = cleanMessage(message.text)
        val msg = """*Message from guest ($allowed):* ` user: ${user.userName} (${user.firstName} ${user.lastName}),
            | userId: ${user.id},  message: ${text}`""".trimMargin()
        sendMarkdownMessage(adminChatId, msg)
    }

    private fun sendErrorMessageToAdmin(message: String, update: Update, allowed: Boolean) {
        val message = update.message
        val user = message.from
        val msg =
            """⚠️*Message from guest ($allowed): failed* ` user: ${user.userName} (${user.firstName} ${user.lastName}),
            | userId: ${user.id},  error: ${message}`""".trimMargin()
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
                twitterHelper.isTwitterUrl(url) -> handleTwitterUrl(url, chatId, comment)
                youtubeHelper.isYoutubeUrl(url) -> handleYoutubeUrl(url, chatId, comment)
                isFileSupported(urlRaw) -> handleBinaryUrl(url, chatId, comment)
                else -> sendMessage(chatId, MESSAGE_UNKNOWN_COMMAND)
            }
        } catch (e: MalformedURLException) {
            log.error(e.message, e)
            sendMarkdownMessage(chatId, "⛔*Invalid url*")
        }
    }

    private fun handleYoutubeUrl(url: URL, chatId: Long, comment: String) {
        // TODO: make async
        val media = youtubeHelper.downloadMediaFrom(url)
        val file = media.first
        val info = media.second
        val finalComment = if (comment == "@" && !info.title.isNullOrEmpty()) info.title else comment

        if (file.exists()) {
            sendVideo(chatId, file, finalComment)
            FileUtils.deleteQuietly(file)
        } else {
            sendMarkdownMessage(chatId, "⛔*Media not found* \uD83D\uDE1E")
        }
    }

    private fun handleBinaryUrl(url: URL, chatId: Long, comment: String) {
        val ext = FilenameUtils.getExtension(url.path)
        val file = loadBinaryFile(url, System.nanoTime().toString(), ext)
        sendFile(chatId, file, comment)
        FileUtils.deleteQuietly(file)
    }

    private fun handleTwitterUrl(url: URL, chatId: Long, comment: String) {
        // TODO: make async
        val media = twitterHelper.downloadMediaFrom(url)
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
        } else if ("sendto" == command) {
            sendMessage(chatId, MESSAGE_PLEASE_SEND_TO)
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
                sendMessage(chatId, MESSAGE_UNKNOWN_COMMAND)
            }
        } else {
            sendMessage(chatId, MESSAGE_UNKNOWN_COMMAND)
        }
    }

    override fun resendRecentMedia(userId: Long, chatId: String) {
        val recentMedia = userRecentMedia[userId.toString()] ?: throw IllegalStateException("Recent media not found")

        val type = recentMedia.fileIds.first().fileType

        when (type) {
            MediaType.Photo -> sendImagesById(
                chatId, recentMedia.fileIds.map { it.fileId },
                recentMedia.caption, updateRecent = false
            )
            MediaType.Video -> sendVideo(
                chatId, recentMedia.fileIds.first().fileId,
                recentMedia.caption, updateRecent = false
            )
            MediaType.Document -> sendDocument(
                chatId, recentMedia.fileIds.first().fileId,
                recentMedia.caption, updateRecent = false
            )
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
            sendMessage(
                chatId, """Please, send me some url!
                |Or command:
                |  /addUser user_id - add user
                |  /delUser user_id - remove user
                |  /listUsers - show allowed users
                |  /toggleAnon - (dis)allow anon users
                |  /kill - kill me :(
                |  /reboot me - reboot system!⚠️
                |
                |  Anon is allowed: ${writeConfig.allowAnon}
            """.trimMargin()
            )
        } else {
            sendMarkdownMessage(
                chatId, """*Please, send me some twitter or direct media url*
                | `twitter_url @` - Media and caption from tweet
                | `twitter_url My own caption` - Media from tweet with custom caption
                |
                | Examples:
                |   `https://twitter.com/ziggush/status/1086543849605001216`
                |   `https://foo.bar/i/image.jpeg`
                |   `https://twitter.com/ziggush/status/1086543849605001216 Your caption here`
                |   `https://dump.video/i/7I75fT.mp4 LOL!`
            """.trimMargin()
            )
        }
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

    private fun sendDocument(chatId: Long, file: File, comment: String, updateRecent: Boolean = true) {
        sendDocument(chatId.toString(), file, comment, updateRecent)
    }

    private fun sendDocument(chatId: String, file: File, comment: String, updateRecent: Boolean) {
        val doc = SendDocument()
        doc.chatId = chatId
        doc.setDocument(file)

        if (comment.isNotBlank()) {
            doc.caption = comment
        }

        try {
            log.info("Sending file: $doc")
            val msg = execute(doc)

            if (updateRecent) {
                userRecentMedia[chatId] = UserRecentMedia(
                    listOf(
                        MediaFile(
                            fileId = msg.document.fileId,
                            fileType = MediaType.Document
                        )
                    ), caption = comment
                )
            }
        } catch (e: TelegramApiException) {
            log.error(e.message, e)
        }
    }

    private fun sendDocument(chatId: Long, fileId: String, comment: String, updateRecent: Boolean = true) {
        sendDocument(chatId.toString(), fileId, comment, updateRecent)
    }

    private fun sendDocument(chatId: String, fileId: String, comment: String, updateRecent: Boolean) {
        val doc = SendDocument()
        doc.chatId = chatId
        doc.setDocument(fileId)

        if (comment.isNotBlank()) {
            doc.caption = comment
        }

        try {
            log.info("Sending file: $doc")
            val msg = execute(doc)

            if (updateRecent) {
                userRecentMedia[chatId] = UserRecentMedia(
                    listOf(
                        MediaFile(
                            fileId = msg.document.fileId,
                            fileType = MediaType.Document
                        )
                    ), caption = comment
                )
            }
        } catch (e: TelegramApiException) {
            log.error(e.message, e)
        }
    }

    private fun sendVideo(chatId: Long, fileId: String, comment: String, updateRecent: Boolean = true) {
        sendVideo(chatId.toString(), fileId, comment, updateRecent)
    }

    private fun sendVideo(chatId: String, fileId: String, comment: String, updateRecent: Boolean) {
        val video = SendVideo()
        video.chatId = chatId
        video.setVideo(fileId)

        if (comment.isNotBlank()) {
            video.caption = comment
        }

        try {
            log.info("Sending file: $video")
            execute(video)

            if (updateRecent) {
                userRecentMedia[chatId] = UserRecentMedia(
                    listOf(
                        MediaFile(
                            fileId = fileId,
                            fileType = MediaType.Video
                        )
                    ), caption = comment
                )
            }
        } catch (e: TelegramApiException) {
            log.error(e.message, e)
        }
    }

    private fun sendVideo(chatId: Long, file: File, comment: String, updateRecent: Boolean = true) {
        sendVideo(chatId.toString(), file, comment, updateRecent)
    }

    private fun sendVideo(chatId: String, file: File, comment: String, updateRecent: Boolean) {
        val video = SendVideo()
        video.chatId = chatId
        video.setVideo(file)

        if (comment.isNotBlank()) {
            video.caption = comment
        }

        try {
            log.info("Sending file: $video")
            val msg = execute(video)

            if (updateRecent) {
                userRecentMedia[chatId] = UserRecentMedia(
                    listOf(
                        MediaFile(
                            fileId = msg.video.fileId,
                            fileType = MediaType.Video
                        )
                    ), caption = comment
                )
            }
        } catch (e: TelegramApiException) {
            log.error(e.message, e)
        }
    }

    private fun sendImagesById(chatId: Long, filesIds: List<String>, comment: String, updateRecent: Boolean = true) {
        sendImagesById(chatId.toString(), filesIds, comment, updateRecent)
    }

    private fun sendImagesById(chatId: String, filesIds: List<String>, comment: String, updateRecent: Boolean) {
        val doc = SendMediaGroup()
        doc.chatId = chatId
        doc.media = filesIds.map { imageIds -> InputMediaPhoto().setMedia(imageIds) }

        if (comment.isNotBlank()) {
            doc.media.first().caption = comment
        }

        try {
            log.info("Sending photos: $doc")
            execute(doc)

            if (updateRecent) {
                userRecentMedia[chatId] = UserRecentMedia(filesIds.map {
                    MediaFile(
                        fileId = it,
                        fileType = MediaType.Photo
                    )
                }, caption = comment)
            }
        } catch (e: TelegramApiException) {
            log.error(e.message, e)
        }
    }

    private fun sendImages(chatId: Long, files: List<File>, comment: String, updateRecent: Boolean = true) {
        if (files.size == 1) {
            sendImage(chatId, files.first(), comment)
            return
        }

        sendImages(chatId.toString(), files, comment, updateRecent)
    }

    private fun sendImages(chatId: String, files: List<File>, comment: String, updateRecent: Boolean) {
        val doc = SendMediaGroup()
        doc.chatId = chatId
        doc.media = files.map { file -> InputMediaPhoto().setMedia(file, file.name) }

        if (comment.isNotBlank()) {
            doc.media.first().caption = comment
        }

        try {
            log.info("Sending photos: $doc")
            val msgs = execute(doc)

            if (updateRecent) {
                userRecentMedia[chatId] = UserRecentMedia(msgs.map {
                    MediaFile(
                        fileId = it.photo.mostBig().fileId,
                        fileType = MediaType.Photo
                    )
                }, caption = comment)
            }
        } catch (e: TelegramApiException) {
            log.error(e.message, e)
        }
    }

    private fun sendImage(chatId: Long, file: File, comment: String, updateRecent: Boolean = true) {
        sendImage(chatId.toString(), file, comment, updateRecent)
    }

    private fun sendImage(chatId: String, file: File, comment: String, updateRecent: Boolean) {
        val image = SendPhoto()
        image.chatId = chatId
        image.setPhoto(file)

        if (comment.isNotBlank()) {
            image.caption = comment
        }

        try {
            log.info("Sending photo: $image")
            val msg = execute(image)

            if (updateRecent) {
                userRecentMedia[chatId] = UserRecentMedia(
                    listOf(
                        MediaFile(
                            fileId = msg.photo.mostBig().fileId,
                            fileType = MediaType.Photo
                        )
                    ), caption = comment
                )
            }
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

    private fun sendHtmlMessage(chatId: Long, message: String) {
        sendMessage(chatId, message, false, true)
    }

    private fun sendMarkdownMessage(chatId: Long, message: String) {
        sendMessage(chatId, message, true, false)
    }

    override fun sendMessageTo(chatId: Long, message: String, markDown: Boolean, html: Boolean) {
        sendMessage(chatId, message, markDown, html)
    }

    private fun sendMessage(chatId: Long, message: String, markDown: Boolean = false, html: Boolean = false) {
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
            sendMessage.chatId = chatId.toString()
            sendMessage.text = message
            sendMessage.disableWebPagePreview()

            applyKeyboard(sendMessage, chatId)

            log.info(String.format("send (length: %d): %s", message.length, message))

            execute<Message, SendMessage>(sendMessage)
        }
    }

    private fun applyKeyboard(sendMessage: SendMessage, chatId: Long) {
        val replyKeyboardMarkup = ReplyKeyboardMarkup()
        sendMessage.replyMarkup = replyKeyboardMarkup
        replyKeyboardMarkup.selective = true
        replyKeyboardMarkup.resizeKeyboard = true

        val keyboard = ArrayList<KeyboardRow>()

        val keyboardFirstRow = KeyboardRow()

        val userConfig = globalConfig.getUserConfig(chatId)

        if (userConfig != null && userConfig.sendTo.isNotEmpty()) {
            if (sendMessage.text == MESSAGE_PLEASE_SEND_TO) {
                userConfig.sendTo.forEach { chat ->
                    keyboardFirstRow.add(chat.name)
                }

                userCommands[chatId] = SendToCommand(chatId, userConfig.sendTo, this)
            } else {
                keyboardFirstRow.add("/help")
                keyboardFirstRow.add("/sendto")
            }
        } else {
            keyboardFirstRow.add("/help")
        }

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

    data class UserRecentMedia(val fileIds: List<MediaFile>, val caption: String)

    data class MediaFile(val fileId: String, val fileType: MediaType)

    enum class MediaType {
        Photo,

        Video,

        Document
    }

    fun List<PhotoSize>.mostBig(): PhotoSize = this.maxByOrNull { it.height * it.width }!!

    companion object {
        private const val MESSAGE_UNKNOWN_COMMAND = "⚠️Unknown command⚠"
        private val MESSAGE_PLEASE_SEND_TO = "Please, select a chat you want to send"
        private const val TEXT_MESSAGE_MAX_LENGTH = 4096
        private val PATTERN_URL = Pattern.compile("(https*://[^\\s]+)")
    }
}
