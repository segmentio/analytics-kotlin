package com.segment.analytics.destinations.plugins

import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.BaseEvent
import com.segment.analytics.kotlin.core.TrackEvent
import com.segment.analytics.kotlin.core.platform.Plugin
import com.segment.analytics.kotlin.core.utilities.toContent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement

/*
{
  "matchers": [
    {
      "ir": "[\"!\",[\"or\",[\"=\",\"event\",{\"value\":\"CP VOD - Start video\"}],[\"=\",\"event\",{\"value\":\"CP VOD - Track video\"}]]]",
      "type": "fql",
      "config": {
        "expr": "!(event = \"CP VOD - Start video\" or event = \"CP VOD - Track video\")"
      }
    }
  ],
  "scope": "destinations",
  "target_type": "workspace::project::destination::config",
  "transformers": [
    [
      {
        "type": "drop"
      }
    ]
  ],
  "destinationName": "Amazon Kinesis"
}
 */

class DestinationFiltersDropPlugin : Plugin {
    override val type: Plugin.Type = Plugin.Type.Enrichment
    override lateinit var analytics: Analytics

    val matchers = mutableListOf<Matcher>()

    // Implement only drop event
    override fun execute(event: BaseEvent): BaseEvent? {
        for (matcher in matchers) {
            if (matcher.match(event)) {
                return null
            }
        }
        return super.execute(event)
    }
}

sealed class FQLValue {
    abstract fun value(): JsonElement

    class Value(private val value: JsonElement) : FQLValue() {
        override fun value(): JsonElement {
            return value
        }
    }

    class Path(private val event: BaseEvent, private val path: String) : FQLValue() {
        override fun value(): JsonElement {
            return if (path == "event") {
                event as TrackEvent
                JsonPrimitive(event.event)
            } else {
                JsonPrimitive("")
            }
        }
    }
}

interface Matcher {
    /*
    {
      "ir": "["!",["or",["=","event",{"value":"CP VOD - Start video"}],["=","event",{"value":"CP VOD - Track video"}]]]",
      "type": "fql",
      "config": {
        "expr": "!(event = \"CP VOD - Start video\" or event = \"CP VOD - Track video\")"
      }
    }
     */
    fun match(payload: BaseEvent): Boolean
}

interface Expression {
    fun evaluate(event: BaseEvent): Boolean
}

class Not(private val e: Expression) : Expression {
    override fun evaluate(event: BaseEvent): Boolean {
        return !e.evaluate(event)
    }
}

class Or(private val e1: Expression, private val e2: Expression) : Expression {
    override fun evaluate(event: BaseEvent): Boolean {
        return e1.evaluate(event) || e2.evaluate(event)
    }
}

class And(private val e1: Expression, private val e2: Expression) : Expression {
    override fun evaluate(event: BaseEvent): Boolean {
        return e1.evaluate(event) && e2.evaluate(event)
    }
}

class Equals(private val t1: FQLValue, private val t2: FQLValue) : Expression {
    override fun evaluate(event: BaseEvent): Boolean {
        return t1.value() == t2.value()
    }
}

// Not(Or(Equals(payload.event, "CP VOD - Start video"), Equals(payload.event, "CP VOD - Track video")))

class FQLDropPlugin(fqlQuery: String) : Plugin {
    override val type: Plugin.Type = Plugin.Type.Enrichment
    override lateinit var analytics: Analytics

    val fqlIR: JsonArray = Json.decodeFromString(JsonArray.serializer(), fqlQuery)

    // Implement only drop event
    override fun execute(event: BaseEvent): BaseEvent? {
        if (fqlEvaluate(fqlIR, event)) {
            return null
        }
        return super.execute(event)
    }
}

//fun JsonArray.tail(n: Int): JsonArray {
//    return JsonArray(drop(n))
//}

// "["!",["or",["=","event",{"value":"CP VOD - Start video"}],["=","event",{"value":"CP VOD - Track video"}]]]",
// Array("!", Array("or", Array("=", "event", {"value":"CP VOD"}), Array("=", "event", {"value":"CP VOD"})))
fun fqlEvaluate(ir: JsonElement, payload: BaseEvent): Boolean {
    // check if the given value is literally `true`
    if (ir !is JsonArray) {
        return getValue(ir, payload) == true
    }
    val item = ir[0].toContent()
    when (item) {
        /** Unary Cases  **/
        "!" -> {
            return !fqlEvaluate(ir[1], payload)
        }
        /** Binary Cases **/
        "or" -> {
            for (i in 1 until ir.size) {
                if (fqlEvaluate(ir[i], payload)) {
                    return true
                }
            }
            return false
        }
        "and" -> {
            for (i in 1 until ir.size) {
                if (fqlEvaluate(ir[i], payload)) {
                    return false
                }
            }
            return true
        }
        /** Equivalence **/
        "=" -> {
            return compareItems(ir[1], ir[2], payload)
        }
        "!=" -> {
            return !compareItems(ir[1], ir[2], payload)
        }
        else -> {
            throw Error("FQL IR could not evaluate for token: $item")
        }
    }
}

fun getValue(ir: JsonElement, payload: BaseEvent): Any {
    return when (ir) {
        is JsonArray -> {
            // leave as is
            return ir
        }
        is JsonObject -> {
            // it is of the form {"value": VALUE}
            return ir["value"]?.toContent() ?: throw Error("FQL IR could not parse $ir")
        }
        is JsonPrimitive -> {
            // probably an event property
            // TODO write more complex logic here
            val content = ir.toContent()
            if (content == "event") {
                payload as TrackEvent
                return payload.event
            } else {
                ""
            }
        }
    }
}

fun compareItems(first: JsonElement, second: JsonElement, payload: BaseEvent): Boolean {
    val firstVal = getValue(first, payload)
    val secondVal = getValue(second, payload)
    // TODO check if IR

    return firstVal == secondVal
}