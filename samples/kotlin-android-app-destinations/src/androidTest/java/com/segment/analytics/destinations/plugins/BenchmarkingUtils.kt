package com.segment.analytics.destinations.plugins

import java.util.ArrayList
import kotlin.system.measureTimeMillis

/**
 * Iterates provided by [callback] code [ITERATIONS]x[TEST_COUNT] times.
 * Performs warming by iterating [ITERATIONS]x[WARM_COUNT] times.
 */
fun simpleMeasureTest(
    ITERATIONS: Int = 1000,
    TEST_COUNT: Int = 10,
    WARM_COUNT: Int = 2,
    callback: () -> Unit,
): Pair<Long, Long> {
    val results = ArrayList<Long>()
    var totalTime = 0L
    var t = 0

    println("$PRINT_REFIX -> go")

    while (++t <= TEST_COUNT + WARM_COUNT) {
        var i = 0
        val time = measureTimeMillis {
            while (i++ < ITERATIONS)
                callback()
        }

        if (t <= WARM_COUNT) {
            println("$PRINT_REFIX Warming $t of $WARM_COUNT")
            continue
        }

        println(PRINT_REFIX + " " + time.toString() + "ms")

        results.add(time)
        totalTime += time
    }

    results.sort()

    val average = totalTime / TEST_COUNT
    val median = results[results.size / 2]

    println("$PRINT_REFIX -> average=${average}ms / median=${median}ms")
    return Pair(average, median)
}

/**
 * Used to filter console messages easily
 */
private val PRINT_REFIX = "[TimeTest]"

fun compare(
    ITERATIONS: Int = 1000,
    TEST_COUNT: Int = 10,
    WARM_COUNT: Int = 2,
    callback1: () -> Unit,
    callback2: () -> Unit,
) {
    val x = simpleMeasureTest(ITERATIONS, TEST_COUNT, WARM_COUNT) { callback1() }
    val y = simpleMeasureTest(ITERATIONS, TEST_COUNT, WARM_COUNT) { callback2() }

    if (x.first < y.first) {
        println("On Average x is faster. x:${x.first} & y:${y.first}")
    } else {
        println("On Average y is faster. x:${x.first} & y:${y.first}")
    }

    if (x.second < y.second) {
        println("X's median is faster. x:${x.first} & y:${y.first}")
    } else {
        println("Y's median is faster. x:${x.first} & y:${y.first}")
    }

}