package com.segment.analytics.kotlin.core

import com.segment.analytics.kotlin.core.utilities.*
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JSONTests {

    @Nested
    inner class JsonObjectGetTests {

        @Test
        fun `get boolean succeeds`() {
            val jsonObject = buildJsonObject { put("keyed", true) }

            val keyedValue = jsonObject.getBoolean("keyed")

            assertEquals(true, keyedValue)
        }

        @Test
        fun `get boolean fails`() {
            val jsonObject = buildJsonObject { put("keyed", false) }

            val keyedValue = jsonObject.getBoolean("keyed")

            assertEquals(false, keyedValue)
        }

        @Test
        fun `get boolean bad value throws`() {
            val jsonObject = buildJsonObject { put("keyed", 18) }

            try {
                val keyedValue = jsonObject.getBoolean("keyed")
                assertNull(keyedValue)
            } catch (e: Exception) {
                fail("Should not throw when not boolean`")
            }
        }

        @Test
        fun `get boolean optional`() {
            val jsonObject = buildJsonObject { put("keyed", null as Boolean?) }

            try {
                val keyedValue = jsonObject.getBoolean("keyed")
                assertNull(keyedValue)
            } catch (e: Exception) {
                fail("Should not throw when null boolean")
            }
        }

        @Test
        fun `get normal string succeeds`() {
            val jsonObject = buildJsonObject { put("keyed", "test") }

            val keyedValue = jsonObject.getString("keyed")

            assertEquals("test", keyedValue)
        }

        @Test
        fun `get normal string fails`() {
            val jsonObject = buildJsonObject { put("keyed", "tes†") }

            val keyedValue = jsonObject.getString("keyed")

            assertNotEquals("test", keyedValue)
        }

        @Test
        fun `get string bad value throws`() {
            val jsonObject = buildJsonObject { put("keyed", 18) }

            try {
                val keyedValue = jsonObject.getString("keyed")
                assertEquals("18", keyedValue)
            } catch (e: Exception) {
                fail("Should not throw when not int primitive")
            }
        }

        @Test
        fun `get string optional`() {
            val jsonObject = buildJsonObject { put("keyed", null as String?) }

            try {
                val keyedValue = jsonObject.getString("keyed")
                assertNull(keyedValue)
            } catch (e: Exception) {
                fail("Should not throw when null string")
            }
        }

        @Test
        fun `get normal integer succeeds`() {
            val jsonObject = buildJsonObject { put("keyed", 18) }

            val keyedValue = jsonObject.getInt("keyed")

            assertEquals(18, keyedValue)
        }

        @Test
        fun `get normal integer fails`() {
            val jsonObject = buildJsonObject { put("keyed", 19) }

            val keyedValue = jsonObject.getInt("keyed")

            assertNotEquals(18, keyedValue)
        }

        @Test
        fun `get integer bad value throws`() {

            val jsonObject = buildJsonObject { put("keyed", 18) }

            try {
                val keyedValue = jsonObject.getInt("keyed")
                assertEquals(18, keyedValue)
            } catch (e: Exception) {
                fail("Should not throw when not int primitive")
            }
        }

        @Test
        fun `get integer optional`() {
            val jsonObject = buildJsonObject { put("keyed", null as Int?) }

            try {
                val keyedValue = jsonObject.getInt("keyed")
                assertNull(keyedValue)
            } catch (e: Exception) {
                fail("Should not throw when null int")
            }
        }

        @Test
        fun `get normal long succeeds`() {
            val jsonObject = buildJsonObject { put("keyed", 2147483648L) }

            val keyedValue = jsonObject.getLong("keyed")

            assertEquals(2147483648L, keyedValue)
        }

        @Test
        fun `get normal long fails`() {
            val jsonObject = buildJsonObject { put("keyed", 2147483649L) }

            val keyedValue = jsonObject.getLong("keyed")

            assertNotEquals(2147483648L, keyedValue)
        }

        @Test
        fun `get long bad value throws`() {

            val jsonObject = buildJsonObject { put("keyed", "2147483648") }

            try {
                val keyedValue = jsonObject.getLong("keyed")
                assertEquals(2147483648L, keyedValue)
            } catch (e: Exception) {
                fail("Should not throw when not int primitive")
            }
        }

        @Test
        fun `get long optional`() {
            val jsonObject = buildJsonObject { put("keyed", null as Long?) }

            try {
                val keyedValue = jsonObject.getLong("keyed")
                assertNull(keyedValue)
            } catch (e: Exception) {
                fail("Should not throw when null int")
            }
        }

        @Test
        fun `get normal double succeeds`() {
            val jsonObject = buildJsonObject { put("keyed", 18.0) }

            val keyedValue = jsonObject.getDouble("keyed")

            assertEquals(18.0, keyedValue)
        }

        @Test
        fun `get normal double fails`() {
            val jsonObject = buildJsonObject { put("keyed", 19.0) }

            val keyedValue = jsonObject.getDouble("keyed")

            assertNotEquals(18.0, keyedValue)
        }

        @Test
        fun `get double bad value throws`() {

            val jsonObject = buildJsonObject { put("keyed", "18") }

            try {
                val keyedValue = jsonObject.getDouble("keyed")
                assertEquals(18.0, keyedValue)
            } catch (e: Exception) {
                fail("Should not throw when not double primitive")
            }
        }

        @Test
        fun `get double optional`() {
            val jsonObject = buildJsonObject { put("keyed", null as Double?) }

            try {
                val keyedValue = jsonObject.getDouble("keyed")
                assertNull(keyedValue)
            } catch (e: Exception) {
                fail("Should not throw when null double")
            }
        }

        @Test
        fun `get normal string set`() {
            val jsonObject = buildJsonObject {
                put("keyed", buildJsonArray {
                    add("joker")
                    add("batman")
                    add("Mr. Freeze")
                })
            }

            val keyedValue = jsonObject.getStringSet("keyed")

            assertEquals(3, keyedValue?.count())

            assertTrue(keyedValue?.contains("Mr. Freeze") ?: false)
        }

        @Test
        fun `get normal string set with duplicate`() {
            val jsonObject = buildJsonObject {
                put("keyed", buildJsonArray {
                    add("joker")
                    add("batman")
                    add("Mr. Freeze")
                    add("batman")
                })
            }

            val keyedValue = jsonObject.getStringSet("keyed")

            // Make sure there is still 3 and that it removed an additional batman
            assertEquals(3, keyedValue?.count())
        }

        @Test
        fun `get normal string set with improper lookup`() {
            val jsonObject = buildJsonObject {
                put("keyed", buildJsonArray {
                    add("joker")
                    add("batman")
                    add("Mr. Freeze")
                    add("batman")
                })
            }

            val keyedValue = jsonObject.getStringSet("keyed")

            assertFalse(keyedValue?.contains("Penguin") ?: true)
        }

        @Test
        fun `get null lookup for improper keyed type`() {
            val jsonObject = buildJsonObject { put("keyed", 18) }

            val keyedValue = jsonObject.getStringSet("keyed")
            assertNull(keyedValue)
        }

        @Test
        fun `get normal set map`() {
            val jsonObject = buildJsonObject {
                put("villains", buildJsonArray {
                    add(buildJsonObject {
                        put("name", "Victor")
                        put("alias", "Mr. Freeze")
                        put("attempts", 2)
                        put("inPrison", false)
                    })
                    add(buildJsonObject {
                        put("name", "Selina")
                        put("alias", "Catwoman")
                        put("attempts", 5)
                        put("inPrison", true)
                    })
                })
            }

            val villainsList = jsonObject.getMapList("villains")

            assertNotNull(villainsList)
            assertEquals(2, villainsList?.size)
            with(villainsList!!) {
                with(this[0]) {
                    assertEquals("Victor", get("name"))
                    assertEquals("Mr. Freeze", get("alias"))
                    assertEquals(false, get("inPrison"))
                    assertEquals(2, get("attempts"))
                }
                with(this[1]) {
                    assertEquals("Selina", get("name"))
                    assertEquals("Catwoman", get("alias"))
                    assertEquals(true, get("inPrison"))
                    assertEquals(5, get("attempts"))
                }
            }
        }

        @Test
        fun `get null, when not an array property`() {
            val jsonObject = buildJsonObject {
                put("keyed", buildJsonObject {
                    put("name", "Selina")
                    put("alias", "Catwoman")
                    put("attempts", 5)
                    put("inPrison", true)
                })
                put("keyed2", 2)
            }

            assertNull(jsonObject.getMapList("keyed"))
            assertNull(jsonObject.getMapList("keyed2"))
        }

        @Test
        fun `get a list of maps, ignoring non-map elements`() {
            val jsonObject = buildJsonObject {
                put("keyed", buildJsonArray {
                    add(18)
                    add(buildJsonObject {
                        put("name", "Selina")
                        put("alias", "Catwoman")
                        put("attempts", 5)
                        put("inPrison", true)
                    })
                })
            }

            val keyedValue = jsonObject.getMapList("keyed")

            assertEquals(1, keyedValue?.size)
            with(keyedValue!![0]) {
                assertEquals("Selina", get("name"))
                assertEquals("Catwoman", get("alias"))
                assertEquals(true, get("inPrison"))
                assertEquals(5, get("attempts"))
            }
        }
    }


    @Nested
    inner class TransformTests {
        @Test
        fun `transformKeys transforms keys correctly`() {
            val map = buildJsonObject {
                put("Joker", true)
                put("Catwoman", false)
                put("Mr. Freeze", true)
            }

            val newMap = map.transformKeys { it.toUpperCase() }

            with(newMap) {
                assertTrue(containsKey("JOKER"))
                assertTrue(containsKey("CATWOMAN"))
                assertTrue(containsKey("MR. FREEZE"))
            }
        }

        @Test
        fun `transformValues transforms values correctly`() {
            val map = buildJsonObject {
                put("Joker", true)
                put("Catwoman", false)
                put("Mr. Freeze", true)
            }

            val newMap = map.transformValues {
                if (it.jsonPrimitive.boolean) {
                    JsonPrimitive("M")
                } else {
                    JsonPrimitive("F")
                }
            }

            with(newMap) {
                assertEquals("M", getString("Joker"))
                assertEquals("F", getString("Catwoman"))
                assertEquals("M", getString("Mr. Freeze"))
            }
        }
    }

    @Nested
    inner class MapTransformTests {


        @Test
        fun `can map keys + nested keys using mapTransform`() {
            val keyMapper = mapOf(
                "item" to "\$item",
                "phone" to "\$phone",
                "name" to "\$name",
            )
            val map = buildJsonObject {
                put("company", buildJsonObject {
                    put("phone", "123-456-7890")
                    put("name", "Wayne Industries")
                })
                put("family", buildJsonArray {
                    add(buildJsonObject { put("name", "Mary") })
                    add(buildJsonObject { put("name", "Thomas") })
                })
                put("name", "Bruce")
                put("last_name", "wayne")
                put("item", "Grapple")
            }
            val newMap = map.mapTransform(keyMapper)
            with(newMap) {
                assertTrue(containsKey("\$name"))
                assertTrue(containsKey("\$item"))
                assertTrue(containsKey("last_name"))
                with(get("company")!!.jsonObject) {
                    assertTrue(containsKey("\$phone"))
                    assertTrue(containsKey("\$name"))
                }
                with(get("family")!!.jsonArray) {
                    assertTrue(get(0).jsonObject.containsKey("\$name"))
                    assertTrue(get(1).jsonObject.containsKey("\$name"))
                }
            }
        }

        @Test
        fun `can transform values using mapTransform`() {
            val map = buildJsonObject {
                put("company", buildJsonObject {
                    put("phone", "123-456-7890")
                    put("name", "Wayne Industries")
                })
                put("family", buildJsonArray {
                    add(buildJsonObject { put("name", "Mary") })
                    add(buildJsonObject { put("name", "Thomas") })
                })
                put("name", "Bruce")
                put("last_name", "wayne")
                put("item", "Grapple")
            }
            val newMap = map.mapTransform(emptyMap()) { newKey, value ->
                var newVal = value
                if (newKey == "phone") {
                    val foo = value.jsonPrimitive.toContent()
                    if (foo is String) {
                        newVal = JsonPrimitive(foo.replace("-", ""))
                    }
                }
                newVal
            }
            with(newMap) {
                with(get("company")!!.jsonObject) {
                    assertEquals("1234567890", getString("phone"))
                }
            }
        }

        @Test
        fun `can map keys + transform values using mapTransform`() {
            val keyMapper = mapOf(
                "item" to "\$item",
                "phone" to "\$phone",
                "name" to "\$name",
            )
            val map = buildJsonObject {
                put("company", buildJsonObject {
                    put("phone", "123-456-7890")
                    put("name", "Wayne Industries")
                })
                put("family", buildJsonArray {
                    add(buildJsonObject { put("name", "Mary") })
                    add(buildJsonObject { put("name", "Thomas") })
                })
                put("name", "Bruce")
                put("last_name", "wayne")
                put("item", "Grapple")
            }
            val newMap = map.mapTransform(keyMapper) { newKey, value ->
                var newVal = value
                if (newKey == "\$phone") {
                    val foo = value.jsonPrimitive.toContent()
                    if (foo is String) {
                        newVal = JsonPrimitive(foo.replace("-", ""))
                    }
                }
                newVal
            }
            with(newMap) {
                assertTrue(containsKey("\$name"))
                assertTrue(containsKey("\$item"))
                assertTrue(containsKey("last_name"))
                with(get("company")!!.jsonObject) {
                    assertTrue(containsKey("\$phone"))
                    assertTrue(containsKey("\$name"))
                    assertEquals("1234567890", getString("\$phone"))
                }
                with(get("family")!!.jsonArray) {
                    assertTrue(get(0).jsonObject.containsKey("\$name"))
                    assertTrue(get(1).jsonObject.containsKey("\$name"))
                }
            }
        }

        @Test
        fun `can map keys + transform values using mapTransform on JsonArray`() {
            val keyMapper = mapOf(
                "item" to "\$item",
                "phone" to "\$phone",
                "name" to "\$name",
            )
            val list = buildJsonArray {
                add(buildJsonObject {
                    put("phone", "123-456-7890")
                    put("name", "Wayne Industries")
                })
                add(buildJsonArray {
                    add(buildJsonObject { put("name", "Mary") })
                    add(buildJsonObject { put("name", "Thomas") })
                })
                add(buildJsonObject {
                    put("name", "Bruce")
                    put("last_name", "wayne")
                    put("item", "Grapple")
                })
            }
            val newList = list.mapTransform(keyMapper) { newKey, value ->
                var newVal = value
                if (newKey == "\$phone") {
                    val foo = value.jsonPrimitive.toContent()
                    if (foo is String) {
                        newVal = JsonPrimitive(foo.replace("-", ""))
                    }
                }
                newVal
            }
            with(newList) {
                get(0).jsonObject.let {
                    assertTrue(it.containsKey("\$phone"))
                    assertTrue(it.containsKey("\$name"))
                }
                get(1).jsonArray.let {
                    assertEquals(buildJsonObject { put("\$name", "Mary") }, it[0])
                    assertEquals(buildJsonObject { put("\$name", "Thomas") }, it[1])
                }
                get(2).jsonObject.let {
                    assertTrue(it.containsKey("\$name"))
                    assertTrue(it.containsKey("\$item"))
                    assertTrue(it.containsKey("last_name"))
                }
            }
        }
    }

    @Nested
    inner class ToContentTests {
        @ParameterizedTest(name = "{displayName}: {arguments}")
        @ValueSource(booleans = [true, false])
        fun `boolean primitive`(input: Boolean) {
            val x = JsonPrimitive(input)
            val y = x.toContent()
            assertEquals(input, y)
        }

        @ParameterizedTest(name = "{displayName}: {arguments}")
        @ValueSource(ints = [Int.MIN_VALUE, Int.MAX_VALUE])
        fun `integer primitive`(input: Int) {
            val x = JsonPrimitive(input)
            val y = x.toContent()
            assertEquals(input, y)
        }

        @ParameterizedTest(name = "{displayName}: {arguments}")
        @ValueSource(longs = [Long.MIN_VALUE, Long.MAX_VALUE])
        fun `long primitive`(input: Long) {
            val x = JsonPrimitive(input)
            val y = x.toContent()
            assertEquals(input, y)
        }

        @ParameterizedTest(name = "{displayName}: {arguments}")
        @ValueSource(floats = [Float.MIN_VALUE, Float.MAX_VALUE])
        fun `float primitive`(input: Float) {
            val x = JsonPrimitive(input)
            val y = x.toContent()
            assertEquals(input, (y as Double).toFloat())
        }

        @ParameterizedTest(name = "{displayName}: {arguments}")
        @ValueSource(doubles = [4.9999999999999999999, -4.9999999999999999999])
        fun `double primitive`(input: Double) {
            val x = JsonPrimitive(input)
            val y = x.toContent()
            assertEquals(input, y)
        }

        @Test
        fun `string primitive`() {
            val x = JsonPrimitive("input")
            val y = x.toContent()
            assertEquals("input", y)
        }

        @Test
        fun `jsonObject to map`() {
            val x = buildJsonObject {
                put("int", 1)
                put("long", Long.MAX_VALUE)
                put("float", 1.0)
                put("double", Double.MAX_VALUE)
                put("string", "input")
                put("jsonObject", buildJsonObject { put("foo", "bar") })
                put("jsonArray", buildJsonArray { add("foo") })
            }

            val y = x.toContent()
            with(y) {
                assertEquals(1, get("int"))
                assertEquals(Long.MAX_VALUE, get("long"))
                assertEquals(1.0, get("float"))
                assertEquals(Double.MAX_VALUE, get("double"))
                assertEquals("input", get("string"))
                with(get("jsonObject") as Map<String, Any?>) {
                    assertEquals("bar", get("foo"))
                }
                with(get("jsonArray") as List<Any?>) {
                    assertTrue(contains("foo"))
                }
            }
        }

        @Test
        fun `jsonArray to array`() {
            val x = buildJsonArray {
                add(1)
                add(Long.MAX_VALUE)
                add(1.0)
                add(Double.MAX_VALUE)
                add("input")
                add(buildJsonObject { put("foo", "bar") })
                add(buildJsonArray { add("foo") })
            }

            val y = x.toContent()
            with(y) {
                assertTrue(contains(1))
                assertTrue(contains(Long.MAX_VALUE))
                assertTrue(contains(1.0))
                assertTrue(contains(Double.MAX_VALUE))
                with(get(5) as Map<String, Any?>) {
                    assertEquals("bar", get("foo"))
                }
                with(get(6) as List<Any?>) {
                    assertTrue(contains("foo"))
                }
            }
        }
    }

    @Nested
    inner class BuilderTests {
        @Test
        fun `builder putAll adds all values`() {
            val z = buildJsonObject {
                put("foo", "fooBar")
                put("bar", "barFoo")
            }
            val x = buildJsonObject {
                putAll(z)
            }
            assertEquals("fooBar", x.getString("foo"))
            assertEquals("barFoo", x.getString("bar"))
        }

        @Test
        fun `builder putUndefinedIfNull `() {
            val x = buildJsonObject {
                putUndefinedIfNull("foo", null)
            }
            assertEquals("undefined", x.getString("foo"))
        }
    }

    @Nested
    inner class UpdateJsonObjectTests {
        @Test
        fun `updateJsonObject updates values`() {
            var obj = emptyJsonObject

            // test string
            obj = updateJsonObject(obj) {
                it["foo"] = "bar"
            }
            assertEquals(1, obj.size)
            assertEquals("bar", obj.getString("foo"))

            // test number
            obj = updateJsonObject(obj) {
                it["foo"] = null
                it["number"] = 1
            }
            assertEquals(1, obj.size)
            assertEquals(1, obj.getInt("number"))

            obj = updateJsonObject(obj) {
                it["number"] = 2.0
            }
            assertEquals(1, obj.size)
            assertEquals(2.0, obj.getDouble("number"))

            obj = updateJsonObject(obj) {
                it["number"] = 4L
            }
            assertEquals(1, obj.size)
            assertEquals(4L, obj.getLong("number"))

            // test boolean
            obj = updateJsonObject(obj) {
                it["number"] = null
                it["boolean"] = true
            }
            assertEquals(1, obj.size)
            assertEquals(true, obj.getBoolean("boolean"))
        }
    }
}