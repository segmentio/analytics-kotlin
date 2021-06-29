package com.segment.analytics.main

import com.segment.analytics.utilities.*
import io.mockk.spyk
import io.mockk.verify
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.lang.IllegalArgumentException
import java.lang.IllegalStateException
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JSONTests {

    @Test
    fun `get boolean succeeds`() {

        val jsonObject = JsonObject(mapOf("keyed" to JsonPrimitive(true)))

        val keyedValue = jsonObject.getBoolean("keyed")

        Assertions.assertTrue(keyedValue ?: false)
    }

    @Test
    fun `get boolean fails`() {

        val jsonObject = JsonObject(mapOf("keyed" to JsonPrimitive(false)))

        val keyedValue = jsonObject.getBoolean("keyed")

        Assertions.assertFalse(keyedValue ?: true)
    }

    @Test
    fun `get boolean bad value throws`() {

        val jsonObject = JsonObject(mapOf("keyed" to JsonPrimitive(18)))

        try {
            val keyedValue = jsonObject.getBoolean("keyed")
            Assertions.assertTrue(keyedValue == null)
        } catch (e: Exception) {
            Assertions.fail("Should not throw when not boolean`")
        }
    }

    @Test
    fun `get boolean optional`() {
        val jsonObject = JsonObject(mapOf("keyed" to JsonPrimitive(null as Boolean?)))

        try {
            val keyedValue = jsonObject.getBoolean("keyed")
            Assertions.assertTrue(keyedValue == null)
        } catch (e: Exception) {
            Assertions.fail("Should not throw when null boolean")
        }
    }

    @Test
    fun `get normal string succeeds`() {
        val jsonObject = JsonObject(mapOf("keyed" to JsonPrimitive("test")))

        val keyedValue = jsonObject.getString("keyed")

        Assertions.assertTrue(keyedValue.equals("test"))
    }

    @Test
    fun `get normal string fails`() {
        val jsonObject = JsonObject(mapOf("keyed" to JsonPrimitive("tes†")))

        val keyedValue = jsonObject.getString("keyed")

        Assertions.assertFalse(keyedValue.equals("test"))
    }

    @Test
    fun `get string bad value throws`() {

        val jsonObject = JsonObject(mapOf("keyed" to JsonPrimitive(18)))

        try {
            val keyedValue = jsonObject.getString("keyed")
            Assertions.assertTrue(keyedValue.equals("18"))
        } catch (e: Exception) {
            Assertions.fail("Should not throw when not int primitive")
        }
    }

    @Test
    fun `get string optional`() {
        val jsonObject = JsonObject(mapOf("keyed" to JsonPrimitive(null as String?)))

        try {
            val keyedValue = jsonObject.getString("keyed")
            Assertions.assertTrue(keyedValue == null)
        } catch (e: Exception) {
            Assertions.fail("Should not throw when null string")
        }
    }

    @Test
    fun `get normal integer succeeds`() {
        val jsonObject = JsonObject(mapOf("keyed" to JsonPrimitive(18)))

        val keyedValue = jsonObject.getInt("keyed")

        Assertions.assertTrue(keyedValue?.equals(18) ?: false)
    }

    @Test
    fun `get normal integer fails`() {
        val jsonObject = JsonObject(mapOf("keyed" to JsonPrimitive(19)))

        val keyedValue = jsonObject.getInt("keyed")

        Assertions.assertFalse(keyedValue?.equals(18) ?: true)
    }

    @Test
    fun `get integer bad value throws`() {

        val jsonObject = JsonObject(mapOf("keyed" to JsonPrimitive("18")))

        try {
            val keyedValue = jsonObject.getInt("keyed")
            Assertions.assertTrue(keyedValue?.equals(18) ?: false)
        } catch (e: Exception) {
            Assertions.fail("Should not throw when not int primitive")
        }
    }

    @Test
    fun `get integer optional`() {
        val jsonObject = JsonObject(mapOf("keyed" to JsonPrimitive(null as Int?)))

        try {
            val keyedValue = jsonObject.getInt("keyed")
            Assertions.assertTrue(keyedValue == null)
        } catch (e: Exception) {
            Assertions.fail("Should not throw when null int")
        }
    }

    @Test
    fun `get normal double succeeds`() {
        val jsonObject = JsonObject(mapOf("keyed" to JsonPrimitive(18.0)))

        val keyedValue = jsonObject.getDouble("keyed")

        Assertions.assertTrue(keyedValue?.equals(18.0) ?: false)
    }

    @Test
    fun `get normal double fails`() {
        val jsonObject = JsonObject(mapOf("keyed" to JsonPrimitive(19.0)))

        val keyedValue = jsonObject.getDouble("keyed")

        Assertions.assertFalse(keyedValue?.equals(18.0) ?: true)
    }

    @Test
    fun `get double bad value throws`() {

        val jsonObject = JsonObject(mapOf("keyed" to JsonPrimitive("18")))

        try {
            val keyedValue = jsonObject.getDouble("keyed")
            Assertions.assertTrue(keyedValue?.equals(18.0) ?: false)
        } catch (e: Exception) {
            Assertions.fail("Should not throw when not double primitive")
        }
    }

    @Test
    fun `get double optional`() {
        val jsonObject = JsonObject(mapOf("keyed" to JsonPrimitive(null as Double?)))

        try {
            val keyedValue = jsonObject.getDouble("keyed")
            Assertions.assertTrue(keyedValue == null)
        } catch (e: Exception) {
            Assertions.fail("Should not throw when null double")
        }
    }

    @Test
    fun `get normal string set`() {
        val jsonObject = JsonObject(mapOf("keyed" to buildJsonArray { add("joker"); add("batman"); add("Mr. Freeze") }))

        val keyedValue = jsonObject.getStringSet("keyed")

        Assertions.assertTrue(keyedValue?.count() ?: 0 == 3)

        Assertions.assertTrue(keyedValue?.contains("Mr. Freeze") ?: false)
    }

    @Test
    fun `get normal string set with duplicate`() {
        val jsonObject = JsonObject(mapOf("keyed" to buildJsonArray { add("joker"); add("batman"); add("Mr. Freeze"); add("batman") }))

        val keyedValue = jsonObject.getStringSet("keyed")

        // Make sure there is still 3 and that it removed an additional batman
        Assertions.assertTrue(keyedValue?.count() ?: 0 == 3)
    }

    @Test
    fun `get normal string set with improper lookup`() {
        val jsonObject = JsonObject(mapOf("keyed" to buildJsonArray { add("joker"); add("batman"); add("Mr. Freeze"); add("batman") }))

        val keyedValue = jsonObject.getStringSet("keyed")

        Assertions.assertFalse(keyedValue?.contains("Penguin") ?: true)
    }

    @Test
    fun `get null lookup for improper keyed type`() {
        val jsonObject = JsonObject(mapOf("keyed" to JsonPrimitive(18)))

        try {
            val keyedValue = jsonObject.getStringSet("keyed")
            Assertions.fail("Should not return a set with the wrong primitive")
        } catch (e: Exception) {
            Assertions.assertTrue(e is IllegalArgumentException)
        }
    }

    @Test
    fun `get normal set map`() {
        val villainMap = mapOf("villains" to JsonPrimitive("Mr. Freeze"))
        val jsonObject = JsonObject(mapOf("keyed" to JsonObject(villainMap)))

        val keyedValue = jsonObject.getMapSet("keyed")

        Assertions.assertTrue(keyedValue is Set<Map<*, *>>)

        Assertions.assertTrue(keyedValue?.contains(villainMap) ?: false)
    }

    @Test
    fun `get normal set map retrieve key value`() {
        val villainMap = mapOf("villains" to JsonPrimitive("Mr. Freeze"))
        val jsonObject = JsonObject(mapOf("keyed" to JsonObject(villainMap)))

        val keyedValue = jsonObject.getMapSet("keyed")

        val keyedMap = keyedValue?.first() ?: run {
            Assertions.fail("Could not find map")
        }

        val temp = keyedMap["villains"] as JsonPrimitive

        Assertions.assertTrue(temp?.contentOrNull == "Mr. Freeze")
    }

    @Test
    fun `get normal string set map with duplicate`() {
        val batmanMap = mapOf("villains" to buildJsonArray { add("Joker"); add("Bain"); add("Mr. Freeze"); add("Bain") },
                "heroes" to buildJsonArray { add("Batman"); add("Robin"); add("James Gordon"); add("Catwoman") })
        val jsonObject = JsonObject(mapOf("batman" to JsonObject(batmanMap)))

        val keyedValue = jsonObject.getMapSet("batman")

        // Make sure there is still 4 and that it did not remove an additional Bain
        Assertions.assertTrue(keyedValue?.count() ?: 0 == 2)
    }

    @Test
    fun `get normal map with wrong type`() {
        val villainMap = mapOf("villains" to JsonPrimitive(18))
        val jsonObject = JsonObject(mapOf("keyed" to JsonObject(villainMap)))

        val keyedValue = jsonObject.getMapSet("keyed")

        val villainNumber = keyedValue?.first() as Map<*, JsonPrimitive>
        val retrievedMap = villainNumber["villains"] ?: run {
            Assertions.fail("Could not find villains map")
        }

        // Make sure there is still 4 and that it did not remove an additional Bain
        Assertions.assertTrue(retrievedMap.intOrNull == 18)
    }
}