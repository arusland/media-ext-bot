package io.arusland.youtube.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class StreamProcessExtractorTest {
    @Test
    fun parsesProgressAndStages() {
        val output = listOf(
            "[youtube] XbqFZMIidZI: Downloading webpage",
            "[download] Destination: /downloads/video.f137.mp4",
            "[download]   0.0% of   10.00MiB at  Unknown B/s ETA Unknown",
            "[download]  42.5% of   10.00MiB at    1.00MiB/s ETA 00:05\r",
            "[download] 100% of   10.00MiB in 00:00:10",
            "[download] Destination: /downloads/video.f140.m4a",
            "[download]  50.0% of ~  1.00GiB at    1.00MiB/s ETA 01:02:03 (frag 3/10)",
            "[Merger] Merging formats into \"/downloads/video.mp4\"",
        ).joinToString("\n")
        val events = mutableListOf<String>()
        val callback = object : DownloadProgressCallback {
            override fun onProgressUpdate(progress: Float, etaInSeconds: Long) {
                events.add("$progress/$etaInSeconds")
            }

            override fun onStage(stage: String) {
                events.add(stage)
            }
        }

        StreamProcessExtractor(StringBuffer(), output.byteInputStream(), callback).join()

        assertEquals(
            listOf(
                "downloading", "0.0/-1", "42.5/5", "100.0/-1",
                "downloading", "50.0/3723", "merging"
            ),
            events
        )
    }
}
