package dev.bluepitaya.phpmagik.json;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Minimal JSON tree, standing in for the yyjson the C version vendored.
 *
 * <p>Integral and floating-point numbers are kept as separate kinds so that a
 * number that arrived without a fraction is written back without one: LSP peers
 * are picky about {@code 5} versus {@code 5.0} for ids and offsets.
 *
 * <p>Readers are deliberately forgiving - {@link #get} and {@link #at} answer
 * {@code null} instead of throwing, and the typed accessors fall back to a zero
 * value - because the protocol layer null-checks rather than catching.
 */
public final class Json {

    private enum Kind { NULL, BOOLEAN, STRING, INTEGRAL, FRACTIONAL, OBJECT, ARRAY }

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private static final Json NULL = new Json(Kind.NULL);
    private static final Json TRUE = booleanValue(true);
    private static final Json FALSE = booleanValue(false);

    private final Kind kind;

    private String text;
    private long integral;
    private double fractional;
    private boolean flag;
    private Map<String, Json> members;
    private List<Json> elements;

    private Json(Kind kind) {
        this.kind = kind;
    }

    public static Json parse(String text) {
        if (text == null) throw new IllegalArgumentException("JSON input must not be null!");
        Parser parser = new Parser(text);
        parser.skipWhitespace();
        Json value = parser.readValue();
        parser.skipWhitespace();
        if (!parser.atEnd()) throw parser.fail("Trailing content after the top level value");
        return value;
    }

    public static Json object() {
        Json json = new Json(Kind.OBJECT);
        json.members = new LinkedHashMap<>();
        return json;
    }

    public static Json array() {
        Json json = new Json(Kind.ARRAY);
        json.elements = new ArrayList<>();
        return json;
    }

    public static Json of(String value) {
        if (value == null) return NULL;
        Json json = new Json(Kind.STRING);
        json.text = value;
        return json;
    }

    public static Json of(long value) {
        Json json = new Json(Kind.INTEGRAL);
        json.integral = value;
        return json;
    }

    public static Json of(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException("JSON cannot represent " + value);
        }
        Json json = new Json(Kind.FRACTIONAL);
        json.fractional = value;
        return json;
    }

    public static Json of(boolean value) {
        return value ? TRUE : FALSE;
    }

    public static Json nullValue() {
        return NULL;
    }

    private static Json booleanValue(boolean value) {
        Json json = new Json(Kind.BOOLEAN);
        json.flag = value;
        return json;
    }

    public boolean isNull() {
        return kind == Kind.NULL;
    }

    public boolean isObject() {
        return kind == Kind.OBJECT;
    }

    public boolean isArray() {
        return kind == Kind.ARRAY;
    }

    public int size() {
        if (kind == Kind.OBJECT) return members.size();
        if (kind == Kind.ARRAY) return elements.size();
        return 0;
    }

    public Json get(String key) {
        return kind == Kind.OBJECT ? members.get(key) : null;
    }

    public Json at(int index) {
        if (kind != Kind.ARRAY || index < 0 || index >= elements.size()) return null;
        return elements.get(index);
    }

    public List<Json> items() {
        if (kind != Kind.ARRAY) return Collections.emptyList();
        return Collections.unmodifiableList(elements);
    }

    public String asString() {
        return kind == Kind.STRING ? text : null;
    }

    public long asLong() {
        if (kind == Kind.INTEGRAL) return integral;
        if (kind == Kind.FRACTIONAL) return (long) fractional;
        return 0;
    }

    public double asDouble() {
        if (kind == Kind.INTEGRAL) return (double) integral;
        if (kind == Kind.FRACTIONAL) return fractional;
        return 0;
    }

    public boolean asBoolean() {
        return kind == Kind.BOOLEAN && flag;
    }

    public String getString(String key) {
        Json value = get(key);
        return value == null ? null : value.asString();
    }

    public int getInt(String key) {
        Json value = get(key);
        return value == null ? 0 : (int) value.asLong();
    }

    public boolean getBool(String key) {
        Json value = get(key);
        return value != null && value.asBoolean();
    }

    public Json put(String key, Json value) {
        if (kind != Kind.OBJECT) throw new IllegalStateException("Not a JSON object: " + kind);
        Objects.requireNonNull(key, "Key must not be null!");
        members.put(key, value == null ? NULL : value);
        return this;
    }

    public Json put(String key, String value) {
        return put(key, of(value));
    }

    public Json put(String key, long value) {
        return put(key, of(value));
    }

    public Json put(String key, boolean value) {
        return put(key, of(value));
    }

    public Json add(Json value) {
        if (kind != Kind.ARRAY) throw new IllegalStateException("Not a JSON array: " + kind);
        elements.add(value == null ? NULL : value);
        return this;
    }

    public Json add(String value) {
        return add(of(value));
    }

    public String write() {
        StringBuilder out = new StringBuilder();
        write(out);
        return out.toString();
    }

    private void write(StringBuilder out) {
        switch (kind) {
            case NULL -> out.append("null");
            case BOOLEAN -> out.append(flag ? "true" : "false");
            case STRING -> escape(text, out);
            case INTEGRAL -> out.append(integral);
            case FRACTIONAL -> out.append(fractional);
            case OBJECT -> {
                out.append('{');
                boolean first = true;
                for (Map.Entry<String, Json> entry : members.entrySet()) {
                    if (!first) out.append(',');
                    first = false;
                    escape(entry.getKey(), out);
                    out.append(':');
                    entry.getValue().write(out);
                }
                out.append('}');
            }
            case ARRAY -> {
                out.append('[');
                for (int i = 0; i < elements.size(); i++) {
                    if (i > 0) out.append(',');
                    elements.get(i).write(out);
                }
                out.append(']');
            }
        }
    }

    /** Non-ASCII is emitted literally - the transport counts UTF-8 bytes, so escaping it only inflates them. */
    private static void escape(String value, StringBuilder out) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append("\\u00").append(HEX[(c >> 4) & 0xF]).append(HEX[c & 0xF]);
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }

    @Override
    public String toString() {
        return write();
    }

    private static final class Parser {

        private final String text;
        private int pos;

        private Parser(String text) {
            this.text = text;
        }

        private boolean atEnd() {
            return pos >= text.length();
        }

        private void skipWhitespace() {
            while (pos < text.length()) {
                char c = text.charAt(pos);
                if (c != ' ' && c != '\t' && c != '\n' && c != '\r') return;
                pos++;
            }
        }

        private char peek() {
            if (atEnd()) throw fail("Unexpected end of input");
            return text.charAt(pos);
        }

        private char next() {
            if (atEnd()) throw fail("Unexpected end of input");
            return text.charAt(pos++);
        }

        private IllegalArgumentException fail(String message) {
            return new IllegalArgumentException(message + " at offset " + pos);
        }

        private Json readValue() {
            char c = peek();
            switch (c) {
                case '{': return readObject();
                case '[': return readArray();
                case '"': return Json.of(readString());
                case 't': readLiteral("true"); return Json.of(true);
                case 'f': readLiteral("false"); return Json.of(false);
                case 'n': readLiteral("null"); return Json.nullValue();
                default: return readNumber();
            }
        }

        private void readLiteral(String literal) {
            if (!text.startsWith(literal, pos)) throw fail("Expected '" + literal + "'");
            pos += literal.length();
        }

        private Json readObject() {
            Json result = Json.object();
            pos++;
            skipWhitespace();
            if (peek() == '}') {
                pos++;
                return result;
            }
            while (true) {
                skipWhitespace();
                if (peek() != '"') throw fail("Expected a quoted member name");
                String key = readString();
                skipWhitespace();
                if (next() != ':') throw fail("Expected ':' after a member name");
                skipWhitespace();
                result.members.put(key, readValue());
                skipWhitespace();
                char c = next();
                if (c == '}') return result;
                if (c != ',') throw fail("Expected ',' or '}' in an object");
            }
        }

        private Json readArray() {
            Json result = Json.array();
            pos++;
            skipWhitespace();
            if (peek() == ']') {
                pos++;
                return result;
            }
            while (true) {
                skipWhitespace();
                result.elements.add(readValue());
                skipWhitespace();
                char c = next();
                if (c == ']') return result;
                if (c != ',') throw fail("Expected ',' or ']' in an array");
            }
        }

        /**
         * Surrogate pairs need no special case: each unicode escape contributes one
         * UTF-16 unit, so a pair reassembles itself in the builder.
         */
        private String readString() {
            pos++;
            StringBuilder out = new StringBuilder();
            while (true) {
                char c = next();
                if (c == '"') return out.toString();
                if (c < 0x20) throw fail("Unescaped control character in a string");
                if (c != '\\') {
                    out.append(c);
                    continue;
                }
                char escaped = next();
                switch (escaped) {
                    case '"' -> out.append('"');
                    case '\\' -> out.append('\\');
                    case '/' -> out.append('/');
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'u' -> out.append(readHex4());
                    default -> throw fail("Unknown escape '\\" + escaped + "'");
                }
            }
        }

        private char readHex4() {
            if (pos + 4 > text.length()) throw fail("Truncated unicode escape");
            int value = 0;
            for (int i = 0; i < 4; i++) {
                int digit = Character.digit(text.charAt(pos++), 16);
                if (digit < 0) throw fail("Invalid hex digit in a unicode escape");
                value = value * 16 + digit;
            }
            return (char) value;
        }

        private Json readNumber() {
            int start = pos;
            if (peek() == '-') pos++;
            readDigits();
            boolean real = false;
            if (!atEnd() && text.charAt(pos) == '.') {
                real = true;
                pos++;
                readDigits();
            }
            if (!atEnd() && (text.charAt(pos) == 'e' || text.charAt(pos) == 'E')) {
                real = true;
                pos++;
                if (!atEnd() && (text.charAt(pos) == '+' || text.charAt(pos) == '-')) pos++;
                readDigits();
            }
            String literal = text.substring(start, pos);
            if (real) return Json.of(Double.parseDouble(literal));
            try {
                return Json.of(Long.parseLong(literal));
            } catch (NumberFormatException e) {
                return Json.of(Double.parseDouble(literal));
            }
        }

        private void readDigits() {
            int start = pos;
            while (pos < text.length()) {
                char c = text.charAt(pos);
                if (c < '0' || c > '9') break;
                pos++;
            }
            if (pos == start) throw fail("Expected a digit");
        }
    }
}
