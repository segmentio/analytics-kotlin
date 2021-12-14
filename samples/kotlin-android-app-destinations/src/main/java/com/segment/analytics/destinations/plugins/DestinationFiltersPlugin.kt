package com.segment.analytics.destinations.plugins

import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.BaseEvent
import com.segment.analytics.kotlin.core.platform.Plugin

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

class Equals(private val t1: Any, private val t2: Any): Expression {
    override fun evaluate(event: BaseEvent): Boolean {
        return t1 == t2
    }
}

class Path(private val path: String): Expression {
    override fun evaluate(event: BaseEvent): Boolean {
        return
    }
}

// Not(Or(Equals(payload.event, "CP VOD - Start video"), Equals(payload.event, "CP VOD - Track video")))