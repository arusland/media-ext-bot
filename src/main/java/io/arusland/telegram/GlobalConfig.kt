package io.arusland.telegram

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import java.io.File

data class GlobalConfig(val configs: List<GlobalUserConfig>) {
    fun saveTo(file: File = GLOBAL_CONFIG_FILE) {
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, this)
    }

    fun getUserConfig(userId: Long): GlobalUserConfig? {
        return configs.find { it.userId == userId }
    }

    companion object {
        private val GLOBAL_CONFIG_FILE = File("globalConfig.json")

        private val objectMapper = ObjectMapper()
                .registerModule(KotlinModule())

        fun loadFrom(file: File = GLOBAL_CONFIG_FILE): GlobalConfig {
            if (file.exists()) {
                return objectMapper.readValue(file, GlobalConfig::class.java)
            }

            return GlobalConfig(configs = emptyList())
        }
    }
}

data class GlobalUserConfig(val userId: Long, val sendTo: List<SendToChat>)

data class SendToChat(val name: String, val chatId: String)
