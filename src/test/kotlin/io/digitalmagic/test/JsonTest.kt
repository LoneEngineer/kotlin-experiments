package io.digitalmagic.test

import com.fasterxml.jackson.annotation.*
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.exc.InvalidTypeIdException
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.util.NoSuchElementException
import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import java.nio.file.WatchEvent
import kotlin.test.*

data class Value(@JsonValue val x: String, val b: Boolean = false) {
    @JsonCreator
    constructor(x: String) : this(x, false)
}

data class B(val x: Int)
data class C(@JsonUnwrapped val b: B, val s: String)
data class P(val x: Int, val s: String)

data class JsonDTO(@JsonValue val map: Map<String, Any?>): Map<String, Any?> by map {
    val country: String by map
    val city: String? by map
    val age: Int by map
}

data class JsonDTOWithDefault(@JsonValue val map: Map<String, Any?>): Map<String, Any?> by map {
    private val internalMap = map.withDefault { _ -> null }
    val country: String by internalMap
    val city: String? by internalMap
    val age: Int by internalMap
}

data class MutableJsonDTO(@JsonValue val map: MutableMap<String, Any?> = mutableMapOf()) {
    var country: String by map
    var city: String? by map
    var age: Int by map
}

data class MutableJsonDTOWithDefault(@JsonValue val map: MutableMap<String, Any?> = mutableMapOf()) {
    private val internalMap = map.withDefault { _ -> null }
    var country: String by map
    var city: String? by internalMap
    var age: Int by map
}

@Serializable
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = Base.ChildA::class, name = "A"),
    JsonSubTypes.Type(value = Base.ChildB::class, name = "B")
)
sealed class Base {
    @Serializable
    data class ChildA(val type: String): Base()
    @Serializable
    data class ChildB(val data: Int): Base()
}

data class Container1(val items: List<Base>)
data class Container2(val items: List<Base>, val moreData: Boolean)
data class Container3<T>(val items: List<T>, val moreData: Boolean)

@Serializable
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
sealed class A {
    @JsonTypeName("B")
    @Serializable
    object B: A()
    @JsonTypeName("C")
    @Serializable
    object C: A()

    @Serializable
    sealed class D(): A()
    @JsonTypeName("DA")
    @Serializable
    object DA: D()

    @JsonTypeName("DB")
    @Serializable
    object DB: D()
}

object X

class JsonTest {

    @Test
    fun deserializeDeepHierarchy() {
        val da: A = A.DA

        run {
            val json = Jackson.stringify(da)
            assertEquals(da, Jackson.parse(json, A::class.java))
        }
        run {
            val json = Json.encodeToString(da)
            assertEquals(da, Json.decodeFromString<A>(json))
        }
    }

    @Test
    fun deserializationOfPlainObjectRequiresSpecialSetupInJackson2_11_plus() {
        val x1 = X
        val x2 = Jackson.parse(Jackson.stringify(x1), X::class.java)

        assertFailsWith<AssertionError> {
            assertEquals(X, jacksonObjectMapper().apply(Jackson.configureObjectMapper).readValue(Jackson.stringify(x1), X::class.java))
        }
        assertEquals(X, x1)
        assertEquals(X, x2)
        assertEquals(x1, x2)
    }

    @Test
    fun deserializationOfObjectRequiresSpecialSetupInJackson2_11_plus() {
        val x1 = A.B
        val x2 = Jackson.parse(Jackson.stringify(x1), A::class.java)

        assertFailsWith<AssertionError> {
            assertEquals(A.B, jacksonObjectMapper().apply(Jackson.configureObjectMapper).readValue(Jackson.stringify(x1), A::class.java))
        }
        assertEquals(A.B, x1)
        assertEquals(A.B, x2)
        assertEquals(x1, x2)
    }

    @Test
    fun deserializationOfObjectWithKotlinx() {
        val x1: A = A.B
        val x2: A = Json.decodeFromString<A>(Json.encodeToString(x1))

        assertEquals(A.B, x1)
        assertEquals(A.B, x2)
        assertEquals(x1, x2)
    }

