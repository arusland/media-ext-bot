package io.arusland.util

import net.bramp.ffmpeg.FFmpeg
import net.bramp.ffmpeg.FFmpegExecutor
import net.bramp.ffmpeg.FFprobe
import net.bramp.ffmpeg.builder.FFmpegBuilder
import java.io.File

class FfMpegUtils(private val ffMpegPath: String, private val ffProbePath: String) {
    private val ffmpegPath = File(ffMpegPath)
    private val ffprobePath = File(ffProbePath)

    fun convert(input: File, output: File, videoCodec: String = "copy", audioCodec: String = "copy") {
        val ffmpeg = FFmpeg(ffmpegPath.path)
        val ffprobe = FFprobe(ffprobePath.path)

        val builder = FFmpegBuilder()
            .overrideOutputFiles(true)
            .setInput(input.path)     // Filename, or a FFmpegProbeResult
            .addOutput(output.path)   // Filename for the destination
            .setFormat("mp4")        // Format is inferred from filename, or can be set
            .setAudioCodec(audioCodec)
            .setVideoCodec(videoCodec)
            .setAudioBitStreamFilter("aac_adtstoasc")
            .done()

        val executor = FFmpegExecutor(ffmpeg, ffprobe)

        // Run a one-pass encode
        executor.createJob(builder).run()
    }
}
