package com.pax.configservice.xml;

import androidx.annotation.Nullable;
import com.pax.commonlib.utils.LogUtils;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

final class XmlDomUtils {

    private static final Pattern XML_DECLARATION = Pattern.compile("\\A\\s*<\\?xml[^>]*\\?>");

    private XmlDomUtils() {
    }

    static Document parseDocument(InputStream in)
            throws IOException, SAXException, ParserConfigurationException {
        String xml = XML_DECLARATION.matcher(decode(readAll(in))).replaceFirst("");
        return DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(new InputSource(new StringReader(xml)));
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private static String decode(byte[] bytes) {
        if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFE && (bytes[1] & 0xFF) == 0xFF) {
            return new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16BE);
        }
        if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xFE) {
            return new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16LE);
        }
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) {
            return new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

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

    static List<String> childNames(@Nullable Element parent) {
        List<String> names = new ArrayList<>();
        if (parent == null) {
            return names;
        }
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                names.add(node.getNodeName());
            }
        }
        return names;
    }

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
            notANumber(tag, value, defaultValue);
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
            notANumber(tag, value, defaultValue);
            return defaultValue;
        }
    }

    static void notANumber(String tag, String value, Object used) {
        LogUtils.w("XmlDomUtils", tag + " '" + value + "' is not a number — using " + used);
    }

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
