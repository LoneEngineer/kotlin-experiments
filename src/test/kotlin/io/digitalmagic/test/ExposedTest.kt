package io.digitalmagic.test

import org.jetbrains.exposed.dao.*
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.util.*
import kotlin.test.*

object TestTable: IntIdTable() {
    val a = varchar("a", 8)
    val b = integer("b").nullable()
    val c = varchar("c", 8).nullable()
}

class FullProjection(id: EntityID<Int>): IntEntity(id) {
    var a by TestTable.a
    var b by TestTable.b
    var c by TestTable.c

    companion object : IntEntityClass<FullProjection>(TestTable) {
        init {
            EntityHook.subscribe { action ->
                if (action.changeType == EntityChangeType.Updated) {
                    action.toEntity(this)?.let { entity ->
                        println("${entity.id}@${entity::class.simpleName} has been changed via full projection")
                    }
                }
            }
        }
    }
}

class PartialProjection(id: EntityID<Int>): IntEntity(id) {
    var a by TestTable.a

    companion object : IntEntityClass<PartialProjection>(TestTable) {
        init {
            EntityHook.subscribe { action ->
                if (action.changeType == EntityChangeType.Updated) {
                    action.toEntity(this)?.let { entity ->
                        println("${entity.id}@${entity::class.simpleName} has been changed via partial projection")
                    }
                }
            }
        }
    }
}

class ExposedTest {

    @Test
    fun legnghtOfFieldConstraint() {
        Database.connect(
            "jdbc:h2:mem:hook-${UUID.randomUUID().toString()};DB_CLOSE_DELAY=-1",
            "org.h2.Driver",
            "test",
            "test"
        )
        transaction {
            SchemaUtils.create(TestTable)
        }

        assertFailsWith(IllegalArgumentException::class) {
            transaction {
                FullProjection.new(1) {
                    a = "a12345678"
                    b = 1
                    c = "c1"
                }
            }
        }
    }

    @Test
    fun usingEntityHooks() {
        Database.connect(
            "jdbc:h2:mem:hook-${UUID.randomUUID().toString()};DB_CLOSE_DELAY=-1",
            "org.h2.Driver",
            "test",
            "test"
        )
        transaction {
            SchemaUtils.create(TestTable)
        }

        transaction {
            FullProjection.new(1) {
                a = "a1"
                b = 1
                c = "c1"
            }
            FullProjection.new(2) {
                a = "a2"
                b = 2
                c = "c2"
            }
            PartialProjection.new(3) {
                a = "a3"
            }
            PartialProjection.new(4) {
                a = "a4"
            }
        }

        transaction {
            PartialProjection.find {
                TestTable.a eq "a1"
            }.forUpdate().map {
                it.a = "a10"
            }

            FullProjection.find {
                TestTable.a eq "a3"
            }.forUpdate().map {
                it.b = 30
                it.c = "c30"
            }

            TestTable.update(where = {
                TestTable.a eq "a2"
            }) {
                it[TestTable.b] = 20
                it[TestTable.c] = "c20"
            }

            TestTable.select {
                TestTable.a eq "a4"
            }.toList().map {
                PartialProjection.wrapRow(it)
            }.map {
                it.a = "a40"
            }
        }
    }

}