package com.segment.analytics.kotlin.core.compat

import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class XElementBuildersTest {

    @Test
    fun `xson object can put xson object`() {
        val jsonObject = buildJsonObject {
            put("object", buildJsonObject {
                put("int", 1)
                put("long", 1L)
                put("float", 1.0f)
                put("double", 1.0)
                put("string", "1")
                put("boolean", true)
            })
        }

        val xsonObject = XElementBuilders.XsonObjectBuilder()
            .putXsonObject("object") {
                it.put("int", 1)
                    .put("long", 1L)
                    .put("float", 1.0f)
                    .put("double", 1.0)
                    .put("string", "1")
                    .put("boolean", true)
            }.build()

        assertEquals(jsonObject, xsonObject)
    }

    @Test
    fun `xson object can put xson array`() {
        val jsonObject = buildJsonObject {
            put("array", buildJsonArray {
                add(1)
                add(1L)
                add(1.0f)
                add(1.0)
                add("1")
                add(true)
            })
        }

        val xsonObject = XElementBuilders.XsonObjectBuilder()
            .putXsonArray("array") {
                it.add(1)
                    .add(1L)
                    .add(1.0f)
                    .add(1.0)
                    .add("1")
                    .add(true)
            }.build()

        assertEquals(jsonObject, xsonObject)
    }

    @Test
    fun `xson array can add xsonObject`() {
        val jsonArray = buildJsonArray {
            add(buildJsonObject {
                put("int", 1)
                put("long", 1L)
                put("float", 1.0f)
                put("double", 1.0)
                put("string", "1")
                put("boolean", true)
            })
        }

        val xsonArray = XElementBuilders.XsonArrayBuilder()
            .addXsonObject() {
                it.put("int", 1)
                    .put("long", 1L)
                    .put("float", 1.0f)
                    .put("double", 1.0)
                    .put("string", "1")
                    .put("boolean", true)
            }.build()

        assertEquals(jsonArray, xsonArray)
    }

    @Test
    fun `xson array can add xson array`() {
        val jsonArray = buildJsonArray {
            addJsonArray {
                add(1)
                add(1L)
                add(1.0f)
                add(1.0)
                add("1")
                add(true)
            }
        }

        val xsonArray = XElementBuilders.XsonArrayBuilder()
            .addXsonArray() {
                it.add(1)
                    .add(1L)
                    .add(1.0f)
                    .add(1.0)
                    .add("1")
                    .add(true)
            }.build()

        assertEquals(jsonArray, xsonArray)
    }

    @Test
    fun `test buildXsonObjectFunc`() {
        val xsonObject = XElementBuilders.buildXsonObjectFunc {
            put("int", 1)
            put("long", 1L)
            put("float", 1.0f)
            put("double", 1.0)
            put("string", "1")
            put("boolean", true)
            putXsonObject("object") {
                it.put("string", "this is object")
            }
            putXsonArray("array") {
                it.add("this is array")
            }
        }

        assertEquals(fullJsonObject(), xsonObject)
    }

    @Test
    fun `test buildXsonObject`() {
        val xsonObject = XElementBuilders.buildXsonObject() {
            it.put("int", 1)
                .put("long", 1L)
                .put("float", 1.0f)
                .put("double", 1.0)
                .put("string", "1")
                .put("boolean", true)
                .put("object", XElementBuilders.buildXsonObject() {
                    it.put("string", "this is object")
                })
                .put("array", XElementBuilders.buildXsonArray() {
                    it.add("this is array")
                })
        }

        assertEquals(fullJsonObject(), xsonObject)
    }

    @Test
    fun `test buildXsonArrayFunc`() {
        val xsonArray = XElementBuilders.buildXsonArrayFunc {
            add(1)
            add(1L)
            add(1.0f)
            add(1.0)
            add("1")
            add(true)
            addXsonObject() {
                it.put("string", "this is object")
            }
            addXsonArray() {
                it.add("this is array")
            }
        }

        assertEquals(fullJsonArray(), xsonArray)
    }

    @Test
    fun `test buildXsonArray`() {
        val xsonArray = XElementBuilders.buildXsonArray() {
            it.add(1)
                .add(1L)
                .add(1.0f)
                .add(1.0)
                .add("1")
                .add(true)
                .addXsonObject() {
                    it.put("string", "this is object")
                }
                .addXsonArray() {
                    it.add("this is array")
                }
        }

        assertEquals(fullJsonArray(), xsonArray)
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