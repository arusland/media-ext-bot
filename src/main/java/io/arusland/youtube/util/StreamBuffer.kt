package io.arusland.youtube.util

import java.io.IOException
import java.io.InputStream

class StreamBuffer(val buffer: StringBuffer, val stream: InputStream) : Thread() {
    override fun run() {
        try {
            var nextChar: Int
            while (stream.read().also({ nextChar = it }) != -1) {
                buffer.append(nextChar.toChar())
            }
        } catch (e: IOException) {
        }
    }

    init {
        start()
    }
}
