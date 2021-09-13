package com.segment.analytics.kotlin.core.compat

import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class BuildersTest {

    @Test
    fun `json object can put json object`() {
        val expected = buildJsonObject {
            put("object", buildJsonObject {
                put("int", 1)
                put("long", 1L)
                put("float", 1.0f)
                put("double", 1.0)
                put("string", "1")
                put("boolean", true)
            })
        }

        val actual = Builders.JsonObjectBuilder()
            .putJsonObject("object") {
                it.put("int", 1)
                    .put("long", 1L)
                    .put("float", 1.0f)
                    .put("double", 1.0)
                    .put("string", "1")
                    .put("boolean", true)
            }.build()

        assertEquals(expected, actual)
    }

    @Test
    fun `json object can put json array`() {
        val expected = buildJsonObject {
            put("array", buildJsonArray {
                add(1)
                add(1L)
                add(1.0f)
                add(1.0)
                add("1")
                add(true)
            })
        }

        val actual = Builders.JsonObjectBuilder()
            .putJsonArray("array") {
                it.add(1)
                    .add(1L)
                    .add(1.0f)
                    .add(1.0)
                    .add("1")
                    .add(true)
            }.build()

        assertEquals(expected, actual)
    }

    @Test
    fun `json array can add jsonObject`() {
        val expected = buildJsonArray {
            add(buildJsonObject {
                put("int", 1)
                put("long", 1L)
                put("float", 1.0f)
                put("double", 1.0)
                put("string", "1")
                put("boolean", true)
            })
        }

        val actual = Builders.JsonArrayBuilder()
            .addJsonObject() {
                it.put("int", 1)
                    .put("long", 1L)
                    .put("float", 1.0f)
                    .put("double", 1.0)
                    .put("string", "1")
                    .put("boolean", true)
            }.build()

        assertEquals(expected, actual)
    }

    @Test
    fun `json array can add json array`() {
        val expected = buildJsonArray {
            addJsonArray {
                add(1)
                add(1L)
                add(1.0f)
                add(1.0)
                add("1")
                add(true)
            }
        }

        val actual = Builders.JsonArrayBuilder()
            .addJsonArray() {
                it.add(1)
                    .add(1L)
                    .add(1.0f)
                    .add(1.0)
                    .add("1")
                    .add(true)
            }.build()

        assertEquals(expected, actual)
    }

    @Test
    fun `test buildJsonObjectFunc`() {
        val actual = Builders.buildJsonObjectFunc {
            put("int", 1)
            put("long", 1L)
            put("float", 1.0f)
            put("double", 1.0)
            put("string", "1")
            put("boolean", true)
            putJsonObjectFunc("object") {
                put("string", "this is object")
            }
            putJsonArrayFunc("array") {
                add("this is array")
            }
        }

        assertEquals(fullJsonObject(), actual)
    }

    @Test
    fun `test buildJsonObject`() {
        val actual = Builders.buildJsonObject() {
            it.put("int", 1)
                .put("long", 1L)
                .put("float", 1.0f)
                .put("double", 1.0)
                .put("string", "1")
                .put("boolean", true)
                .put("object", Builders.buildJsonObject() {
                    it.put("string", "this is object")
                })
                .put("array", Builders.buildJsonArray() {
                    it.add("this is array")
                })
        }

        assertEquals(fullJsonObject(), actual)
    }

    @Test
    fun `test buildJsonArrayFunc`() {
        val actual = Builders.buildJsonArrayFunc {
            add(1)
            add(1L)
            add(1.0f)
            add(1.0)
            add("1")
            add(true)
            addJsonObjectFunc {
                put("string", "this is object")
            }
            addJsonArrayFunc {
                add("this is array")
            }
        }

        assertEquals(fullJsonArray(), actual)
    }

    @Test
    fun `test buildJsonArray`() {
        val actual = Builders.buildJsonArray() {
            it.add(1)
                .add(1L)
                .add(1.0f)
                .add(1.0)
                .add("1")
                .add(true)
                .addJsonObject() {
                    it.put("string", "this is object")
                }
                .addJsonArray() {
                    it.add("this is array")
                }
        }

        assertEquals(fullJsonArray(), actual)
    }

    private fun fullJsonObject() = buildJsonObject {
        put("int", 1)
        put("long", 1L)
        put("float", 1.0f)
        put("double", 1.0)
        put("string", "1")
        put("boolean", true)
        putJsonObject ("object") {
            put("string", "this is object")
        }
        putJsonArray("array") {
            add("this is array")
        }
    }

    private fun fullJsonArray() = buildJsonArray {
        add(1)
        add(1L)
        add(1.0f)
        add(1.0)
        add("1")
        add(true)
        addJsonObject {
            put("string", "this is object")
        }
        addJsonArray {
            add("this is array")
        }
    }
}