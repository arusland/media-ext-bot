package io.arusland.util

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule

object JsonUtils {
    private val objectMapper = ObjectMapper().registerModule(KotlinModule())

    fun <T> parse(content: String, clazz: Class<T>): T = objectMapper.readValue(content, clazz)

    fun toJson(obj: Any): String = objectMapper.writeValueAsString(obj)

    fun toPrettyJson(obj: Any): String = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(obj)
}