    @Test
    fun serializePolymorphicListInJackson() {
        fun aList(): List<Base> = listOf(Base.ChildA("child-a"), Base.ChildB(10))
        fun container3(): Container3<Base> = Container3(aList(), true)

        run {
            val jsonContainer1 = Jackson.stringify(aList())
            assertTrue(jsonContainer1.contains("\"type\" : \"child-a\""))
            assertTrue(jsonContainer1.contains("\"data\" : 10"))
            // type-info is missing - jackson's 'feature'
            assertFalse(jsonContainer1.contains("\"type\" : \"A\""))
            assertFalse(jsonContainer1.contains("\"type\" : \"B\""))
        }

        run {
            val jsonContainer1 = Jackson.stringify(Container1(aList()))
            assertTrue(jsonContainer1.contains("\"type\" : \"child-a\""))
            assertTrue(jsonContainer1.contains("\"data\" : 10"))
            // type-info
            assertTrue(jsonContainer1.contains("\"type\" : \"A\""))
            assertTrue(jsonContainer1.contains("\"type\" : \"B\""))
        }

        run {
            val jsonContainer2 = Jackson.stringify(Container2(aList(), true))
            assertTrue(jsonContainer2.contains("\"type\" : \"child-a\""))
            assertTrue(jsonContainer2.contains("\"data\" : 10"))
            // type-info
            assertTrue(jsonContainer2.contains("\"type\" : \"A\""))
            assertTrue(jsonContainer2.contains("\"type\" : \"B\""))
        }

        run {
            val jsonContainer2 = Jackson.stringify(container3())
            assertTrue(jsonContainer2.contains("\"type\" : \"child-a\""))
            assertTrue(jsonContainer2.contains("\"data\" : 10"))
            // type-info is missing - jackson's 'feature'
            assertFalse(jsonContainer2.contains("\"type\" : \"A\""))
            assertFalse(jsonContainer2.contains("\"type\" : \"B\""))
        }
    }

    @Test
    fun jsonValueWithMultipleFields() {
        val x = Value("ho", true)
        val jsonrep = Jackson.stringify(x)
        assertEquals("\"ho\"", jsonrep)

        val y = Jackson.parse(jsonrep, Value::class.java)
        assertEquals(x.x, y.x)
        assertFalse(y.b)
    }

    @Test
    fun unwrappedDoesNotWorkWithDataClass() {
        val example = C(B(42), "yes")
        val analogue = P(42, "yes")
        val jsonrep = Jackson.stringify(example)
        assertEquals(Jackson.stringify(analogue), jsonrep)

        /*
        end-up with:
com.fasterxml.jackson.databind.exc.InvalidDefinitionException: Cannot define Creator property "b" as `@JsonUnwrapped`: combination not yet supported
 at [Source: (String)"{
  "x" : 42,
  "s" : "yes"
}"; line: 1, column: 1]
         */
        assertFailsWith<com.fasterxml.jackson.databind.exc.InvalidDefinitionException> {
            val parsed = Jackson.parse(jsonrep, C::class.java)
            assertEquals(example, parsed)
        }
    }

    @Test
    fun clashOnDiscriminatorField() {
        val b = Base.ChildB(42)
        val jsonB = Jackson.stringify(b)
        val newB = Jackson.parse(jsonB, Base::class.java)
        assertEquals(true, newB is Base.ChildB)
        assertEquals(42, (newB as Base.ChildB).data)

        val a = Base.ChildA("foo")
        val jsonA = Jackson.stringify(a)
        val newA = Jackson.parse(jsonA, Base::class.java)
        assertEquals(true, newA is Base.ChildA)
        assertEquals("foo", (newA as Base.ChildA).type)

        assertTrue(jsonA.contains("\"type\" : \"A\""))
        assertTrue(jsonA.contains("\"type\" : \"foo\""))
    }

    @Test
    fun sealedHierarchyWithKotlinSerialization() {
        val b: Base = Base.ChildB(42)
        val jsonB = Json.encodeToString(b)
        val newB = Json.decodeFromString<Base>(jsonB)
        assertEquals(true, newB is Base.ChildB)
        assertEquals(42, (newB as Base.ChildB).data)

        val a: Base = Base.ChildA("foo")
        assertFailsWith<IllegalStateException> {
            val jsonA = Json.encodeToString(a)
        }
        //val newA = Json.decodeFromString<Base>(jsonA)
        //assertEquals(true, newA is Base.ChildA)
        //assertEquals("foo", (newA as Base.ChildA).type)
    }

