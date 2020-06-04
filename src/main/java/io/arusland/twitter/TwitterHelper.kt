package io.arusland.twitter

import com.fasterxml.jackson.databind.ObjectMapper
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
import java.lang.IllegalStateException
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern
import kotlin.streams.toList

data class TweetInfo(val bearerToken: String, val tweetId: String, val text: String, val imageUrls: List<String>)

class TwitterHelper(private val tempDir: File, private val ffmpegPath: File, private val ffprobePath: File) {

    fun downloadMediaFrom(tweetUrl: URL): Pair<File?, TweetInfo> {
        val headers = mutableMapOf<String, String>()
        val info = run {
            val info = loadInfo(tweetUrl) ?: loadInfoNew(tweetUrl)
            ?: throw IllegalStateException("Tweet parsing failed")
            headers["authorization"] = "Bearer " + info.bearerToken
            val tokenJson = loadText(URL("https://api.twitter.com/1.1/guest/activate.json"), headers, true)

            log.info(tokenJson)

            val tokenMap = objectMapper.readValue(tokenJson, Map::class.java) as Map<String, String>

            val guestToken = tokenMap["guest_token"]!!

            log.info("guest_token=$guestToken")

            headers["x-guest-token"] = guestToken

            val timelineJson = loadText(makeTimelineUrl(info.tweetId), headers)
            getTopicInfo(timelineJson, info)
        }
        val tweetId = info.tweetId

        try {
            var config = loadText(URL("https://api.twitter.com/1.1/videos/tweet/config/$tweetId.json"),
                    headers, false)
            val configMap = objectMapper.readValue(config, Map::class.java)
            val track = configMap["track"] as Map<String, String>
            val playbackUrl = URL(track["playbackUrl"])

            log.info("playbackUrl=$playbackUrl")

            if (playbackUrl.path.contains(".mp4")) {
                return loadBinaryFile(playbackUrl, tweetId, "mp4") to info
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

    private fun getTopicInfo(timelineJson: String, info: TweetInfo): TweetInfo {
        val map = objectMapper.readValue(timelineJson, Map::class.java)

        val globalObjects = map["globalObjects"] as Map<String, Any>
        val tweets = globalObjects["tweets"] as Map<String, Any>
        val topic = tweets[info.tweetId] as Map<String, Any>
        log.info("topic: {}", objectMapper.writeValueAsString(topic))
        val fullText = topic["full_text"] as String
        val range = topic["display_text_range"] as List<Int>
        log.info("!!! range: {}", range)
        val endIndex = fullText.lastIndexOf("https://t.co")
        val text = fullText.substring(range[0], Math.max(range[1] + 1, endIndex))
        log.info("!!!! text: {}<<<", text)

        val entities = topic["entities"] as Map<String, Any>?
        val imageUrls = if (entities != null) {
            val media = entities["media"] as List<Map<String, Any>>
            media.map { it["media_url_https"] as String }
        } else {
            emptyList()
        }

        return info.copy(text = text, imageUrls = imageUrls)
    }

    private fun makeTimelineUrl(tweetId: String): URL {
        return URL("https://api.twitter.com/2/timeline/conversation/$tweetId.json?include_profile_interstitial_type=1&include_blocking=1&include_blocked_by=1&include_followed_by=1&include_want_retweets=1&include_mute_edge=1&include_can_dm=1&include_can_media_tag=1&skip_status=1&cards_platform=Web-12&include_cards=1&include_ext_alt_text=true&include_reply_count=1&tweet_mode=extended&include_entities=true&include_user_entities=true&include_ext_media_color=true&include_ext_media_availability=true&send_error_codes=true&simple_quoted_tweet=true&count=20&ext=mediaStats%2ChighlightedLabel%2CcameraMoment&include_quote_count=true")
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

    fun loadInfoNew(tweetUrl: URL): TweetInfo? {
        log.debug("V2: Loading tweet content by url: {}", tweetUrl)
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

        val mc = mainJsUrlPattern.matcher(content)
        val idmc = tweetIdPattern.matcher(tweetUrl.toString())
        idmc.find()
        val tweetId = idmc.group(1)

        if (mc.find()) {
            val initJsUrl = mc.group(1)

            log.info("initJsUrl=$initJsUrl")

            val initJsContent = loadText(URL(initJsUrl))

            val mc2 = bearerTokenPattern2.matcher(initJsContent)

            if (mc2.find()) {
                return TweetInfo(mc2.group(1), tweetId, tweetText, tweetImageUrls)
            }
        }

        return null
    }

    fun loadInfo(tweetUrl: URL): TweetInfo? {
        log.debug("V1: Loading tweet content by url: {}", tweetUrl)
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
        private val log = LoggerFactory.getLogger(TwitterHelper::class.java)
        private const val USER_AGENT = "Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:76.0) Gecko/20100101 Firefox/76.0"
        private val initJsUrlPattern = Pattern.compile("src=\\\"(http.+init.+\\.js)\\\"")
        private val mainJsUrlPattern = Pattern.compile("src=\\\"(http.+/main.+\\.js)\\\"")
        private val bearerTokenPattern = Pattern.compile("t.a=\\\"(A[^\\\"]+)\\\"")
        private val bearerTokenPattern2 = Pattern.compile("c=\\\"(A[^\\\"]+)\\\"")
        private val tweetIdPattern = Pattern.compile("status/(\\d+)")
        private val objectMapper = ObjectMapper()


        private fun getHost(url: URL): String {
            return url.protocol + "://" + url.host
        }

        @Throws(IOException::class)
        private fun loadText(url: URL, headers: Map<String, String> = emptyMap(), post: Boolean = false): String {
            val conn = url.openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Referer", "https://twitter.com/")
            conn.setRequestProperty("Host", url.host)

            if (post) {
                conn.requestMethod = "POST"
            }

            for (key in headers.keys) {
                conn.setRequestProperty(key, headers[key])
            }

            var stream: InputStream? = null

            val response = try {
                stream = conn.inputStream
                IOUtils.toString(stream!!, "UTF-8")
            } finally {
                stream?.close()
            }

            try {
                conn.disconnect()
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
            }

            return response
        }

        private fun loadBytes(url: URL, headers: Map<String, String> = emptyMap(), post: Boolean = false): ByteArray {
            val conn = url.openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Referer", "https://twitter.com/")
            conn.setRequestProperty("Host", url.host)

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
