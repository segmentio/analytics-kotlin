package com.segment.analytics.kotlin.core.platform

import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.BaseEvent
import com.segment.analytics.kotlin.core.ScreenEvent
import com.segment.analytics.kotlin.core.emptyJsonObject
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

internal class MediatorTest {

    private val multiThreadDispatcher = Executors.newFixedThreadPool(4).asCoroutineDispatcher()
    private val singleThreadDispatcherA = Executors.newFixedThreadPool(1).asCoroutineDispatcher()
    private val singleThreadDispatcherB = Executors.newFixedThreadPool(1).asCoroutineDispatcher()

    companion object {
        private val event1 = ScreenEvent("event 1", "", emptyJsonObject).apply {
            messageId = "qwerty-1234"
            anonymousId = "anonId"
            integrations = emptyJsonObject
            context = emptyJsonObject
            timestamp = Date(0).toInstant().toString()
        }

        private val event2 = ScreenEvent("event 2", "", emptyJsonObject).apply {
            messageId = "qwerty-1234"
            anonymousId = "anonId"
            integrations = emptyJsonObject
            context = emptyJsonObject
            timestamp = Date(0).toInstant().toString()
        }

        open class BasePlugin : Plugin {
            override val type: Plugin.Type = Plugin.Type.Enrichment
            override lateinit var analytics: Analytics

            override fun execute(event: BaseEvent): BaseEvent? {
                return event
            }
        }

        class PluginA : BasePlugin()
        class PluginB : BasePlugin()
        class PluginC : BasePlugin()

        class ExceptionPlugin : BasePlugin() {
            override fun execute(event: BaseEvent): BaseEvent? {
                throw Exception("Boom! Exception Plugin Threw Exception!")
            }
        }

        class ErrorPlugin : BasePlugin() {
            override fun execute(event: BaseEvent): BaseEvent? {
                throw Error("Boom! Exception Plugin Threw Exception!")
            }
        }

        class ThrowablePlugin : BasePlugin() {
            override fun execute(event: BaseEvent): BaseEvent? {
                throw Throwable("Boom! Exception Plugin Threw Exception!")
            }
        }
    }

    @BeforeEach
    internal fun setUp() {
    }

    @Test
    fun `is initialized correctly`() {
        // No arg constructor
        val mediator = Mediator()
        assertNotNull(mediator.plugins)
        assertTrue(mediator.plugins.isEmpty())

        // With an empty list
        val emptyMediator = Mediator(CopyOnWriteArrayList())
        assertNotNull(emptyMediator.plugins)
        assertTrue(emptyMediator.plugins.isEmpty())

        // With a non-empty list
        val list = CopyOnWriteArrayList<Plugin>()
        val pluginA = PluginA()
        list.add(pluginA)
        val pluginB = PluginB()
        list.add(pluginB)
        val nonEmptyMediator = Mediator(list)
        assertNotNull(nonEmptyMediator.plugins)
        assertEquals(2, nonEmptyMediator.plugins.size)
        assertEquals(pluginA, nonEmptyMediator.plugins[0])
        assertEquals(pluginB, nonEmptyMediator.plugins[1])
    }

    @Test
    fun `can add plugins`() = runBlocking {
        val mediator = Mediator()

        assertTrue(mediator.plugins.isEmpty())

        for (i in 1..5) {
            mediator.add(PluginA())
        }

        assertEquals(5, mediator.plugins.size)
    }

    @Test
    fun `can remove plugins`() = runBlocking {
        val list = CopyOnWriteArrayList<Plugin>()
        val pluginA = PluginA()
        val pluginB = PluginB()
        list.add(pluginA)
        list.add(pluginB)
        val mediator = Mediator(list)

        assertEquals(2, mediator.plugins.size)
        mediator.remove(pluginA)
        assertEquals(1, mediator.plugins.size)
        assertEquals(pluginB, mediator.plugins[0])
        mediator.remove(pluginB)
        assertTrue(mediator.plugins.isEmpty())
    }

    @Test
    fun `can add plugins concurrently`() = runBlocking {
        val mediator = Mediator()
        assertTrue(mediator.plugins.isEmpty())

        val scope = CoroutineScope(Job() + multiThreadDispatcher)
        val jobA = scope.launch {
            for (i in 1..5) {
                mediator.add(PluginA())
                delay(50)
            }
        }

        val jobB = scope.launch {
            for (i in 1..5) {
                delay(500)
                mediator.add(PluginB())
            }
        }

        joinAll(jobA, jobB)

        assertEquals(10, mediator.plugins.size)
    }

