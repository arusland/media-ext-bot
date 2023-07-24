package io.arusland.youtube

import io.arusland.util.FfMpegUtils
import io.arusland.util.JsonUtils
import io.arusland.youtube.model.VideoInfo
import io.arusland.youtube.model.YoutubeRequest
import io.arusland.youtube.model.YoutubeResponse
import io.arusland.youtube.util.DownloadProgressCallback
import io.arusland.youtube.util.StreamBuffer
import io.arusland.youtube.util.StreamProcessExtractor
import org.apache.commons.io.FileUtils
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.URL
import java.util.*


class YoutubeHelper(private val tempDir: File, private val ffMpegUtils: FfMpegUtils) {
    fun isYoutubeUrl(url: URL): Boolean = url.toString().run { contains("youtube.com") || contains("youtu.be") }

    fun downloadMediaFrom(url: URL): Pair<File, VideoInfo> {
        val info = getVideoInfo(url)
        val file = downloadVideo(url)

        return file to info
    }

    fun downloadVideo(url: URL): File {
        log.debug("Download video from {}...", url)
        val fileId = "${UUID.randomUUID()}.tmp"
        val directory = tempDir.absolutePath
        val request = YoutubeRequest(url.toString(), directory)
        request.setOption("ignore-errors")
        request.setOption("output", fileId)
        request.setOption("retries", 10)
        val resp = execute(request, null)
        val fileRaw = tempDir.listFiles()!!
            .firstOrNull { it.isFile && it.name.contains(fileId) } ?: File(tempDir, fileId)

        if (log.isDebugEnabled) {
            log.debug(
                "Downloaded video {}, filesize: {}, url: {}\n\n{}",
                fileRaw, if (fileRaw.exists()) fileRaw.length() else 0, url, resp.out
            )
        }

        if (fileRaw.exists()) {
            // TODO: do not convert until you needed
            log.debug("Normalizing video {} from url {}...", fileRaw, url)
            val fileResult = File(tempDir, "${UUID.randomUUID()}.mp4")
            ffMpegUtils.convert(fileRaw, fileResult, videoCodec = "libx264")
            FileUtils.deleteQuietly(fileRaw)

            log.debug(
                "Normalized video filesize: {}, url: {}",
                if (fileResult.exists()) fileResult.length() else 0,
                url
            )

            return fileResult
        }

        return fileRaw
    }

    fun getVideoInfo(url: URL): VideoInfo {
        log.debug("Get info by {}...", url)
        val request = YoutubeRequest(url.toString())
        request.setOption("dump-json")
        request.setOption("no-playlist")
        val response: YoutubeResponse = execute(request)

        if (log.isDebugEnabled) {
            log.debug("Got info by url: {}\n{}", url, response.out)
        }

        try {
            return JsonUtils.parse(response.out, VideoInfo::class.java)
        } catch (e: IOException) {
            throw IllegalStateException("Unable to parse video information: " + e.message)
        }
    }

    private fun execute(request: YoutubeRequest, callback: DownloadProgressCallback? = null): YoutubeResponse {
        val command: String = buildCommand(request.buildOptions())
        val directory: String? = request.directory
        val options: Map<String, String?> = request.option
        val youtubeResponse: YoutubeResponse
        val process: Process
        val exitCode: Int
        val outBuffer = StringBuffer() //stdout
        val errBuffer = StringBuffer() //stderr
        val startTime = System.nanoTime()
        val split = command.split(" ").toTypedArray()
        val processBuilder = ProcessBuilder(*split)

        if (directory != null) processBuilder.directory(File(directory))
        process = try {
            processBuilder.start()
        } catch (e: IOException) {
            throw IllegalStateException(e)
        }
        val outStream: InputStream = process.inputStream
        val errStream: InputStream = process.errorStream
        val stdOutProcessor = StreamProcessExtractor(outBuffer, outStream, callback)
        val stdErrProcessor = StreamBuffer(errBuffer, errStream)

        exitCode = try {
            stdOutProcessor.join()
            stdErrProcessor.join()
            process.waitFor()
        } catch (e: InterruptedException) {
            throw IllegalStateException(e)
        }
        val out = outBuffer.toString()
        val err = errBuffer.toString()
        if (exitCode > 0) {
            throw IllegalStateException(err)
        }
        val elapsedTime = ((System.nanoTime() - startTime) / 1000000).toInt()
        youtubeResponse = YoutubeResponse(command, options, directory, exitCode, elapsedTime, out, err)

        return youtubeResponse
    }

    private fun buildCommand(command: String): String {
        return java.lang.String.format("%s %s", executablePath, command)
    }

    companion object {
        private val executablePath = "docker run --rm jauderho/yt-dlp"
        private val log = LoggerFactory.getLogger(YoutubeHelper::class.java)
    }
}
