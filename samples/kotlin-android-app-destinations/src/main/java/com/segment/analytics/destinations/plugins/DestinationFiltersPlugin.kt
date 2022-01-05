package com.segment.analytics.destinations.plugins

import com.segment.analytics.kotlin.core.BaseEvent
import com.segment.analytics.kotlin.core.TrackEvent
import com.segment.analytics.kotlin.core.utilities.toContent
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement

//
//class FQLRuntimePlugin(private val destinationKey: String) : Plugin {
//    override val type: Plugin.Type = Plugin.Type.Enrichment
//    override lateinit var analytics: Analytics
//
//    var fqlStore: FQLStore? = null
//    var rulesToApply: List<RoutingRule> = emptyList()
//
//    override fun update(settings: Settings, type: Plugin.UpdateType) {
//        super.update(settings, type)
//        updateFQLStore(JsonArray(emptyList()))
//    }
//
//    fun updateFQLStore(routingRules: JsonArray) {
//        fqlStore = FQLStore(routingRules)
//        fqlStore?.getRulesForDestination(destinationKey)?.let {
//            rulesToApply = it
//        }
//    }
//
//    // Implement only drop event
//    override fun execute(event: BaseEvent): BaseEvent? {
//        for (routingRule in rulesToApply) {
//            for (matcher in routingRule.matchers) {
//                val ir = Json.decodeFromString(JsonArray.serializer(), matcher.ir)
//                if (fqlEvaluate(ir, event)) {
//                    return null
//                }
//            }
//        }
//        return super.execute(event)
//    }
//}
//
//class FQLCompiledPlugin(private val destinationKey: String) : Plugin {
//    override val type: Plugin.Type = Plugin.Type.Enrichment
//    override lateinit var analytics: Analytics
//
//    var fqlStore: FQLStore? = null
//
//    override fun update(settings: Settings, type: Plugin.UpdateType) {
//        super.update(settings, type)
//        updateFQLStore(JsonArray(emptyList()))
//    }
//
//    fun updateFQLStore(routingRules: JsonArray) {
//        fqlStore = FQLStore(routingRules)
//    }
//
//    // Implement only drop event
//    override fun execute(event: BaseEvent): BaseEvent? {
//        fqlStore?.getRulesForDestination(destinationKey)?.let {
//            for (routingRule in it) {
//                for (matcher in routingRule.matchers) {
//                    val ir = Json.decodeFromString(JsonArray.serializer(), matcher.ir)
//                    if (fqlEvaluate(ir, event)) {
//                        return null
//                    }
//                }
//            }
//        }
//        return super.execute(event)
//    }
//}

typealias Predicate<T> = (payload: T) -> Boolean

fun compile(ir: JsonElement): Predicate<BaseEvent> {
    // check if the given value is literally `true`
    if (ir !is JsonArray) {
        return { event: BaseEvent ->
            getValue(ir, event) == true
        }
    }
    val item = ir[0].toContent()
    when (item) {
        /** Unary Cases  **/
        "!" -> {
            return { event: BaseEvent ->
                val filter = compile(ir[1])
                !filter(event)
            }
        }
        /** Binary Cases **/
        "or" -> {
            val predicates = (1 until ir.size).map {
                compile(ir[it])
            }
            return { event: BaseEvent ->
                var result = false
                for (predicate in predicates) {
                    if (predicate(event)) {
                        result = true
                        break
                    }
                }
                result
            }
        }
        "and" -> {
            val predicates = (1 until ir.size).map {
                compile(ir[it])
            }
            return { event: BaseEvent ->
                var result = true
                for (predicate in predicates) {
                    if (!predicate(event)) {
                        result = false
                        break
                    }
                }
                result
            }
        }
        /** Equivalence **/
        "=" -> {
            return { event: BaseEvent ->
                getValue(ir[1], event) == getValue(ir[2], event)
            }
        }
        "!=" -> {
            return { event: BaseEvent ->
                getValue(ir[1], event) != getValue(ir[2], event)
            }
        }
        else -> {
            throw Error("FQL IR could not evaluate for token: $item")
        }
    }
}

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

@Serializable
data class Matcher(
    val ir: String,
    val type: String,
)

@Serializable
data class RoutingRule(
    val matchers: List<Matcher>,
    val destinationName: String,
)

class FQLStore(routingRules: JsonArray) {
    val rules: List<RoutingRule> = Json.decodeFromJsonElement(routingRules)

    fun getRulesForDestination(key: String): List<RoutingRule> {
        return rules.filter { it.destinationName == key }
    }
}