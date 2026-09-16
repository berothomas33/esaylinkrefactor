package com.pax.configservice.xml;

import androidx.annotation.Nullable;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.List;

/** Small DOM navigation helpers shared by {@link EmvXmlParamParser} and {@link ClssXmlParamParser}. */
final class XmlDomUtils {

    private XmlDomUtils() {
    }

    /** First direct child element named {@code tag}, or {@code null}. */
    @Nullable
    static Element firstChild(@Nullable Element parent, String tag) {
        if (parent == null) {
            return null;
        }
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && tag.equals(node.getNodeName())) {
                return (Element) node;
            }
        }
        return null;
    }

    /** All direct child elements named {@code tag}, in document order. */
    static List<Element> children(@Nullable Element parent, String tag) {
        List<Element> result = new ArrayList<>();
        if (parent == null) {
            return result;
        }
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && tag.equals(node.getNodeName())) {
                result.add((Element) node);
            }
        }
        return result;
    }

    /** Trimmed text content of the first direct child element named {@code tag}, or {@code null}. */
    @Nullable
    static String text(@Nullable Element parent, String tag) {
        Element child = firstChild(parent, tag);
        if (child == null) {
            return null;
        }
        String value = child.getTextContent();
        return value == null ? null : value.trim();
    }

    static String text(@Nullable Element parent, String tag, String defaultValue) {
        String value = text(parent, tag);
        return value == null || value.isEmpty() ? defaultValue : value;
    }

    static int intOf(@Nullable Element parent, String tag, int defaultValue) {
        String value = text(parent, tag);
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    static long longOf(@Nullable Element parent, String tag, long defaultValue) {
        String value = text(parent, tag);
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /** {@code "0"/"1"} (or any nonzero digit) style boolean flag tags. */
    static boolean boolOf(@Nullable Element parent, String tag, boolean defaultValue) {
        String value = text(parent, tag);
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        return !"0".equals(value);
    }

    static byte byteOf(@Nullable Element parent, String tag, int defaultValue) {
        return (byte) intOf(parent, tag, defaultValue);
    }
}
