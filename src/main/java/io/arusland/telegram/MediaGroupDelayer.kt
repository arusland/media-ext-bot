package io.arusland.telegram

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

class MediaGroupDelayer(
    private val delay: Long,
    private val groupingTimeout: Long,
    private val handler: (MediaGroup) -> Unit
) {
    private val lastMediaGroups = ConcurrentHashMap<Long, MediaGroup>()
    private val executor = Executors.newCachedThreadPool()

    fun hasMedia(chatId: Long): Boolean {
        return lastMediaGroups[chatId]?.isNotEmpty() ?: false
    }

    fun createNewGroup(chatId: Long, vararg mediaFiles: MediaFile) {
        lastMediaGroups[chatId] = MediaGroup.of(chatId).withNewMedias(mediaFiles.toList())
    }

    fun sendMediaDelayed(
        chatId: Long,
        mediaFile: MediaFile,
        comment: String
    ) {
        synchronized(lastMediaGroups) {
            val group = getActualGroup(chatId)

            lastMediaGroups[chatId] = group.withNewMedia(mediaFile, comment)
        }

        sendMediaDelayedAsync(chatId, delay)
    }

    /**
     * Returns actual group
     */
    private fun getActualGroup(chatId: Long) = getRecentGroup(chatId).clearIfExpired(groupingTimeout)

    /**
     * Returns last group. Probably expired
     */
    private fun getRecentGroup(chatId: Long) = lastMediaGroups.computeIfAbsent(chatId) { MediaGroup.of(chatId) }

    private fun sendMediaDelayedAsync(chatId: Long, delay: Long) {
        executor.submit {
            Thread.sleep(delay)

            synchronized(lastMediaGroups) {
                val group = lastMediaGroups[chatId]

                if (group != null) {
                    if (group.isExpired(delay)) {
                        if (!group.sent) {
                            lastMediaGroups[chatId] = group.copy(sent = true)
                            handler(group)
                        }
                    } else {
                        // try again later
                        sendMediaDelayedAsync(chatId, delay)
                    }
                }
            }
        }
    }

    fun setActualCaption(chatId: Long, caption: String) {
        synchronized(lastMediaGroups) {
            lastMediaGroups[chatId] = getActualGroup(chatId).withNewCaption(caption)
        }

        sendMediaDelayedAsync(chatId, delay)
    }

    fun resendWithNewCaption(chatId: Long, caption: String) {
        if (lastMediaGroups.containsKey(chatId)) {
            synchronized(lastMediaGroups) {
                lastMediaGroups[chatId] = getRecentGroup(chatId).withNewCaption(caption)
            }

            sendMediaDelayedAsync(chatId, 0L)
        }
    }
}
