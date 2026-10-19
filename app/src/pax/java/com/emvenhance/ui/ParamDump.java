package com.emvenhance.ui;

import com.pax.commonlib.utils.ConvertUtils;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collection;

/**
 * Every field of one parameter object — a cached kernel AID, CAPK or the like — as text, the way
 * the kernel gets it: byte arrays in hex, nested objects and lists indented. For the EMV params
 * screen, to see exactly what is loaded.
 */
final class ParamDump {

    private ParamDump() {
    }

    static String describe(Object value) {
        StringBuilder text = new StringBuilder();
        append(text, value, "");
        return text.toString();
    }

    private static void append(StringBuilder text, Object value, String indent) {
        for (Class<?> type = value.getClass(); type != null && type != Object.class;
                type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                    continue;
                }
                field.setAccessible(true);
                Object fieldValue;
                try {
                    fieldValue = field.get(value);
                } catch (IllegalAccessException e) {
                    continue;
                }
                text.append(indent).append(field.getName()).append(" = ");
                appendValue(text, fieldValue, indent);
            }
        }
    }

    private static void appendValue(StringBuilder text, Object value, String indent) {
        if (value == null) {
            text.append("null\n");
        } else if (value instanceof byte[]) {
            byte[] bytes = (byte[]) value;
            text.append(bytes.length == 0 ? "(empty)" : ConvertUtils.bcd2Str(bytes)).append('\n');
        } else if (value instanceof Byte) {
            text.append(String.format("%02X", (Byte) value & 0xFF)).append('\n');
        } else if (value.getClass().isArray()) {
            StringBuilder items = new StringBuilder("[");
            for (int i = 0; i < Array.getLength(value); i++) {
                items.append(i == 0 ? "" : ", ").append(Array.get(value, i));
            }
            text.append(items).append("]\n");
        } else if (value instanceof Collection) {
            Collection<?> items = (Collection<?>) value;
            text.append(items.size()).append(" item(s)\n");
            int i = 0;
            for (Object item : items) {
                text.append(indent).append("  [").append(i++).append("]\n");
                if (isPlain(item)) {
                    text.append(indent).append("    ");
                    appendValue(text, item, indent + "    ");
                } else {
                    append(text, item, indent + "    ");
                }
            }
        } else if (isPlain(value)) {
            text.append(value).append('\n');
        } else {
            text.append('\n');
            append(text, value, indent + "  ");
        }
    }

    private static boolean isPlain(Object value) {
        return value == null || value instanceof CharSequence || value instanceof Number
                || value instanceof Boolean || value instanceof Character || value instanceof Enum
                || value instanceof byte[] || value.getClass().isArray();
    }
}
