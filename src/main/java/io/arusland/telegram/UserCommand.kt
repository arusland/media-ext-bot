package io.arusland.telegram

import org.telegram.telegrambots.meta.api.objects.Update

abstract class UserCommand(val userId: Long, val bot: UserCommandApi) {
    /**
     * Returns true if command must stay active, false remove current command
     */
    abstract fun execute(message: Update): Boolean
}

class SendToCommand(userId: Long, private val sendTo: List<SendToChat>, bot: UserCommandApi)
    : UserCommand(userId, bot) {
    override fun execute(update: Update): Boolean {
        if (update.message.hasText()) {
            val chat = sendTo.find { it.name == update.message.text }

            if (chat != null) {
                bot.resendRecentMedia(userId, chat.chatId)
                // TODO: check sending result
                bot.sendMessageTo(userId, "Message successfully sent")
            }
        }

        return false
    }
}

class EditLastCaptionCommand(userId: Long, bot: UserCommandApi)
    : UserCommand(userId, bot) {
    override fun execute(update: Update): Boolean {
        if (update.message.hasText()) {
            val newCaption = update.message.text

            if (newCaption.isNotBlank()) {
                bot.editLastCaption(userId, newCaption)
            }
        }

        return false
    }
}


interface UserCommandApi {
    fun resendRecentMedia(userId: Long, chatId: String)

    fun sendMessageTo(chatId: Long, message: String, markDown: Boolean = false, html: Boolean = false)

    fun editLastCaption(userId: Long, newCaption: String)
}
