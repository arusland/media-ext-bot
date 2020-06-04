package io.arusland.telegram

import io.arusland.twitter.TwitterHelper
import org.junit.jupiter.api.Test
import java.io.File
import java.net.URL

/**
 * @author Ruslan Absalyamov
 * @since 1.0
 */
class TwitterHelperTest {
    val helper = TwitterHelper(File("/tmp"), File("/usr/bin/ffmpeg"),
            File("/usr/bin/ffprobe"))

    @Test
    fun testTest() {
        val info = helper.loadInfoNew(URL("https://twitter.com/dreamonUn/status/1268281349322006534"))

        println(info)
    }

    @Test
    fun testLoadMedia() {
        val media = helper.downloadMediaFrom(URL("https://twitter.com/dreamonUn/status/1268281349322006534"))

        println("File: " + media.first)
        println("Info: " + media.second)
    }
}
