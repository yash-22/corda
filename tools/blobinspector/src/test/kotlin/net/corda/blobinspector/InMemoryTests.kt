package net.corda.blobinspector

import net.corda.nodeapi.internal.serialization.AllWhitelist
import net.corda.nodeapi.internal.serialization.amqp.SerializationOutput
import net.corda.nodeapi.internal.serialization.amqp.SerializerFactory
import org.junit.Test


class InMemoryTests {
    private val factory = SerializerFactory(AllWhitelist, ClassLoader.getSystemClassLoader())

    @Test
    fun test1() {
        data class C (val a: Int, val b: Long, val c: String)
        val ser = SerializationOutput(factory).serialize(C(100, 567L, "this is a test"))

        BlobHandler.make(InMemoryConfig(Mode.inMem).apply { blob = ser; data = true}).apply {
            inspectBlob(config, getBytes())
        }
    }

    @Test
    fun test2() {
        data class C (val i: Int, val c: C?)
        val ser = SerializationOutput(factory).serialize(C(1, C(2, C(3, C(4, null)))))

        BlobHandler.make(InMemoryConfig(Mode.inMem).apply { blob = ser; data = true}).apply {
            inspectBlob(config, getBytes())
        }
    }

    @Test
    fun test3() {
        data class C (val a: IntArray, val b: Array<String>)

        val a = IntArray(10) { i -> i }
        val c = C(a, arrayOf("aaa", "bbb", "ccc"))

        val ser = SerializationOutput(factory).serialize(c)

        BlobHandler.make(InMemoryConfig(Mode.inMem).apply { blob = ser; data = true}).apply {
            inspectBlob(config, getBytes())
        }
    }

}