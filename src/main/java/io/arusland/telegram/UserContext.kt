package io.arusland.telegram

import org.telegram.telegrambots.meta.api.objects.Message
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class UserContext {
    private var lastMessage: Message? = null
    private var lastMessageTime: Date = Date()

    fun setLastMessage(value: Message?) {
        lastMessage = value
        lastMessageTime = Date()
    }

    fun getLastComment(): String {
        val lastMessage = lastMessage
        if ((Date().time - lastMessageTime.time) < 1000 && lastMessage != null) {
            return if (lastMessage.hasText()) lastMessage.text else lastMessage.caption ?: ""
        }

        return ""
    }

    companion object {
        fun get(userId: Long): UserContext {
            return map.getOrPut(userId) {
                UserContext()
            }
        }

        private val map = ConcurrentHashMap<Long, UserContext>()
    }
}
