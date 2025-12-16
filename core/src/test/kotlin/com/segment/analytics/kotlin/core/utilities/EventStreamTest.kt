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

            eventStream.openOrCreate("test.tmp")
            eventStream.write(str1)
            assertEquals(str1.length * 1L, eventStream.length)

            eventStream.write(str2)
            assertEquals(str1.length + str2.length * 1L, eventStream.length)
        }

        @Test
        fun isOpenTest() {
            assertFalse(eventStream.isOpened)

            eventStream.openOrCreate("test.tmp")
            assertTrue(eventStream.isOpened)

            eventStream.close()
            assertFalse(eventStream.isOpened)
        }

        @Test
        fun openOrCreateTest() {
            var actual = eventStream.openOrCreate("test.tmp")
            assertTrue(actual)

            actual = eventStream.openOrCreate("test.tmp")
            assertFalse(actual)
        }

        @Test
        fun writeAndReadStreamTest() {
            val file = "test.tmp"
            eventStream.openOrCreate(file)
            val str1 = "abc"
            val str2 = "defgh"

            assertEquals(0, eventStream.length)

            eventStream.write(str1)
            assertEquals(str1, eventStream.readAsStream(file)!!.bufferedReader().use { it.readText() })
            eventStream.write(str2)
            assertEquals((str1 + str2), eventStream.readAsStream(file)!!.bufferedReader().use { it.readText() })
        }

        @Test
        fun readTest() {
            val files = arrayOf("test1.tmp", "test2", "test3.tmp")

            eventStream.openOrCreate("test1.tmp")

            // open test2 without finish test1
            eventStream.openOrCreate("test2.tmp")
            eventStream.finishAndClose {
                removeFileExtension(it)
            }

            // open test3 after finish test2
            eventStream.openOrCreate("test3.tmp")
            // open test3 again
            eventStream.openOrCreate("test3.tmp")

            val actual = HashSet<String>(eventStream.read())
            assertEquals(files.size, actual.size)
            assertTrue(actual.contains(files[0]))
            assertTrue(actual.contains(files[1]))
            assertTrue(actual.contains(files[2]))
        }

        @Test
        fun removeTest() {
            eventStream.openOrCreate("test.tmp")
            eventStream.finishAndClose {
                removeFileExtension(it)
            }
            eventStream.remove("test")
            val newFile = eventStream.openOrCreate("test.tmp")

            assertTrue(newFile)
        }

        @Test
        fun closeTest() {
            eventStream.openOrCreate("test.tmp")
            assertTrue(eventStream.isOpened)

            eventStream.close()
            assertFalse(eventStream.isOpened)
        }

        @Test
        fun finishAndCloseTest() {
            eventStream.openOrCreate("test.tmp")
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

            eventStream.openOrCreate("test.tmp")
            eventStream.write(str1)
            assertEquals(str1.length * 1L, eventStream.length)

            eventStream.write(str2)
            assertEquals(str1.length + str2.length * 1L, eventStream.length)
        }

        @Test
        fun isOpenTest() {
            assertFalse(eventStream.isOpened)

            eventStream.openOrCreate("test.tmp")
            assertTrue(eventStream.isOpened)

            eventStream.close()
            assertFalse(eventStream.isOpened)
        }

        @Test
        fun openOrCreateTest() {
            var actual = eventStream.openOrCreate("test.tmp")
            assertTrue(actual)
            assertTrue(File(dir, "test.tmp").exists())

            actual = eventStream.openOrCreate("test.tmp")
            assertFalse(actual)
        }

        @Test
        fun writeAndReadStreamTest() {
            val str1 = "abc"
            val str2 = "defgh"

            eventStream.openOrCreate("test.tmp")
            assertEquals(0, eventStream.length)
            var files = eventStream.read()
            assertEquals(1, files.size)
            eventStream.write(str1)
            eventStream.close()
            assertEquals(str1, eventStream.readAsStream(files[0])!!.bufferedReader().use { it.readText() })

            eventStream.openOrCreate("test.tmp")
            assertEquals(str1.length * 1L, eventStream.length)
            files = eventStream.read()
            assertEquals(1, files.size)
            eventStream.write(str2)
            eventStream.close()
            assertEquals((str1 + str2), eventStream.readAsStream(files[0])!!.bufferedReader().use { it.readText() })
        }

        @Test
        fun readTest() {
            val files = arrayOf("test1.tmp", "test2", "test3.tmp")

            eventStream.openOrCreate("test1.tmp")

            // open test2 without finish test1
            eventStream.openOrCreate("test2.tmp")
            eventStream.finishAndClose {
                removeFileExtension(it)
            }

            // open test3 after finish test2
            eventStream.openOrCreate("test3.tmp")
            // open test3 again
            eventStream.openOrCreate("test3.tmp")

            val actual = HashSet<String>(eventStream.read().map { it.substring(it.lastIndexOf('/') + 1) })
            assertEquals(files.size, actual.size)
            assertTrue(actual.contains(files[0]))
            assertTrue(actual.contains(files[1]))
            assertTrue(actual.contains(files[2]))
        }

        @Test
        fun removeTest() {
            eventStream.openOrCreate("test.tmp")
            eventStream.finishAndClose {
                removeFileExtension(it)
            }
            assertTrue(File(dir, "test").exists())

            eventStream.remove(File(dir, "test").absolutePath)
            assertFalse(File(dir, "test").exists())

            val newFile = eventStream.openOrCreate("test.tmp")

            assertTrue(newFile)
        }

        @Test
        fun closeTest() {
            eventStream.openOrCreate("test.tmp")
            assertTrue(eventStream.isOpened)

            eventStream.close()
            assertFalse(eventStream.isOpened)
        }

        @Test
        fun finishAndCloseTest() {
            eventStream.openOrCreate("test.tmp")
            eventStream.finishAndClose {
                removeFileExtension(it)
            }

            val files = eventStream.read().map { it.substring(it.lastIndexOf('/') + 1) }
            assertEquals(1, files.size)
            assertEquals("test", files[0])
            assertFalse(eventStream.isOpened)
        }

        @Test
        fun readLimitsTo1000FilesTest() {
            // Create 1250 files in the directory
            for (i in 1..1250) {
                File(dir, "test$i.tmp").createNewFile()
            }

            val files = eventStream.read()

            // Verify that read() returns at most 1000 files
            assertTrue(files.size <= 1000, "Expected at most 1000 files, but got ${files.size}")

            // Verify all returned paths are valid files
            files.forEach { path ->
                assertTrue(File(path).exists(), "File $path should exist")
                assertTrue(File(path).isFile, "Path $path should be a file")
            }
        }

        @Test
        fun readReturnsAllFilesWhenUnder1000Test() {
            // Create 50 files in the directory
            for (i in 1..50) {
                File(dir, "test$i.tmp").createNewFile()
            }

            val files = eventStream.read()

            // Verify that all 50 files are returned when count is under 1000
            assertEquals(50, files.size, "Expected 50 files, but got ${files.size}")

            // Verify all returned paths are valid files
            files.forEach { path ->
                assertTrue(File(path).exists(), "File $path should exist")
                assertTrue(File(path).isFile, "Path $path should be a file")
            }
        }
    }
}