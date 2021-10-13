package com.segment.analytics.kotlin.core

@RequiresOptIn(
    level = RequiresOptIn.Level.WARNING,
    message = "This method invokes `runBlocking` internal, it's not recommended to be used in coroutines."
)
annotation class BlockingApi