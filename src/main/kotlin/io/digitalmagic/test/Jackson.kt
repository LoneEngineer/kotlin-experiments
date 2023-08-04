package io.digitalmagic.test

import com.fasterxml.jackson.annotation.*
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.SingletonSupport
import com.fasterxml.jackson.module.kotlin.jsonMapper

//import org.zalando.jackson.datatype.money.MoneyModule

object Jackson {
    val configureObjectMapper: ObjectMapper.() -> Unit = {
        registerModule(Jdk8Module())
            .registerModule(JavaTimeModule())
            .registerModule(KotlinModule.Builder().configure(KotlinFeature.SingletonSupport, true).build())
//            .registerModule(MoneyModule())
            .setSerializationInclusion(JsonInclude.Include.ALWAYS)
            .enable(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN)
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS)
            .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
            .setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE)
            .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY)

        // instruct BigDecimal to be serialized as a string
        // configOverride(BigDecimal::class.java).setFormat(JsonFormat.Value.forShape(JsonFormat.Shape.STRING))
    }

    @PublishedApi
    internal val objectMapper = jsonMapper().apply(configureObjectMapper)

    fun <T> convert(value: Any?, clazz: Class<T>): T = objectMapper.convertValue(value, clazz)
    fun <T> parse(str: String, clazz: Class<T>): T = objectMapper.readValue(str, clazz)
    fun <T> parse(str: String, ref: TypeReference<T>): T = objectMapper.readValue(str, ref)
    fun <T> parser(clazz: Class<T>): (String) -> T = { objectMapper.readValue(it, clazz) }
    fun <T> stringify(value: T): String = objectMapper.writeValueAsString(value)
    fun <T> parseJsonNode(node: JsonNode, clazz: Class<T>) = objectMapper.treeToValue(node, clazz)
    fun toJsonNode(value: Any) = objectMapper.valueToTree<JsonNode>(value)
}
