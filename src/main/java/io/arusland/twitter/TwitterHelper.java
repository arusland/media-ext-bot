package io.arusland.twitter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.bramp.ffmpeg.FFmpeg;
import net.bramp.ffmpeg.FFmpegExecutor;
import net.bramp.ffmpeg.FFprobe;
import net.bramp.ffmpeg.builder.FFmpegBuilder;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.stream.Collectors.toList;

public class TwitterHelper {
    private final Logger log = LoggerFactory.getLogger(getClass());
    private static Pattern initJsUrlPattern = Pattern.compile("src=\\\"(http.+init.+\\.js)\\\"");
    private static Pattern bearerTokenPattern = Pattern.compile("t.a=\\\"(A[^\\\"]+)\\\"");
    private static Pattern tweetIdPattern = Pattern.compile("status/(\\d+)");
    private final File tempDir;
    private final File ffmpegPath;
    private final File ffprobePath;

    public TwitterHelper(File tempDir, File ffmpegPath, File ffprobePath) {
        this.tempDir = tempDir;
        this.ffmpegPath = ffmpegPath;
        this.ffprobePath = ffprobePath;
    }

    public File downloadMediaFrom(URL tweetUrl) throws IOException {
        final TweetInfo info = loadInfo(tweetUrl);
        Map<String, String> headers = new HashMap<String, String>() {{
            put("authorization", "Bearer " + info.bearerToken);
        }};

        String tokenJson = loadText(new URL("https://api.twitter.com/1.1/guest/activate.json"), headers, true);

        log.info(tokenJson);

        Map<String, String> tokenMap = new Gson().fromJson(tokenJson, Map.class);

        String guestToken = tokenMap.get("guest_token");

        log.info("guest_token=" + guestToken);

        headers.put("x-guest-token", guestToken);

        String config = loadText(new URL("https://api.twitter.com/1.1/videos/tweet/config/" + info.tweetId + ".json"), headers, false);
        Map<String, Object> configMap = new Gson().fromJson(config, Map.class);
        config = new GsonBuilder().setPrettyPrinting().create().toJson(configMap);

        log.info(config);

        Map<String, String> track = (Map<String, String>) configMap.get("track");

        URL playbackUrl = new URL(track.get("playbackUrl"));

        log.info("playbackUrl=" + playbackUrl);

        String m3u8Master = loadText(playbackUrl);

        List<String> masterLines = IOUtils.readLines(new StringReader(m3u8Master));

        masterLines.forEach(l -> log.info("master line: {}", l));

        String host = getHost(playbackUrl);

        String lastUrl = host + masterLines.get(masterLines.size() - 1);

        log.info("lastUrl=" + lastUrl);

        String m3u8Target = loadText(new URL(lastUrl));

        List<String> targetLines = IOUtils.readLines(new StringReader(m3u8Target));

        List<String> tsUrls = targetLines.stream()
                .filter(p -> p.startsWith("/"))
                .map(p -> host + p)
                .collect(toList());

        tsUrls.forEach(u -> log.info("ts url: {}", u));

        File fileInput = new File(tempDir, info.tweetId + ".ts");

        try (OutputStream os = new FileOutputStream(fileInput)) {
            tsUrls.forEach(url -> {
                try {
                    byte[] bytes = loadBytes(new URL(url));
                    os.write(bytes);
                    log.info("write " + bytes.length + " bytes");
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }

        File outputFile = new File(tempDir, info.tweetId + ".mp4");
        convertFfmpeg(fileInput, outputFile);

        FileUtils.deleteQuietly(fileInput);

        return outputFile;
    }

    private void convertFfmpeg(File input, File output) throws IOException {
        FFmpeg ffmpeg = new FFmpeg(ffmpegPath.getPath());
        FFprobe ffprobe = new FFprobe(ffprobePath.getPath());

        FFmpegBuilder builder = new FFmpegBuilder()
                .overrideOutputFiles(true)
                .setInput(input.getPath())     // Filename, or a FFmpegProbeResult
                .addOutput(output.getPath())   // Filename for the destination
                .setFormat("mp4")        // Format is inferred from filename, or can be set
                .setAudioCodec("copy")
                .setVideoCodec("copy")
                .setAudioBitStreamFilter("aac_adtstoasc")
                .done();

        FFmpegExecutor executor = new FFmpegExecutor(ffmpeg, ffprobe);

        // Run a one-pass encode
        executor.createJob(builder).run();
    }

    private static String getHost(URL url) {
        return url.getProtocol() + "://" + url.getHost();
    }

    private TweetInfo loadInfo(URL tweetUrl) throws IOException {
        String content = loadText(tweetUrl);

        Matcher mc = initJsUrlPattern.matcher(content);

        Matcher idmc = tweetIdPattern.matcher(tweetUrl.toString());

        idmc.find();

        String tweetId = idmc.group(1);

        if (mc.find()) {
            String initJsUrl = mc.group(1);

            log.info("initJsUrl=" + initJsUrl);

            String initJsContent = loadText(new URL(initJsUrl));

            Matcher mc2 = bearerTokenPattern.matcher(initJsContent);

            if (mc2.find()) {
                return new TweetInfo(mc2.group(1), tweetId);
            }
        }

        return null;
    }

    private static String loadText(URL url) throws IOException {
        return loadText(url, Collections.<String, String>emptyMap(), false);
    }

    private static String loadText(URL url, Map<String, String> headers, boolean post) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:62.0) Gecko/20100101");

        if (post) {
            conn.setRequestMethod("POST");
        }

        for (String key : headers.keySet()) {
            conn.setRequestProperty(key, headers.get(key));
        }

        InputStream stream = null;

        try {
            stream = conn.getInputStream();
            return IOUtils.toString(stream, "UTF-8");
        } finally {
            if (stream != null) {
                stream.close();
            }
        }
    }

    private static byte[] loadBytes(URL url) throws IOException {
        return loadBytes(url, Collections.emptyMap(), false);
    }

    private static byte[] loadBytes(URL url, Map<String, String> headers, boolean post) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:62.0) Gecko/20100101");

        if (post) {
            conn.setRequestMethod("POST");
        }

        for (String key : headers.keySet()) {
            conn.setRequestProperty(key, headers.get(key));
        }

        InputStream stream = null;

        try {
            stream = conn.getInputStream();
            return IOUtils.toByteArray(stream);
        } finally {
            if (stream != null) {
                stream.close();
            }
        }
    }
}
