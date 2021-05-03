package com.segment.analytics.main.utils

import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class SynchronousExecutor : AbstractExecutorService() {
    private val terminated = AtomicBoolean(false)
    override fun shutdown() {
        terminated.set(true)
    }

    override fun shutdownNow(): List<Runnable> {
        return emptyList()
    }

    override fun isShutdown(): Boolean {
        return terminated.get()
    }

    override fun isTerminated(): Boolean {
        return terminated.get()
    }

    @Throws(InterruptedException::class)
    override fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean {
        return false
    }

    override fun execute(command: Runnable) {
        command.run()
    }
}