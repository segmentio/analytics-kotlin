package com.segment.analytics.kotlin.core.platform.policies

import com.segment.analytics.kotlin.core.Analytics
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class FrequencyFlushPolicy(val flushIntervalInMillis: Long = 30_000): FlushPolicy {

    var flushJob: Job? = null
    var jobStarted: Boolean = false

    override fun schedule(analytics: Analytics) {

        if (!jobStarted) {
            jobStarted = true

            flushJob = analytics.analyticsScope.launch(analytics.fileIODispatcher) {

                if (flushIntervalInMillis > 0) {

                    while (isActive) {
                        analytics.flush()

                        // use delay to do periodical task
                        // this is doable in coroutine, since delay only suspends, allowing thread to
                        // do other work and then come back. see:
                        // https://github.com/Kotlin/kotlinx.coroutines/issues/1632#issuecomment-545693827
                        delay(flushIntervalInMillis)
                    }
                }
            }
        }
    }

    override fun unschedule() {
        if (jobStarted) {
            jobStarted = false
            flushJob?.cancel()
        }
    }

    override fun shouldFlush(): Boolean = false // Always return false; Scheduler will call flush.
}