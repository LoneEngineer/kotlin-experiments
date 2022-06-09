package io.digitalmagic.test

import arrow.core.*
import arrow.core.computations.either
import kotlinx.coroutines.runBlocking
import java.time.Duration
import kotlin.test.*

typealias CallResult<T> = Either<Throwable, T>

inline fun<reified T> Iterable<CallResult<T>>.sequence(): CallResult<List<T>> =
    fold<CallResult<T>, CallResult<List<T>>>(Either.Right(emptyList())) { accumulator, elem ->
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

    @Test // see jmh package for precise performance testing
    fun computeSequenceOfEitherWithoutStackOverflow() {
        System.nanoTime().let { started ->
            val ids = plainItemIds()
            val result = ids.map {
                val item = plainItem(it)
                it to item
            }.filter { it.second.contains("bad") }.map { it.first }
            println("Done in ${Duration.ofNanos(System.nanoTime() - started).toMillis()} ms in warm-up")
            result
        }

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

        val idsEitherBind0 =
            System.nanoTime().let { started ->
                val result = runBlocking {
                    either<Throwable, List<Int>> {
                        val ids = getItemIds().bind()
                        val items = ids.map { id -> id to getItem(id).bind() }
                        items.filter { it.second.contains("bad") }.map { it.first }
                    }
                }
                println("Done in ${Duration.ofNanos(System.nanoTime() - started).toMillis()} ms in either bind")
                result
            }

        val idsEitherBind1 =
            System.nanoTime().let { started ->
                val result = either.eager<Throwable, List<Int>> {
                    val ids = getItemIds().bind()
                    val items = ids.map { id -> id to getItem(id).bind() }
                    items.filter { it.second.contains("bad") }.map { it.first }
                }
                println("Done in ${Duration.ofNanos(System.nanoTime() - started).toMillis()} ms in either.eiger bind")
                result
            }

        val idsEitherBind2 =
            System.nanoTime().let { started ->
                val result = runBlocking {
                    either<Throwable, List<Int>> {
                        val ids = getItemIds().bind()
                        val items = ids.map { id -> getItem(id).map { id to it } }.sequence().bind()
                        items.filter { it.second.contains("bad") }.map { it.first }
                    }
                }
                println("Done in ${Duration.ofNanos(System.nanoTime() - started).toMillis()} ms in either sequence bind")
                result
            }

        val idsEitherBind3 =
            System.nanoTime().let { started ->
                val result = either.eager<Throwable, List<Int>> {
                    val ids = getItemIds().bind()
                    val items = ids.map { id -> getItem(id).map { id to it } }.sequence().bind()
                    items.filter { it.second.contains("bad") }.map { it.first }
                }
                println("Done in ${Duration.ofNanos(System.nanoTime() - started).toMillis()} ms in either.eiger sequence bind")
                result
            }

        assertNotNull(idsEitherBind0.orNull())
        assertNotNull(idsEitherBind1.orNull())
        assertNotNull(idsEitherBind2.orNull())
        assertNotNull(idsEitherBind3.orNull())
        assertNotNull(idsWithFp.orNull())
        assertEquals(ids, idsWithFp.orNull())
        assertEquals(ids, idsEitherBind0.orNull())
        assertEquals(ids, idsEitherBind1.orNull())
        assertEquals(ids, idsEitherBind2.orNull())
        assertEquals(ids, idsEitherBind3.orNull())
    }
}
