package com.emvenhance.ui

import com.pax.commonlib.utils.ConvertUtils
import java.lang.reflect.Modifier

/**
 * Every field of one parameter object — a cached kernel AID, CAPK or the like — as text, the way
 * the kernel gets it: byte arrays in hex, nested objects and lists indented. For the EMV params
 * screen, to see exactly what is loaded.
 */
internal object ParamDump {

    fun describe(value: Any): String {
        val text = StringBuilder()
        append(text, value, "")
        return text.toString()
    }

    private fun append(text: StringBuilder, value: Any, indent: String) {
        var type: Class<*>? = value.javaClass
        while (type != null && type != Any::class.java) {
            for (field in type.declaredFields) {
                if (Modifier.isStatic(field.modifiers) || field.isSynthetic) {
                    continue
                }
                field.isAccessible = true
                val fieldValue = try {
                    field.get(value)
                } catch (e: IllegalAccessException) {
                    continue
                }
                text.append(indent).append(field.name).append(" = ")
                appendValue(text, fieldValue, indent)
            }
            type = type.superclass
        }
    }

    private fun appendValue(text: StringBuilder, value: Any?, indent: String) {
        when {
            value == null -> text.append("null\n")
            value is ByteArray ->
                text.append(if (value.isEmpty()) "(empty)" else ConvertUtils.bcd2Str(value)).append('\n')
            value is Byte -> text.append(String.format("%02X", value.toInt() and 0xFF)).append('\n')
            value.javaClass.isArray -> {
                val items = StringBuilder("[")
                for (i in 0 until java.lang.reflect.Array.getLength(value)) {
                    items.append(if (i == 0) "" else ", ").append(java.lang.reflect.Array.get(value, i))
                }
                text.append(items).append("]\n")
            }
            value is Collection<*> -> {
                text.append(value.size).append(" item(s)\n")
                for ((i, item) in value.withIndex()) {
                    text.append(indent).append("  [").append(i).append("]\n")
                    if (isPlain(item)) {
                        text.append(indent).append("    ")
                        appendValue(text, item, "$indent    ")
                    } else {
                        append(text, item!!, "$indent    ")
                    }
                }
            }
            isPlain(value) -> text.append(value).append('\n')
            else -> {
                text.append('\n')
                append(text, value, "$indent  ")
            }
        }
    }

    private fun isPlain(value: Any?): Boolean =
        value == null || value is CharSequence || value is Number || value is Boolean ||
            value is Char || value is Enum<*> || value is ByteArray || value.javaClass.isArray
}
