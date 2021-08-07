package io.arusland.telegram

import io.arusland.twitter.TwitterHelper
import io.arusland.util.FfMpegUtils
import org.junit.jupiter.api.Test
import java.io.File
import java.net.URL

/**
 * @author Ruslan Absalyamov
 * @since 1.0
 */
class TwitterHelperTest {
    private val ffmpegUtils = FfMpegUtils("/usr/bin/ffmpeg", "/usr/bin/ffprobe")
    private val helper = TwitterHelper(File("/tmp"), ffmpegUtils)

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
