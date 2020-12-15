package io.digitalmagic.test

import arrow.core.*
import arrow.core.extensions.fx
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

    fun getItemIds(): CallResult<List<Int>> = (1 until 100000).toList().right()

    fun getItem(id: Int): CallResult<String> =
        when {
            id % 17 == 0 -> "$id is bad"
            else -> "$id is good"
        }.right()

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
        val ids = getItemIds().flatMap {
            it.map { id ->
                getItem(id).map {
                    id to it
                }
            }.sequence().map {
                it.filter { it.second.contains("bad") }.map { it.first }
            }
        }
        assertTrue(ids.isRight())
        assertTrue(ids.getOrElse { fail("never") }.isNotEmpty())
    }
}