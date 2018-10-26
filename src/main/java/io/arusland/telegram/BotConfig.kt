package io.arusland.telegram

import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.Validate
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.util.*
import kotlin.streams.toList

/**
 * Simple bot configuration
 */
class BotConfig protected constructor(prop: Properties, private val configFile: File) {
    private val log = LoggerFactory.getLogger(BotConfig::class.java)
    private val prop: Properties = Validate.notNull(prop, "prop")

    val botName: String
        get() = getProperty("bot.name")

    val botToken: String
        get() = getProperty("bot.token")

    val ffMpegPath: String
        get() = getProperty("ffmpeg.path")

    val ffProbePath: String
        get() = getProperty("ffprobe.path")

    /**
     * Returns admin user's id.
     */
    val adminId: Int
        get() {
            val selectedUsers = allowedUsersIds

            return if (selectedUsers.isEmpty()) 0 else selectedUsers[0]
        }

    /**
     * Returns allowed users ids.
     */
    val allowedUsersIds: List<Int>
        get() {
            if (prop.containsKey("allowed.userids")) {
                val ids = getProperty("allowed.userids")
                try {
                    return Arrays.stream<String>(ids.split(",".toRegex()).dropLastWhile({ it.isEmpty() }).toTypedArray())
                            .filter { p -> !p.isEmpty() }
                            .map { p -> Integer.parseInt(p) }
                            .toList()
                } catch (ex: NumberFormatException) {
                    log.error(ex.message, ex)
                }

            }

            return emptyList()
        }

    private fun getProperty(key: String): String {
        return Validate.notNull(prop.getProperty(key),
                "Configuration not found for key: $key")
    }

    private fun getProperty(key: String, defValue: String): String {
        val value = prop.getProperty(key)

        return StringUtils.defaultString(value, defValue)
    }

    companion object {
        fun load(fileName: String): BotConfig {
            val prop = Properties()
            var file: File? = null

            try {
                file = File(fileName).canonicalFile
                FileInputStream(fileName).use { input -> prop.load(input) }
            } catch (e: FileNotFoundException) {
                throw RuntimeException(e)
            } catch (e: IOException) {
                throw RuntimeException(e)
            }

            return BotConfig(prop, file)
        }
    }
}
