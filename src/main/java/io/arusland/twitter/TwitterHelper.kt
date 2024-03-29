package io.arusland.twitter

import com.fasterxml.jackson.databind.ObjectMapper
import io.arusland.util.FfMpegUtils
import io.arusland.util.loadBinaryFile
import io.arusland.youtube.YoutubeHelper
import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils
import org.jsoup.Jsoup
import org.jsoup.nodes.TextNode
import org.slf4j.LoggerFactory
import java.io.*
import java.net.CookieHandler
import java.net.CookieManager
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern

data class TweetInfo(val bearerToken: String, val tweetId: String, val text: String, val imageUrls: List<String>)

class TwitterHelper(
    private val tempDir: File,
    private val ffMpegUtils: FfMpegUtils,
    private val youtubeHelper: YoutubeHelper
) {
    fun isTwitterUrl(url: URL): Boolean = url.toString().startsWith("https://twitter.com")

    fun downloadMediaFrom(tweetUrl: URL): Pair<File?, TweetInfo> {
        val info = loadInfoNew(tweetUrl) ?: throw IllegalStateException("Tweet parsing failed")
        val file = youtubeHelper.downloadVideo(tweetUrl)

        return file to info
    }

    private fun getTopicInfo(timelineJson: String, info: TweetInfo): TweetInfo {
        val map = objectMapper.readValue(timelineJson, Map::class.java)

        val globalObjects = map["globalObjects"] as Map<String, Any>
        val tweets = globalObjects["tweets"] as Map<String, Any>
        val topic = tweets[info.tweetId] as Map<String, Any>
        log.info("topic: {}", objectMapper.writeValueAsString(topic))
        val fullText = topic["full_text"] as String
        val range = topic["display_text_range"] as List<Int>
        log.info("got range: {}", range)
        val endIndex = fullText.lastIndexOf("https://t.co")
        val text = fullText.substring(range[0], if (endIndex > 0) endIndex else range[1])
        log.info("got text: {}<<<", text)

        val entities = topic["extended_entities"] as Map<String, Any>?
        val imageUrls = if (entities != null) {
            val media = entities["media"] as List<Map<String, Any>>
            media.map { it["media_url_https"] as String }
        } else {
            emptyList()
        }

        return info.copy(text = text, imageUrls = imageUrls)
    }

    @Deprecated("Not working anymore")
    private fun downloadMediaFromOld(tweetUrl: URL): Pair<File?, TweetInfo> {
        val headers = mutableMapOf<String, String>()
        val info = run {
            val info = loadInfo(tweetUrl) ?: loadInfoNew(tweetUrl)
            ?: throw IllegalStateException("Tweet parsing failed")
            headers["authorization"] =
                "Bearer AAAAAAAAAAAAAAAAAAAAAPYXBAAAAAAACLXUNDekMxqa8h%2F40K4moUkGsoc%3DTYfbDKbT3jJPCEVnMYqilB28NHfOPqkca3qaAxGfsyKCs0wRbw"
            val tokenJson = loadText(URL("https://api.twitter.com/1.1/guest/activate.json"), headers, true)

            val tokenMap = objectMapper.readValue(tokenJson, Map::class.java) as Map<String, String>

            val guestToken = tokenMap["guest_token"]!!

            log.info("guest_token=$guestToken")

            headers["x-guest-token"] = guestToken

            val timelineJson = loadText(makeTimelineUrl(info.tweetId), headers)
            getTopicInfo(timelineJson, info)
        }
        val tweetId = info.tweetId

        try {
            var config = loadText(
                URL("https://api.twitter.com/1.1/videos/tweet/config/$tweetId.json"),
                headers, false
            )
            val configMap = objectMapper.readValue(config, Map::class.java)
            val track = configMap["track"] as Map<String, String>
            val playbackUrl = URL(track["playbackUrl"])

            log.info("playbackUrl=$playbackUrl")

            return if (playbackUrl.path.contains(".mp4")) {
                loadBinaryFile(playbackUrl, tweetId, "mp4") to info
            } else {
                loadM3u8Format(playbackUrl, info) to info
            }
        } catch (e: Exception) {
            if (e is FileNotFoundException && info.imageUrls.isNotEmpty()) {
                return null to info
            }

            throw e
        }
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
            .filter { p -> p.startsWith("/") || p.startsWith(MEDIA_HEADER) }
            .map { p -> cleanUrl(p) }
            .map { p -> host + p }
            .toList()

        tsUrls.forEach { u -> log.info("ts url: {}", u) }

        val fileInput = File(tempDir, info.tweetId + ".ts")

        FileOutputStream(fileInput).use { os ->
            tsUrls.forEach { url ->
                try {
                    val bytes = loadBytes(URL(url))
                    os.write(bytes)
                    log.info("write {} bytes from {}", bytes.size, url)
                } catch (e: IOException) {
                    throw RuntimeException(e)
                }
            }
        }

        val outputFile = File(tempDir, info.tweetId + ".mp4")
        ffMpegUtils.convert(fileInput, outputFile)

        FileUtils.deleteQuietly(fileInput)

        return outputFile
    }

    private fun cleanUrl(url: String): String {
        if (url.startsWith(MEDIA_HEADER) && url.endsWith("\"")) {
            return url.substring(MEDIA_HEADER.length, url.length - 1)
        }

        return url
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
        private const val MEDIA_HEADER = "#EXT-X-MAP:URI=\""
        private val initJsUrlPattern = Pattern.compile("src=\\\"(http.+init.+\\.js)\\\"")
        private val mainJsUrlPattern = Pattern.compile("src=\\\"(http[^\\\"]+/main.+\\.js)\\\"")
        private val bearerTokenPattern = Pattern.compile("t.a=\\\"(A[^\\\"]+)\\\"")
        private val bearerTokenPattern2 = Pattern.compile("Web-12.+\\w=\\\"(A[^\\\"]+)\\\"")
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

    init {
        CookieHandler.setDefault(CookieManager())
    }
}
