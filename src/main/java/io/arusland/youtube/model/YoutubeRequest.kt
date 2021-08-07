package io.arusland.youtube.model

/**
 * Youtube request
 */
data class YoutubeRequest(
    private val url: String?,
    val directory: String? = null
) {
    private val options: MutableMap<String, String?> = HashMap()

    val option: Map<String, String?>
        get() = options

    fun setOption(key: String) {
        options[key] = null
    }

    fun setOption(key: String, value: String?) {
        options[key] = value
    }

    fun setOption(key: String, value: Int) {
        options[key] = value.toString()
    }

    fun buildOptions(): String {
        val builder = StringBuilder()

        // Set Url
        if (url != null) builder.append("$url ")

        // Build options strings
        val it: MutableIterator<*> = options.entries.iterator()
        while (it.hasNext()) {
            val (key, value1) = it.next() as Map.Entry<*, *>
            val name = key as String
            var value = value1 as String?
            if (value == null) value = ""
            val optionFormatted = String.format("--%s %s", name, value).trim { it <= ' ' }
            builder.append("$optionFormatted ")
            it.remove()
        }

        return builder.toString().trim { it <= ' ' }
    }
}
