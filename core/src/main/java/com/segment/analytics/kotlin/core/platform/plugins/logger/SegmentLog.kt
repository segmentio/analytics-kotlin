package com.segment.analytics.kotlin.core.platform.plugins.logger

import com.segment.analytics.kotlin.core.Analytics

// Internal log usage
fun Analytics.Companion.segmentLog(message: String, kind: LogKind = LogKind.ERROR) {
   val logger = logger
   val logMessage = LogMessage(kind, message=message)
   when (kind){
      LogKind.DEBUG -> {
         if (debugLogsEnabled) {
            logger.parseLog(logMessage)
         }
      }
      else -> logger.parseLog(logMessage)
   }
}
