package net.corda.blobinspector

import net.corda.core.utilities.ByteSequence
import net.corda.nodeapi.internal.serialization.amqp.*
import org.apache.qpid.proton.amqp.Binary
import org.apache.qpid.proton.amqp.DescribedType
import org.apache.qpid.proton.amqp.Symbol
import org.apache.qpid.proton.codec.Data
import java.nio.ByteBuffer

/**
 * Print a string to the console only if the verbose config option is set.
 */
fun String.debug(config: Config) {
    if (config.verbose) {
        println(this)
    }
}

/**
 *
 */
interface Stringify {
    fun stringify(sb: StringBuilder)
}

/**
 * Used by the [StringBuilder] extension method to track the current indentation
 * level for pretty printing
 */
var g_indent = 0

/**
 *
 */
var g_indenting = true

/**
 * Adds a line and adjust the indentation depending upon whether the
 */
fun StringBuilder.appendlnIndent(ln: String) {
    if (ln.endsWith("}") || ln.endsWith("]")) {
        g_indent -= 4
    }
    appendln("${"".padStart(if (g_indenting) g_indent else 0, ' ')}$ln")
    if (ln.endsWith("{") || ln.endsWith("[")) {
        g_indent += 4
    }

    g_indenting = true
}

/**
 * Adds a line and adjust the indentation depending upon whether the
 */
fun StringBuilder.appendIndent(ln: String) {
    g_indenting = false

    if (ln.endsWith("}") || ln.endsWith("]")) {
        g_indent -= 4
    }
    append("${"".padStart(g_indent, ' ')}$ln")
    if (ln.endsWith("{") || ln.endsWith("[")) {
        g_indent += 4
    }
}

fun String.simplifyClass(): String {
    return this.substring(this.lastIndexOf('.') + 1)
}

/**
 * Represents the deserialized form of the property of an Object
 *
 * @param name
 * @param type
 */
abstract class Property(
        val name: String,
        val type: String) : Stringify

/**
 * Derived class of [Property], represents properties of an object that are non compelex, such
 * as any POD type or String
 */
class PrimProperty(
        name: String,
        type: String,
        private val value: String) : Property(name, type) {
    override fun toString(): String = "$name : $type : $value"

    override fun stringify(sb: StringBuilder) {
        sb.appendlnIndent("$name : $type : $value")
    }
}

/**
 * Derived class of [Property] that represents a binary blob. Specifically useful because printing
 * a stream of bytes onto the screen isn't very use friendly
 */
class BinaryProperty(
        name: String,
        type: String,
        val value: ByteArray) : Property(name, type) {
    override fun toString(): String = "$name : $type : <<<BINARY BLOB>>>"

    override fun stringify(sb: StringBuilder) {
        sb.appendlnIndent("$name : $type : <<<BINARY BLOB>>>")
    }
}

/**
 * Derived class of [Property] that represent a list property. List could be either PoD types or
 * composite types.
 */
class ListProperty(
        name: String,
        type: String,
        private val values: MutableList<Any> = mutableListOf()) : Property(name, type) {
    override fun stringify(sb: StringBuilder) {
        sb.apply {
            if (values.isEmpty()) {
                appendlnIndent("$name : $type : [ << EMPTY LIST >> ]")
            } else if (values.first() is Stringify) {
                appendlnIndent("$name : $type : [")
                values.forEach {
                    (it as Stringify).stringify(this)
                }
                appendlnIndent("]")
            } else {
                appendlnIndent("$name : $type : [")
                values.forEach {
                    appendlnIndent(it.toString())
                }
                appendlnIndent("]")
            }
        }
    }
}

/**
 * Derived class of [Property] that represents class properties that are themselves instances of
 * some complex type.
 */
class InstanceProperty(
        name: String,
        type: String,
        val value: Instance) : Property(name, type) {
    override fun stringify(sb: StringBuilder) {
        sb.appendIndent("$name : ")
        value.stringify(sb)
    }
}

/**
 * Represents an instance of a composite type.
 */
class Instance(
        val name: String,
        val type: String,
        val fields: MutableList<Property> = mutableListOf()) : Stringify {
    override fun stringify(sb: StringBuilder) {
        sb.apply {
            appendlnIndent("${name.simplifyClass()} : {")
            fields.forEach {
                it.stringify(this)
            }
            appendlnIndent("}")
        }
    }
}

/**
 *
 */
