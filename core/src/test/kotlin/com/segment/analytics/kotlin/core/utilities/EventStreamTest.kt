package com.segment.analytics.kotlin.core.utilities

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File
import java.util.UUID

class EventStreamTest {

    @Nested
    inner class InMemoryEventStreamTest {

        private lateinit var eventStream: EventStream;

        @BeforeEach
        fun setup() {
            eventStream = InMemoryEventStream()
        }

        @Test
        fun lengthTest() {
            val str1 = "abc"
            val str2 = "defgh"

            assertEquals(0, eventStream.length)

            eventStream.openOrCreate("test")
            eventStream.write(str1)
            assertEquals(str1.length, eventStream.length)

            eventStream.write(str2)
            assertEquals(str1.length + str2.length, eventStream.length)
        }

        @Test
        fun isOpenTest() {
            assertFalse(eventStream.isOpened)

            eventStream.openOrCreate("test")
            assertTrue(eventStream.isOpened)

            eventStream.close()
            assertFalse(eventStream.isOpened)
        }

        @Test
        fun openOrCreateTest() {
            var actual = eventStream.openOrCreate("test")
            assertTrue(actual)

            actual = eventStream.openOrCreate("test")
            assertFalse(actual)
        }

        @Test
        fun writeAndReadStreamTest() {
            val file = "test"
            eventStream.openOrCreate(file)
            val str1 = "abc"
            val str2 = "defgh"

            assertEquals(0, eventStream.length)

            eventStream.write(str1)
            assertEquals(str1.toByteArray(), eventStream.readAsStream(file)!!.readBytes())
            eventStream.write(str2)
            assertEquals((str1 + str2).toByteArray(), eventStream.readAsStream(file)!!.readBytes())
        }

        @Test
        fun readTest() {
            val files = arrayOf("test1.tmp", "test2", "test3.tmp")

            eventStream.openOrCreate("test1")

            // open test2 without finish test1
            eventStream.openOrCreate("test2")
            eventStream.finishAndClose {
                removeFileExtension(it)
            }

            // open test3 after finish test2
            eventStream.openOrCreate("test3")
            // open test3 again
            eventStream.openOrCreate("test3")

            val actual = HashSet<String>(eventStream.read())
            assertEquals(files.size, actual.size)
            assertTrue(actual.contains(files[0]))
            assertTrue(actual.contains(files[1]))
            assertTrue(actual.contains(files[2]))
        }

        @Test
        fun removeTest() {
            eventStream.openOrCreate("test")
            eventStream.finishAndClose {
                removeFileExtension(it)
            }
            eventStream.remove("test")
            val newFile = eventStream.openOrCreate("test")

            assertTrue(newFile)
        }

        @Test
        fun closeTest() {
            eventStream.openOrCreate("test")
            assertTrue(eventStream.isOpened)

            eventStream.close()
            assertFalse(eventStream.isOpened)
        }

        @Test
        fun finishAndCloseTest() {
            eventStream.openOrCreate("test")
            eventStream.finishAndClose {
                removeFileExtension(it)
            }

            val files = eventStream.read()
            assertEquals(1, files.size)
            assertEquals("test", files[0])
            assertFalse(eventStream.isOpened)
        }
    }

    @Nested
    inner class FileEventStreamTest {
        private lateinit var eventStream: EventStream

        private lateinit var dir: File

        @BeforeEach
        fun setup() {
            dir = File(UUID.randomUUID().toString())
            eventStream = FileEventStream(dir)
        }

        @AfterEach
        fun tearDown() {
            dir.deleteRecursively()
        }


        @Test
        fun lengthTest() {
            val str1 = "abc"
            val str2 = "defgh"

            assertEquals(0, eventStream.length)

            eventStream.openOrCreate("test")
            eventStream.write(str1)
            assertEquals(str1.length, eventStream.length)

            eventStream.write(str2)
            assertEquals(str1.length + str2.length, eventStream.length)
        }

        @Test
        fun isOpenTest() {
            assertFalse(eventStream.isOpened)

            eventStream.openOrCreate("test")
            assertTrue(eventStream.isOpened)

            eventStream.close()
            assertFalse(eventStream.isOpened)
        }

        @Test
        fun openOrCreateTest() {
            var actual = eventStream.openOrCreate("test")
            assertTrue(actual)

            actual = eventStream.openOrCreate("test")
            assertFalse(actual)
        }

        @Test
        fun writeAndReadStreamTest() {
            val str1 = "abc"
            val str2 = "defgh"

            eventStream.openOrCreate("test")
            assertEquals(0, eventStream.length)
            var files = eventStream.read()
            assertEquals(1, files.size)
            eventStream.write(str1)
            eventStream.close()
            assertEquals(str1.toByteArray(), eventStream.readAsStream(files[0])!!.readBytes())

            eventStream.openOrCreate("test")
            assertEquals(str1.length, eventStream.length)
            files = eventStream.read()
            assertEquals(1, files.size)
            eventStream.write(str2)
            eventStream.close()
            assertEquals((str1 + str2).toByteArray(), eventStream.readAsStream(files[0])!!.readBytes())
        }

        @Test
        fun readTest() {
            val files = arrayOf("test1.tmp", "test2", "test3.tmp")

            eventStream.openOrCreate("test1")

            // open test2 without finish test1
            eventStream.openOrCreate("test2")
            eventStream.finishAndClose {
                removeFileExtension(it)
            }

            // open test3 after finish test2
            eventStream.openOrCreate("test3")
            // open test3 again
            eventStream.openOrCreate("test3")

            val actual = HashSet<String>(eventStream.read())
            assertEquals(files.size, actual.size)
            assertTrue(actual.contains(files[0]))
            assertTrue(actual.contains(files[1]))
            assertTrue(actual.contains(files[2]))
        }

        @Test
        fun removeTest() {
            eventStream.openOrCreate("test")
            eventStream.finishAndClose {
                removeFileExtension(it)
            }
            eventStream.remove("test")
            val newFile = eventStream.openOrCreate("test")

            assertTrue(newFile)
        }

        @Test
        fun closeTest() {
            eventStream.openOrCreate("test")
            assertTrue(eventStream.isOpened)

            eventStream.close()
            assertFalse(eventStream.isOpened)
        }

        @Test
        fun finishAndCloseTest() {
            eventStream.openOrCreate("test")
            eventStream.finishAndClose {
                removeFileExtension(it)
            }

            val files = eventStream.read()
            assertEquals(1, files.size)
            assertEquals("test", files[0])
            assertFalse(eventStream.isOpened)
        }
    }
}