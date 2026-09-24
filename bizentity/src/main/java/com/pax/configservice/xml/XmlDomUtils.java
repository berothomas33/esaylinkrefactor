package com.pax.configservice.xml;

import androidx.annotation.Nullable;

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

/** Small DOM navigation helpers shared by {@link EmvXmlParamParser} and {@link ClssXmlParamParser}. */
final class XmlDomUtils {

    /** Matches a leading {@code <?xml ...?>} prolog — see {@link #parseDocument}. */
    private static final Pattern XML_DECLARATION = Pattern.compile("\\A\\s*<\\?xml[^>]*\\?>");

    private XmlDomUtils() {
    }

    /**
     * Parses a downloaded {@code emv_param.emv}/{@code clss_param.clss} into a DOM, working around
     * a real device failure ({@code org.xml.sax.SAXParseException: unexpected attributes in XML
     * declaration}, thrown from {@code org.apache.harmony.xml.parsers.DocumentBuilderImpl}) that
     * plain {@code DocumentBuilder#parse(InputStream)} hit on both files. That parser is Android's
     * own bundled expat binding, not a JDK one — it insists on a stricter/narrower {@code <?xml
     * ...?>} prolog than a desktop JVM's parser accepts (the same file the old app's own
     * {@code EMVParamsEngine} likely read without incident, since it isn't going through this same
     * strict Android XML stack). Rather than reverse-engineer exactly which token in the prolog
     * this build of expat objects to, decode the bytes ourselves (honoring any BOM; defaulting to
     * UTF-8 otherwise — this file's content is ASCII-safe hex/tag data either way) and strip the
     * prolog entirely before parsing the resulting {@link String} — a DOM document doesn't need
     * one, and once expat is looking at already-decoded characters there's no declaration left for
     * it to reject.
     */
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

    /** Honors a UTF-16 BOM if present; otherwise assumes UTF-8 (see {@link #parseDocument}). */
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
