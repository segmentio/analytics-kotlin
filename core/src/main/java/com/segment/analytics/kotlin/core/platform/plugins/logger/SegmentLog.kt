package com.segment.analytics.kotlin.core.platform.plugins.logger

import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.BaseEvent
import com.segment.analytics.kotlin.core.Settings
import com.segment.analytics.kotlin.core.platform.EventPlugin
import com.segment.analytics.kotlin.core.platform.Plugin
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive
import java.lang.Exception
import java.util.*
import kotlin.reflect.KClass

// Internal log usage
fun Analytics.Companion.segmentLog(message: String, kind: LogFilterKind = LogFilterKind.ERROR, function: String? = null, line: Int? = null) {
   val logTarget = Analytics.staticLogTarget
   val logMessage = LogMessage(kind, message=message)
   when (kind){
      LogFilterKind.DEBUG -> {
         if (Analytics.debugLogsEnabled) {
            logTarget.parseLog(logMessage)
         }
      }
      else -> logTarget.parseLog(logMessage)
   }
}
