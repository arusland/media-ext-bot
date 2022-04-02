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

    fun sendMediaDelayed(
        chatId: Long,
        mediaFile: MediaFile,
        comment: String
    ) {
        synchronized(lastMediaGroups) {
            val group = getGroup(chatId)

            lastMediaGroups[chatId] = group.withNewMedia(mediaFile, comment)
        }

        sendMediaDelayedAsync(chatId)
    }

    /**
     * Returns actual group
     */
    private fun getGroup(chatId: Long) = lastMediaGroups.computeIfAbsent(chatId) { MediaGroup.of(chatId) }
        .clearIfExpired(groupingTimeout)

    private fun sendMediaDelayedAsync(chatId: Long) {
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
                        sendMediaDelayedAsync(chatId)
                    }
                }
            }
        }
    }

    fun setCaption(chatId: Long, caption: String) {
        synchronized(lastMediaGroups) {
            lastMediaGroups[chatId] = getGroup(chatId).withNewCaption(caption)
        }

        sendMediaDelayedAsync(chatId)
    }
}
