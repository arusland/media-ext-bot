package io.arusland.util

import net.bramp.ffmpeg.FFmpeg
import net.bramp.ffmpeg.FFmpegExecutor
import net.bramp.ffmpeg.FFprobe
import net.bramp.ffmpeg.builder.FFmpegBuilder
import net.bramp.ffmpeg.progress.ProgressListener
import java.io.File

class FfMpegUtils(private val ffMpegPath: String, private val ffProbePath: String) {
    private val ffmpegPath = File(ffMpegPath)
    private val ffprobePath = File(ffProbePath)

    /**
     * @param progress receives conversion progress in percents (0..100), called from ffmpeg's progress thread
     */
    fun convert(
        input: File,
        output: File,
        videoCodec: String = "copy",
        audioCodec: String = "copy",
        progress: ((Float) -> Unit)? = null
    ) {
        val ffmpeg = FFmpeg(ffmpegPath.path)
        val ffprobe = FFprobe(ffprobePath.path)

        val builder = FFmpegBuilder()
            .overrideOutputFiles(true)
            .setInput(input.path)     // Filename, or a FFmpegProbeResult
            .done()
            .addOutput(output.path)   // Filename for the destination
            .setFormat("mp4")        // Format is inferred from filename, or can be set
            .setAudioCodec(audioCodec)
            .setVideoCodec(videoCodec)
          //  .setAudioBitStreamFilter("aac")
            .done()

        val executor = FFmpegExecutor(ffmpeg, ffprobe)

        val durationNs = if (progress != null) probeDurationNs(ffprobe, input) else 0L

        // Run a one-pass encode
        if (progress != null && durationNs > 0) {
            executor.createJob(builder, ProgressListener { p ->
                if (p.out_time_ns > 0) {
                    progress((p.out_time_ns.toDouble() / durationNs * 100).coerceIn(0.0, 100.0).toFloat())
                }
            }).run()
        } else {
            executor.createJob(builder).run()
        }
    }

    fun removeAudio(input: File, output: File) {
        val ffmpeg = FFmpeg(ffmpegPath.path)
        val ffprobe = FFprobe(ffprobePath.path)

        val builder = FFmpegBuilder()
            .overrideOutputFiles(true)
            .setInput(input.path)
            .done()
            .addOutput(output.path)
            .setFormat("mp4")
            .disableAudio()
            .setVideoCodec("copy")
            .done()

        val executor = FFmpegExecutor(ffmpeg, ffprobe)

        executor.createJob(builder).run()
    }

    private fun probeDurationNs(ffprobe: FFprobe, input: File): Long = try {
        (ffprobe.probe(input.path).format.duration * 1_000_000_000).toLong()
    } catch (e: Exception) {
        0L
    }
}
