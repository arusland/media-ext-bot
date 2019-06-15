package io.arusland.telegram

import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.Validate
import org.slf4j.LoggerFactory
import java.io.*
import java.util.*

/**
 * Simple bot configuration
 */
class BotConfig private constructor(prop: Properties) {
    private val log = LoggerFactory.getLogger(BotConfig::class.java)
    private val props: Properties = Validate.notNull(prop, "props")

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
    val adminId: Long
        get() {
            val selectedUsers = allowedUsersIds

            return if (selectedUsers.isEmpty()) 0 else selectedUsers[0]
        }

    /**
     * Returns allowed users ids.
     */
    var allowedUsersIds: List<Long>
        get() {
            return getLongList("allowed.userids")
        }
        set(value) {
            setLongList("allowed.userids", value)
        }

    var bannedUsersIds: List<Long>
        get() {
            return getLongList("banned.userids")
        }
        set(value) {
            setLongList("banned.userids", value)
        }

    var allowAnon: Boolean
        get() {
            return "true" == getProperty("allow.annon", "false")
        }
        set(value) {
            props.setProperty("allow.annon", value.toString())
        }

    private fun getProperty(key: String): String {
        return Validate.notNull(props.getProperty(key),
                "Configuration not found for key: $key")
    }

    private fun getProperty(key: String, defValue: String): String {
        val value = props.getProperty(key)

        return StringUtils.defaultString(value, defValue)
    }

    private fun setLongList(propName: String, list: List<Long>) {
        props.setProperty(propName, list.joinToString(","))
    }

    private fun getLongList(propName: String): List<Long> {
        return getProperty(propName, "")
                .split(",".toRegex())
                .filter { it.isNotBlank() }
                .map { it.trim().toLong() }
                .toList()
    }

    fun save(fileName: String) {
        FileOutputStream(fileName).use { output -> props.store(output, "Media Ext Bot") }
    }

    companion object {
        fun load(fileName: String, throwOnError: Boolean = true): BotConfig {
            val props = Properties()

            try {
                val file = File(fileName).canonicalFile
                FileInputStream(file).use { input -> props.load(input) }
            } catch (e: Exception) {
                if (throwOnError) {
                    throw RuntimeException(e)
                }
            }

            return BotConfig(props)
        }
    }
}
