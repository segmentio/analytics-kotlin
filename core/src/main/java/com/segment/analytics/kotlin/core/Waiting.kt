package com.segment.analytics.kotlin.core

import com.segment.analytics.kotlin.core.platform.Plugin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * An interface that provides functionality of pausing and resuming event processing on Analytics.
 *
 * By default plugins that implement this interface pauses processing when it is added to
 * analytics (via `setup()`) and resumes after 30s.
 *
 * To customize pausing and resuming, override `setup()` and call `pause()/resumes()` as needed
 */
interface WaitingPlugin: Plugin {
    override fun setup(analytics: Analytics) {
        super.setup(analytics)
        pause()
    }

    fun pause() {
        analytics.pauseEventProcessing(this)
    }

    fun resume() {
        analytics.resumeEventProcessing(this)
    }
}

fun Analytics.pauseEventProcessing(plugin: WaitingPlugin) = analyticsScope.launch(analyticsDispatcher) {
    store.dispatch(System.AddWaitingPlugin(plugin.hashCode()), System::class)
    pauseEventProcessing()
}


fun Analytics.resumeEventProcessing(plugin: WaitingPlugin) = analyticsScope.launch(analyticsDispatcher) {
    store.dispatch(System.RemoveWaitingPlugin(plugin.hashCode()), System::class)
    resumeEventProcessing()
}

internal suspend fun Analytics.running(): Boolean {
    val system = store.currentState(System::class)
    return system?.running ?: false
}

internal suspend fun Analytics.pauseEventProcessing(timeout: Long = 30_000) {
    if (!running()) return

    store.dispatch(System.ToggleRunningAction(false), System::class)
    startProcessingAfterTimeout(timeout)
}

internal suspend fun Analytics.resumeEventProcessing() {
    if (running()) return
    store.dispatch(System.ToggleRunningAction(true), System::class)
}

internal fun Analytics.startProcessingAfterTimeout(timeout: Long) = analyticsScope.launch(analyticsDispatcher) {
    delay(timeout)
    store.dispatch(System.ForceRunningAction(), System::class)
}

