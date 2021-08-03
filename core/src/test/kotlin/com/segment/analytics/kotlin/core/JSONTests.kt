package com.segment.analytics.kotlin.core

import com.segment.analytics.kotlin.core.utilities.getBoolean
import com.segment.analytics.kotlin.core.utilities.getDouble
import com.segment.analytics.kotlin.core.utilities.getInt
import com.segment.analytics.kotlin.core.utilities.getMapSet
import com.segment.analytics.kotlin.core.utilities.getString
import com.segment.analytics.kotlin.core.utilities.getStringSet
import com.segment.analytics.kotlin.core.utilities.transformKeys
import com.segment.analytics.kotlin.core.utilities.transformValues
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.lang.IllegalArgumentException

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JSONTests {

    @Test
    fun `get boolean succeeds`() {

        val jsonObject = JsonObject(mapOf("keyed" to JsonPrimitive(true)))

        val keyedValue = jsonObject.getBoolean("keyed")

        assertTrue(keyedValue ?: false)
    }

    @Test
    fun `get boolean fails`() {

        val jsonObject = JsonObject(mapOf("keyed" to JsonPrimitive(false)))

        val keyedValue = jsonObject.getBoolean("keyed")

        assertFalse(keyedValue ?: true)
    }

    @Test
    fun `get boolean bad value throws`() {

        val jsonObject = JsonObject(mapOf("keyed" to JsonPrimitive(18)))

        try {
            val keyedValue = jsonObject.getBoolean("keyed")
            assertTrue(keyedValue == null)
        } catch (e: Exception) {
            fail("Should not throw when not boolean`")
        }
    }

    @Test
    fun `get boolean optional`() {
        val jsonObject = JsonObject(mapOf("keyed" to JsonPrimitive(null as Boolean?)))

        try {
            val keyedValue = jsonObject.getBoolean("keyed")
            assertTrue(keyedValue == null)
        } catch (e: Exception) {
            fail("Should not throw when null boolean")
        }
    }

    @Test
    fun `get normal string succeeds`() {
        val jsonObject = JsonObject(mapOf("keyed" to JsonPrimitive("test")))

        val keyedValue = jsonObject.getString("keyed")

        assertTrue(keyedValue.equals("test"))
    }

    @Test
    fun `get normal string fails`() {
        val jsonObject = JsonObject(mapOf("keyed" to JsonPrimitive("tes†")))

        val keyedValue = jsonObject.getString("keyed")

        assertFalse(keyedValue.equals("test"))
    }

    @Test
    fun `get string bad value throws`() {

        val jsonObject = JsonObject(mapOf("keyed" to JsonPrimitive(18)))

        try {
            val keyedValue = jsonObject.getString("keyed")
            assertTrue(keyedValue.equals("18"))
        } catch (e: Exception) {
            fail("Should not throw when not int primitive")
        }
    }

    @Test
    fun `get string optional`() {
        val jsonObject = JsonObject(mapOf("keyed" to JsonPrimitive(null as String?)))

        try {
            val keyedValue = jsonObject.getString("keyed")
            assertTrue(keyedValue == null)
        } catch (e: Exception) {
            fail("Should not throw when null string")
        }
    }

    @Test
    fun `get normal integer succeeds`() {
        val jsonObject = JsonObject(mapOf("keyed" to JsonPrimitive(18)))

        val keyedValue = jsonObject.getInt("keyed")

        assertTrue(keyedValue?.equals(18) ?: false)
    }

    @Test
    fun `get normal integer fails`() {
        val jsonObject = JsonObject(mapOf("keyed" to JsonPrimitive(19)))

        val keyedValue = jsonObject.getInt("keyed")

        assertFalse(keyedValue?.equals(18) ?: true)
    }

    @Test
    fun `get integer bad value throws`() {

        val jsonObject = JsonObject(mapOf("keyed" to JsonPrimitive("18")))

        try {
            val keyedValue = jsonObject.getInt("keyed")
            assertTrue(keyedValue?.equals(18) ?: false)
        } catch (e: Exception) {
            fail("Should not throw when not int primitive")
        }
    }

    @Test
    fun `get integer optional`() {
        val jsonObject = JsonObject(mapOf("keyed" to JsonPrimitive(null as Int?)))

        try {
            val keyedValue = jsonObject.getInt("keyed")
            assertTrue(keyedValue == null)
        } catch (e: Exception) {
            fail("Should not throw when null int")
        }
    }

    @Test
    fun `get normal double succeeds`() {
        val jsonObject = JsonObject(mapOf("keyed" to JsonPrimitive(18.0)))

        val keyedValue = jsonObject.getDouble("keyed")

        assertTrue(keyedValue?.equals(18.0) ?: false)
    }

    @Test
    fun `get normal double fails`() {
        val jsonObject = JsonObject(mapOf("keyed" to JsonPrimitive(19.0)))

        val keyedValue = jsonObject.getDouble("keyed")

        assertFalse(keyedValue?.equals(18.0) ?: true)
    }

    @Test
    fun `get double bad value throws`() {

        val jsonObject = JsonObject(mapOf("keyed" to JsonPrimitive("18")))

        try {
            val keyedValue = jsonObject.getDouble("keyed")
            assertTrue(keyedValue?.equals(18.0) ?: false)
        } catch (e: Exception) {
            fail("Should not throw when not double primitive")
        }
    }

    @Test
    fun `get double optional`() {
        val jsonObject = JsonObject(mapOf("keyed" to JsonPrimitive(null as Double?)))

        try {
            val keyedValue = jsonObject.getDouble("keyed")
            assertTrue(keyedValue == null)
        } catch (e: Exception) {
            fail("Should not throw when null double")
        }
    }

    @Test
    fun `get normal string set`() {
        val jsonObject =
            JsonObject(mapOf("keyed" to buildJsonArray { add("joker"); add("batman"); add("Mr. Freeze") }))

        val keyedValue = jsonObject.getStringSet("keyed")

        assertTrue(keyedValue?.count() ?: 0 == 3)

        assertTrue(keyedValue?.contains("Mr. Freeze") ?: false)
    }

    @Test
    fun `get normal string set with duplicate`() {
        val jsonObject = JsonObject(mapOf("keyed" to buildJsonArray {
            add("joker"); add("batman"); add("Mr. Freeze"); add("batman")
        }))

        val keyedValue = jsonObject.getStringSet("keyed")

        // Make sure there is still 3 and that it removed an additional batman
        assertTrue(keyedValue?.count() ?: 0 == 3)
    }

    @Test
    fun `get normal string set with improper lookup`() {
        val jsonObject = JsonObject(mapOf("keyed" to buildJsonArray {
            add("joker"); add("batman"); add("Mr. Freeze"); add("batman")
        }))

        val keyedValue = jsonObject.getStringSet("keyed")

        assertFalse(keyedValue?.contains("Penguin") ?: true)
    }

    @Test
    fun `get null lookup for improper keyed type`() {
        val jsonObject = JsonObject(mapOf("keyed" to JsonPrimitive(18)))

        try {
            val keyedValue = jsonObject.getStringSet("keyed")
            fail("Should not return a set with the wrong primitive")
        } catch (e: Exception) {
            assertTrue(e is IllegalArgumentException)
        }
    }

    @Test
    fun `get normal set map`() {
        val villainMap = mapOf("villains" to JsonPrimitive("Mr. Freeze"))
        val jsonObject = JsonObject(mapOf("keyed" to JsonObject(villainMap)))

        val keyedValue = jsonObject.getMapSet("keyed")

        assertTrue(keyedValue is Set<Map<*, *>>)

        assertTrue(keyedValue?.contains(villainMap) ?: false)
    }

    @Test
    fun `get normal set map retrieve key value`() {
        val villainMap = mapOf("villains" to JsonPrimitive("Mr. Freeze"))
        val jsonObject = JsonObject(mapOf("keyed" to JsonObject(villainMap)))

        val keyedValue = jsonObject.getMapSet("keyed")

        val keyedMap = keyedValue?.first() ?: run {
            fail("Could not find map")
        }

        val temp = keyedMap["villains"] as JsonPrimitive

        assertTrue(temp?.contentOrNull == "Mr. Freeze")
    }

    @Test
    fun `get normal string set map with duplicate`() {
        val batmanMap =
            mapOf("villains" to buildJsonArray { add("Joker"); add("Bain"); add("Mr. Freeze"); add("Bain") },
                "heroes" to buildJsonArray { add("Batman"); add("Robin"); add("James Gordon"); add("Catwoman") })
        val jsonObject = JsonObject(mapOf("batman" to JsonObject(batmanMap)))

        val keyedValue = jsonObject.getMapSet("batman")

        // Make sure there is still 4 and that it did not remove an additional Bain
        assertTrue(keyedValue?.count() ?: 0 == 2)
    }

    @Test
    fun `get normal map with wrong type`() {
        val villainMap = mapOf("villains" to JsonPrimitive(18))
        val jsonObject = JsonObject(mapOf("keyed" to JsonObject(villainMap)))

        val keyedValue = jsonObject.getMapSet("keyed")

        val villainNumber = keyedValue?.first() as Map<*, JsonPrimitive>
        val retrievedMap = villainNumber["villains"] ?: run {
            fail("Could not find villains map")
        }

        // Make sure there is still 4 and that it did not remove an additional Bain
        assertTrue(retrievedMap.intOrNull == 18)
    }

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