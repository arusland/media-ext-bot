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
            }
        }

        return false
    }
}


interface UserCommandApi {
    fun resendRecentMedia(userId: Long, chatId: String)
}