fun inspectComposite(
        config: Config,
        typeMap: Map<Symbol?, TypeNotation>,
        obj: DescribedType): Instance {
    if (obj.described !is List<*>) throw MalformedBlob("")

    "composite: ${(typeMap[obj.descriptor] as CompositeType).name}".debug(config)

    val inst = Instance(
            typeMap[obj.descriptor]?.name ?: "",
            typeMap[obj.descriptor]?.label ?: "")

    (typeMap[obj.descriptor] as CompositeType).fields.zip(obj.described as List<*>).forEach {
        "  field: ${it.first.name}".debug(config)
        inst.fields.add(
                if (it.second is DescribedType) {
                    "    - is described".debug(config)
                    val d = inspectDescribed(config, typeMap, it.second as DescribedType)

                    when (d) {
                        is Instance ->
                            InstanceProperty(
                                    it.first.name,
                                    it.first.type,
                                    d)
                        is List<*> -> {
                            "      - List".debug(config)
                            ListProperty(
                                    it.first.name,
                                    it.first.type,
                                    d as MutableList<Any>)
                        }
                        else -> {
                            "    skip it".debug(config)
                            return@forEach
                        }
                    }

                } else {
                    "    - is prim".debug(config)
                    when (it.first.type) {
                        "binary" -> BinaryProperty(it.first.name, it.first.type, (it.second as Binary).array)
                        else -> PrimProperty(it.first.name, it.first.type, it.second.toString())
                    }
                })
    }

    return inst
}

fun inspectRestricted(
        config: Config,
        typeMap: Map<Symbol?, TypeNotation>,
        obj: DescribedType): Any {
    return when ((typeMap[obj.descriptor] as RestrictedType).source) {
        "list" -> inspectRestrictedList(config, typeMap, obj)
        "map" -> inspectRestrictedMap(config, typeMap, obj)
        else -> throw NotImplementedError()
    }
}

fun inspectRestrictedList(
        config: Config,
        typeMap: Map<Symbol?, TypeNotation>,
        obj: DescribedType): List<Any> {
    if (obj.described !is List<*>) throw MalformedBlob("")

    return mutableListOf<Any>().apply {
        (obj.described as List<*>).forEach {
            try {
                add(inspectDescribed(config, typeMap, it as DescribedType))
            } catch (e: ClassCastException) {
                add(it.toString())
            }
        }
    }
}

fun inspectRestrictedMap(
        config: Config,
        typeMap: Map<Symbol?, TypeNotation>,
        obj: DescribedType): Instance {
    throw NotImplementedError()
}


/**
 * Every element of the blob stream will be a ProtonJ [DescribedType]. When inspecting the blob stream
 * the two custom Corda types we're interested in are [CompositeType]'s, representing the instance of
 * some object (class), and [RestrictedType]'s, representing containers and enumerations.
 *
 * @param config The configuration object that controls the behaviour of the BlobInspector
 * @param typeMap
 * @param obj
 *
 */
fun inspectDescribed(
        config: Config,
        typeMap: Map<Symbol?, TypeNotation>,
        obj: DescribedType): Any {
    "${obj.descriptor} in typeMap? = ${obj.descriptor in typeMap}".debug(config)

    return when (typeMap[obj.descriptor]) {
        is CompositeType -> {
            "* It's composite".debug(config)
            inspectComposite(config, typeMap, obj)
        }
        is RestrictedType -> {
            "* It's restricted".debug(config)
            inspectRestricted(config, typeMap, obj)
        }
        else -> {
            "${typeMap[obj.descriptor]?.name} is neither Composite or Restricted".debug(config)
        }
    }

}

fun inspectBlob(config: Config, blob: ByteArray) {
    // This sucks, but that's because the main code doesn't deal well with the multiple versions so for now
    // we're going to just bodge around that

    val bytes = ByteSequence.of(blob)

    val headerSize = AmqpHeaderV1_0.size
    val headers = listOf(ByteSequence.of(AmqpHeaderV1_0.bytes))

    val blobHeader = bytes.take(headerSize)

    if (blobHeader !in headers) {
        throw MalformedBlob("Blob is not a Corda AMQP serialised object graph")
    }


    val data = Data.Factory.create()
    val size = data.decode(ByteBuffer.wrap(bytes.bytes, bytes.offset + headerSize, bytes.size - headerSize))

    if (size.toInt() != blob.size - headerSize) {
        throw MalformedBlob("Blob size does not match internal size")
    }

    val e = Envelope.get(data)

    if (config.schema) {
        println(e.schema)
    }

    if (config.transforms) {
        println(e.transformsSchema)
    }

    val typeMap = e.schema.types.associateBy({ it.descriptor.name }, { it })

    if (config.data) {
        val inspected = inspectDescribed(config, typeMap, e.obj as DescribedType)


        println("\n${StringBuilder().apply { (inspected as Instance).stringify(this) }}")

        (inspected as Instance).fields.find { it.type == "net.corda.core.serialization.SerializedBytes<?>" }?.let {
            "Found field of SerializedBytes".debug(config)
            (it as InstanceProperty).value.fields.find { it.name == "bytes" }?.let { raw ->
                inspectBlob(config, (raw as BinaryProperty).value)
            }
        }
    }
}