    @Test
    fun `can remove plugins concurrently`() = runBlocking {
        val list = CopyOnWriteArrayList<Plugin>()
        val pluginA1 = PluginA()
        val pluginA2 = PluginA()
        val pluginA3 = PluginA()
        val pluginB1 = PluginB()
        val pluginB2 = PluginB()
        val pluginB3 = PluginB()
        val pluginC1 = PluginC()
        val pluginC2 = PluginC()
        val pluginC3 = PluginC()

        list.add(pluginA1)
        list.add(pluginA2)
        list.add(pluginA3)
        list.add(pluginB1)
        list.add(pluginB2)
        list.add(pluginB3)
        list.add(pluginC1)
        list.add(pluginC2)
        list.add(pluginC3)

        val mediator = Mediator(list)

        // Use two single threaded dispatchers to ensure running on different threads.
        val scopeA = CoroutineScope(Job() + singleThreadDispatcherA)
        val scopeB = CoroutineScope(Job() + singleThreadDispatcherB)

        val jobA = scopeA.launch {
            delay(50)
            mediator.remove(pluginA1)
            delay(50)
            mediator.remove(pluginA2)
            delay(50)
            mediator.remove(pluginA3)
        }

        val jobB = scopeB.launch {
            delay(50)
            mediator.remove(pluginB1)
            delay(50)
            mediator.remove(pluginB2)
            delay(50)
            mediator.remove(pluginB3)
        }

        joinAll(jobA, jobB)

        assertEquals(3, mediator.plugins.size)
        assertTrue(mediator.plugins.contains(pluginC1))
        assertTrue(mediator.plugins.contains(pluginC2))
        assertTrue(mediator.plugins.contains(pluginC3))
    }

    @Test
    fun `can remove and add plugins concurrently`() = runBlocking {

        val list = CopyOnWriteArrayList<Plugin>()
        val pluginA1 = PluginA()
        val pluginA2 = PluginA()
        val pluginA3 = PluginA()
        val pluginB1 = PluginB()
        val pluginB2 = PluginB()
        val pluginB3 = PluginB()
        val pluginC1 = PluginC()
        val pluginC2 = PluginC()
        val pluginC3 = PluginC()

        list.add(pluginA1)
        list.add(pluginA2)
        list.add(pluginA3)
        // Don't add "B" plugins let the coroutine do that
        list.add(pluginC1)
        list.add(pluginC2)
        list.add(pluginC3)

        val mediator = Mediator(list)

        // Use two single threaded dispatchers to ensure running on different threads.
        val scopeA = CoroutineScope(Job() + singleThreadDispatcherA)
        val scopeB = CoroutineScope(Job() + singleThreadDispatcherB)

        val jobA = scopeA.launch {
            delay(50)
            mediator.remove(pluginA1)
            delay(50)
            mediator.remove(pluginA2)
            delay(50)
            mediator.remove(pluginA3)
        }

        val jobB = scopeB.launch {
            delay(50)
            mediator.add(pluginB1)
            delay(50)
            mediator.add(pluginB2)
            delay(50)
            mediator.add(pluginB3)
        }

        joinAll(jobA, jobB)

        assertEquals(6, mediator.plugins.size)
        assertTrue(mediator.plugins.contains(pluginB1))
        assertTrue(mediator.plugins.contains(pluginB2))
        assertTrue(mediator.plugins.contains(pluginB3))
        assertTrue(mediator.plugins.contains(pluginC1))
        assertTrue(mediator.plugins.contains(pluginC2))
        assertTrue(mediator.plugins.contains(pluginC3))
    }

    @Test
    fun `catches all exception and errors from plugins`() {
        val mediator = Mediator()

        val pluginA = mockk<PluginA>()
        val pluginB = mockk<PluginB>()
        val pluginC = mockk<PluginC>()

        mediator.plugins.add(pluginA)
        mediator.plugins.add(ExceptionPlugin())
        mediator.plugins.add(ErrorPlugin())
        mediator.plugins.add(pluginB)
        mediator.plugins.add(ThrowablePlugin())
        mediator.plugins.add(pluginC)

        assertDoesNotThrow {
            mediator.execute(event1)
        }

        // Make sure the rest of the plugins were called.
        verify {
            pluginA.execute(event1)
            pluginB.execute(event1)
            pluginC.execute(event1)
        }
    }

    @Test
    fun `applyClosure applies to all plugins`() {
        val list = CopyOnWriteArrayList<Plugin>()
        val pluginA = mockk<PluginA>()
        val pluginB = mockk<PluginB>()
        val pluginC = mockk<PluginC>()
        list.add(pluginA)
        list.add(pluginB)
        list.add(pluginC)
        val mediator = Mediator(list)

        mediator.applyClosure { p -> p.toString() }

        verify {
            pluginA.toString()
            pluginB.toString()
            pluginC.toString()
        }
    }

    @Test
    fun `find returns the right plugin`() {
        val list = CopyOnWriteArrayList<Plugin>()

        val pluginA = PluginA()
        val pluginB = PluginB()
        val pluginC1 = PluginC()
        val pluginC2 = PluginC()

        list.add(pluginA)
        list.add(pluginB)
        list.add(pluginC1)
        list.add(pluginC2)

        val mediator = Mediator(list)

        assertEquals(pluginA, mediator.find(PluginA::class))
        assertEquals(pluginB, mediator.find(PluginB::class))
        assertEquals(pluginC1, mediator.find(PluginC::class))
    }

    @Test
    fun `findAll returns the matching plugins`() {
        val list = CopyOnWriteArrayList<Plugin>()

        val pluginA = PluginA()
        val pluginB1 = PluginB()
        val pluginB2 = PluginB()
        val pluginC1 = PluginC()
        val pluginC2 = PluginC()

        list.add(pluginA)
        list.add(pluginB1)
        list.add(pluginB2)
        list.add(pluginC1)
        list.add(pluginC2)

        val mediator = Mediator(list)

        assertEquals(listOf(pluginA), mediator.findAll(PluginA::class))
        assertEquals(listOf(pluginB1, pluginB2), mediator.findAll(PluginB::class))
        assertEquals(listOf(pluginC1, pluginC2), mediator.findAll(PluginC::class))
    }
}