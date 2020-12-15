package io.digitalmagic.test

import arrow.core.*
import arrow.core.extensions.fx
import java.time.Duration
import kotlin.test.*

typealias CallResult<T> = Either<Throwable, T>

inline fun<reified T> Iterable<CallResult<T>>.sequence(): CallResult<List<T>> =
    fold<CallResult<T>, CallResult<List<T>>>(Right(emptyList())) { accumulator, elem ->
        accumulator.flatMap { a ->
            elem.map { e ->
                a + e
            }
        }
    }

class ArrowTest {

    fun plainItemIds(): List<Int> = (1 until 100000).toList()
    fun plainItem(id: Int): String =
        when {
            id % 17 == 0 -> "$id is bad"
            else -> "$id is good"
        }

    fun getItemIds(): CallResult<List<Int>> = plainItemIds().right()
    fun getItem(id: Int): CallResult<String> = plainItem(id).right()

    @Test
    fun computeSequenceOfEitherWithFxLeadsToStackoverflow() {
        assertFailsWith(StackOverflowError::class) {
            CallResult.fx<Throwable, List<Int>> {
                val ids = getItemIds().bind()
                ids.filter {
                    val item = getItem(it).bind()
                    item.contains("bad")
                }
            }
        }
    }

    @Test
    fun computeSequenceOfEitherWithoutStackOverflow() {
        val idsWithFp =
            System.nanoTime().let { started ->
                val result = getItemIds().flatMap {
                    it.map { id ->
                        getItem(id).map {
                            id to it
                        }
                    }.sequence().map {
                        it.filter { it.second.contains("bad") }.map { it.first }
                    }
                }
                println("Done in ${Duration.ofNanos(System.nanoTime() - started).toMillis()} ms in FP")
                result
            }
        assertTrue(idsWithFp.isRight())
        assertTrue(idsWithFp.getOrElse { fail("never") }.isNotEmpty())

        val ids =
            System.nanoTime().let { started ->
                val ids = plainItemIds()
                val result = ids.map {
                    val item = plainItem(it)
                    it to item
                }.filter { it.second.contains("bad") }.map { it.first }
                println("Done in ${Duration.ofNanos(System.nanoTime() - started).toMillis()} ms in plain")
                result
            }

        assertEquals(ids, idsWithFp.getOrElse { fail("never") })
    }
}