    @Test
    fun representJsonDtoAsMap() {
        val result = Jackson.parse(
            """{
                  "city" : "Zero",
                  "street" : "Ground",
                  "age" : 100500,
                  "country": "EE",
                  "flat" : null,
                  "value" : { "amount" : 10.02, "currency": "EUR" } 
                }""".trimIndent(), JsonDTO::class.java
        )
        assertEquals("EE", result.country)
        assertEquals(100500, result.age)
        assertEquals("Zero", result.city)
        assertEquals("Ground", result.map["street"])
        assertEquals("Ground", result["street"])
        assertEquals(null, result.map["district"])
        assertFalse { result.containsKey("district") }
        assertEquals(null, result.map["flat"])
        assertTrue { result.containsKey("flat") }
        assertEquals("EUR", (result.map["value"] as Map<String, Any?>)["currency"])

        val result2 = Jackson.parse(
            """{
                  "age" : 100500,
                  "country": "EE"
                }""".trimIndent(), JsonDTO::class.java
        )

        assertFailsWith<NoSuchElementException> {
            assertEquals("Loop", result2.city)
        }

        val result3 = Jackson.parse(
            """{
                  "age" : 100500,
                  "country": "EE"
                }""".trimIndent(), JsonDTOWithDefault::class.java
        )

        assertEquals("EE", result.country)
        assertEquals(100500, result.age)
        assertEquals(null, result3.city)
        assertTrue{ result.containsKey("value") }
        assertEquals(null, result3["value"])

        val dto1 = with(
            MutableJsonDTO(
                mutableMapOf(
                    "city" to "Qux",
                    "street" to "Boo"
                )
            )
        ) {
            country = "Bar"
            city = "Foo"
            age = 42
            this
        }

        val result4 = Jackson.parse(Jackson.stringify(dto1), MutableJsonDTO::class.java)
        assertEquals(42, result4.age)
        assertEquals("Bar", result4.country)
        assertEquals("Foo", result4.city)
        assertEquals("Boo", result4.map["street"])

        val dto2 = MutableJsonDTO()

        val result5 = Jackson.parse(Jackson.stringify(dto2), MutableJsonDTO::class.java)
        assertFailsWith<NoSuchElementException> {
            assertEquals(42, result5.age)
        }
        assertFailsWith<NoSuchElementException> {
            assertEquals("Bar", result5.country)
        }
        assertFailsWith<NoSuchElementException> {
            assertEquals("Bar", result5.city)
        }

        val result6 = Jackson.parse(Jackson.stringify(dto2), MutableJsonDTOWithDefault::class.java)
        assertFailsWith<NoSuchElementException> {
            assertEquals(42, result6.age)
        }
        assertFailsWith<NoSuchElementException> {
            assertEquals("Bar", result6.country)
        }
        assertEquals(null, result6.city)
    }

    @Serializable
    data class DataWithBool(val abc: String, val xyz: Boolean)
    @Test
    fun defaultBoolInJackson() {
        assertEquals(true, Jackson.parse("""
                { "abc": "48kj3h43hgo5i3", "xyz": true }
            """.trimIndent(), DataWithBool::class.java).xyz)
        assertFailsWith(JsonMappingException::class) {
            Jackson.parse("""
                { "abc": "48kj3h43hgo5i3", "xyz": "ok" }
            """.trimIndent(), DataWithBool::class.java)
        }
        assertFailsWith(JsonMappingException::class) {
            val obj = Jackson.parse("""
                { "abc": "48kj3h43hgo5i3" }
            """.trimIndent(), DataWithBool::class.java)
            assertEquals(false, obj.xyz)
        }
    }

    @Test
    fun defaultBoolInKotlinx() {
        assertEquals(true, Json.decodeFromString<DataWithBool>("""
                { "abc": "48kj3h43hgo5i3", "xyz": true }
            """.trimIndent()).xyz)
        assertFailsWith(SerializationException::class) {
            Json.decodeFromString<DataWithBool>("""
                { "abc": "48kj3h43hgo5i3", "xyz": "ok" }
            """.trimIndent())
        }
        assertFailsWith(SerializationException::class) {
            val obj = Json.decodeFromString<DataWithBool>("""
                    { "abc": "48kj3h43hgo5i3" }
                """.trimIndent())
            println(obj)
        }
    }
}
