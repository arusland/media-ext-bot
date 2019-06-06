package io.arusland.twitter

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import io.arusland.util.loadBinaryFile
import net.bramp.ffmpeg.FFmpeg
import net.bramp.ffmpeg.FFmpegExecutor
import net.bramp.ffmpeg.FFprobe
import net.bramp.ffmpeg.builder.FFmpegBuilder
import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils
import org.jsoup.Jsoup
import org.jsoup.nodes.TextNode
import org.slf4j.LoggerFactory
import java.io.*
import java.lang.Exception
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern
import kotlin.streams.toList

class TweetInfo(val bearerToken: String, val tweetId: String, val text: String, val imageUrls: List<String>)

class TwitterHelper(private val tempDir: File, private val ffmpegPath: File, private val ffprobePath: File) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun downloadMediaFrom(tweetUrl: URL): Pair<File?, TweetInfo> {
        val info = loadInfo(tweetUrl)!!
        val headers = hashMapOf("authorization" to "Bearer " + info.bearerToken)
        val tokenJson = loadText(URL("https://api.twitter.com/1.1/guest/activate.json"), headers, true)

        log.info(tokenJson)

        val tokenMap = Gson().fromJson(tokenJson, Map::class.java) as Map<String, String>

        val guestToken = tokenMap["guest_token"]!!

        log.info("guest_token=$guestToken")

        headers["x-guest-token"] = guestToken

        try {
            var config = loadText(URL("https://api.twitter.com/1.1/videos/tweet/config/" + info!!.tweetId + ".json"), headers, false)
            val configMap = Gson().fromJson(config, Map::class.java)
            config = GsonBuilder().setPrettyPrinting().create().toJson(configMap)

            log.info(config)

            val track = configMap["track"] as Map<String, String>

            val playbackUrl = URL(track["playbackUrl"])

            log.info("playbackUrl=$playbackUrl")

            if (playbackUrl.path.contains(".mp4")) {
                return loadBinaryFile(playbackUrl, info.tweetId, "mp4") to info
            } else {
                return loadM3u8Format(playbackUrl, info) to info
            }
        } catch (e: Exception) {
            if (e is FileNotFoundException && info.imageUrls.isNotEmpty()) {
                return null to info
            }

            throw e
        }
    }

    private fun loadM3u8Format(playbackUrl: URL, info: TweetInfo): File {
        val m3u8Master = loadText(playbackUrl)

        val masterLines = IOUtils.readLines(StringReader(m3u8Master))

        masterLines.forEach { l -> log.info("master line: {}", l) }

        val host = getHost(playbackUrl)

        val lastUrl = host + masterLines[masterLines.size - 1]

        log.info("lastUrl=$lastUrl")

        val m3u8Target = loadText(URL(lastUrl))

        val targetLines = IOUtils.readLines(StringReader(m3u8Target))

        val tsUrls = targetLines.stream()
                .filter { p -> p.startsWith("/") }
                .map { p -> host + p }
                .toList()

        tsUrls.forEach { u -> log.info("ts url: {}", u) }

        val fileInput = File(tempDir, info.tweetId + ".ts")

        FileOutputStream(fileInput).use { os ->
            tsUrls.forEach { url ->
                try {
                    val bytes = loadBytes(URL(url))
                    os.write(bytes)
                    log.info("write " + bytes.size + " bytes")
                } catch (e: IOException) {
                    throw RuntimeException(e)
                }
            }
        }

        val outputFile = File(tempDir, info.tweetId + ".mp4")
        convertFfmpeg(fileInput, outputFile)

        FileUtils.deleteQuietly(fileInput)

        return outputFile
    }

    private fun convertFfmpeg(input: File, output: File) {
        val ffmpeg = FFmpeg(ffmpegPath.path)
        val ffprobe = FFprobe(ffprobePath.path)

        val builder = FFmpegBuilder()
                .overrideOutputFiles(true)
                .setInput(input.path)     // Filename, or a FFmpegProbeResult
                .addOutput(output.path)   // Filename for the destination
                .setFormat("mp4")        // Format is inferred from filename, or can be set
                .setAudioCodec("copy")
                .setVideoCodec("copy")
                .setAudioBitStreamFilter("aac_adtstoasc")
                .done()

        val executor = FFmpegExecutor(ffmpeg, ffprobe)

        // Run a one-pass encode
        executor.createJob(builder).run()
    }

    private fun loadInfo(tweetUrl: URL): TweetInfo? {
        val content = loadText(tweetUrl)
        val doc = Jsoup.parse(content)
        val elem = doc.selectFirst("p.tweet-text")
        val tweetText = if (elem != null) {
            (elem.childNodes().find { it is TextNode } as TextNode?)?.wholeText ?: ""
        } else {
            ""
        }
        val container = doc.selectFirst(".AdaptiveMedia-container")
        val tweetImageUrls = if (container != null) {
            val images = container.select("img[data-aria-label-part]")
            if (images != null) {
                images.map { it.attr("src") }.filter { it != null }
            } else {
                emptyList()
            }
        } else {
            emptyList()
        }

        val mc = initJsUrlPattern.matcher(content)
        val idmc = tweetIdPattern.matcher(tweetUrl.toString())
        idmc.find()
        val tweetId = idmc.group(1)

        if (mc.find()) {
            val initJsUrl = mc.group(1)

            log.info("initJsUrl=$initJsUrl")

            val initJsContent = loadText(URL(initJsUrl))

            val mc2 = bearerTokenPattern.matcher(initJsContent)

            if (mc2.find()) {
                return TweetInfo(mc2.group(1), tweetId, tweetText, tweetImageUrls)
            }
        }

        return null
    }

    companion object {
        private val initJsUrlPattern = Pattern.compile("src=\\\"(http.+init.+\\.js)\\\"")
        private val bearerTokenPattern = Pattern.compile("t.a=\\\"(A[^\\\"]+)\\\"")
        private val tweetIdPattern = Pattern.compile("status/(\\d+)")

        private fun getHost(url: URL): String {
            return url.protocol + "://" + url.host
        }

        @Throws(IOException::class)
        private fun loadText(url: URL, headers: Map<String, String> = emptyMap(), post: Boolean = false): String {
            val conn = url.openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:62.0) Gecko/20100101")

            if (post) {
                conn.requestMethod = "POST"
            }

            for (key in headers.keys) {
                conn.setRequestProperty(key, headers[key])
            }

            var stream: InputStream? = null

            try {
                stream = conn.inputStream
                return IOUtils.toString(stream!!, "UTF-8")
            } finally {
                stream?.close()
            }
        }

        private fun loadBytes(url: URL, headers: Map<String, String> = emptyMap(), post: Boolean = false): ByteArray {
            val conn = url.openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:62.0) Gecko/20100101")

            if (post) {
                conn.requestMethod = "POST"
            }

            for (key in headers.keys) {
                conn.setRequestProperty(key, headers[key])
            }

            var stream: InputStream? = null

            try {
                stream = conn.inputStream
                return IOUtils.toByteArray(stream!!)
            } finally {
                stream?.close()
            }
        }
    }
}
