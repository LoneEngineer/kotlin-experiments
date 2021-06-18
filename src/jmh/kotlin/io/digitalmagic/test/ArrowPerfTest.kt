package io.digitalmagic.test

import arrow.core.*
import arrow.core.computations.either
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.openjdk.jmh.annotations.*

// ATTENTION: comment out everything about java.money before JMH tests - does not work together

@State(Scope.Benchmark)
open class ArrowPerfTest {
    fun plainItemIds(): List<Int> = (1 until 100).toList()
    fun plainItem(id: Int): String =
        when {
            id % 13 == 0 -> "$id is bad"
            else -> "$id is good"
        }

    fun getItemIds(): CallResult<List<Int>> = plainItemIds().right()
    fun getItem(id: Int): CallResult<String> = plainItem(id).right()

    @Benchmark
    @Warmup(iterations = 5, batchSize = 10)
    @Measurement(iterations = 10, batchSize = 20)
    @BenchmarkMode(Mode.Throughput)
    fun pureKotlin() {
        plainItemIds()
            .map { it to plainItem(it) }
            .filter { it.second.contains("bad") }
            .map { it.first }
    }

    @Benchmark
    @Warmup(iterations = 5, batchSize = 10)
    @Measurement(iterations = 10, batchSize = 20)
    @BenchmarkMode(Mode.Throughput)
    fun pureFlatMap() {
        getItemIds().flatMap {
            it.map { id -> getItem(id).map { id to it } }
                .sequence()
                .map {
                    it.filter { it.second.contains("bad") }
                        .map { it.first }
                }
        }
    }

    @Benchmark
    @Warmup(iterations = 5, batchSize = 10)
    @Measurement(iterations = 10, batchSize = 20)
    @BenchmarkMode(Mode.Throughput)
    fun eitherBind1() {
        runBlocking(Dispatchers.Unconfined) {
            either<Throwable, List<Int>> {
                val ids = getItemIds().bind()
                val items = ids.map { id -> id to getItem(id).bind() }
                items.filter { it.second.contains("bad") }.map { it.first }
            }
        }
    }

    @Benchmark
    @Warmup(iterations = 5, batchSize = 10)
    @Measurement(iterations = 10, batchSize = 20)
    @BenchmarkMode(Mode.Throughput)
    fun eitherBind2() {
        runBlocking(Dispatchers.Unconfined) {
            either<Throwable, List<Int>> {
                val ids = getItemIds().bind()
                val items = ids.map { id -> getItem(id).map { id to it } }.sequence().bind()
                items.filter { it.second.contains("bad") }.map { it.first }
            }
        }
    }
}
