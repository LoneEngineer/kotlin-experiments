package io.digitalmagic.test

class WithLazy {
    val x: String by lazyOf("x".also { println("lazy x") })
    val y: String by lazy { "y".also { println("lazy y") }}
    init { println("ctor") }
    fun print() { println("fun($x, $y)") }
}

fun main() { WithLazy().print() }
