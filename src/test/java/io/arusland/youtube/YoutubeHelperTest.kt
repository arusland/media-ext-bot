package io.arusland.youtube

import io.arusland.util.FfMpegUtils
import io.arusland.util.JsonUtils
import org.junit.jupiter.api.Test
import java.io.File
import java.net.URL

class YoutubeHelperTest {
    private val ffmpegUtils = FfMpegUtils("/usr/bin/ffmpeg", "/usr/bin/ffprobe")
    private val youtubeHelper = YoutubeHelper(File("/tmp/test"), ffmpegUtils)

    @Test
    fun testDownloadByUrl() {
        val file = youtubeHelper.downloadVideo(URL("https://www.youtube.com/watch?v=XbqFZMIidZI"))

        println("File: ${file.absolutePath}, size: ${file.length()}")
    }

    @Test
    fun testGetVideoInfo() {
        val info = youtubeHelper.getVideoInfo(URL("https://www.youtube.com/watch?v=XbqFZMIidZI"))

        println(JsonUtils.toPrettyJson(info))
    }
}
