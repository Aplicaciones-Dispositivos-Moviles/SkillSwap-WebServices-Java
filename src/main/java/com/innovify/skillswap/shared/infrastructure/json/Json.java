package com.innovify.skillswap.shared.infrastructure.json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A small JSON reader and writer for the few places that need one: the jsonb columns, the skill catalog and
 * the Gemini request and response. It keeps those places independent of the Jackson version in use.
 *
 * <p>Parsed values are {@link Map} (insertion order kept), {@link List}, {@link String}, {@link Long} (integers
 * that fit), {@link Double} (any other number), {@link Boolean} and {@code null}. The writer accepts the same
 * types plus any other {@link Number} and {@link CharSequence}.
 */
public final class Json {

    private static final int MAX_DEPTH = 100;

    private final String text;
    private int position;

    private Json(String text) {
        this.text = text;
    }

    /**
     * @throws JsonException when the text is not a single valid JSON value
     */
    public static Object parse(String text) {
        if (text == null) {
            throw new JsonException("The JSON text is null.");
        }
        Json reader = new Json(text);
        reader.skipWhitespace();
        Object value = reader.readValue(0);
        reader.skipWhitespace();
        if (reader.position != text.length()) {
            throw reader.error("Unexpected content after the JSON value");
        }
        return value;
    }

    /**
     * @throws JsonException when the value contains a type that has no JSON form
     */
    public static String write(Object value) {
        StringBuilder out = new StringBuilder();
        writeValue(out, value);
        return out.toString();
    }

    // ---------- Reading ----------

    private Object readValue(int depth) {
        if (depth > MAX_DEPTH) {
            throw error("The JSON is nested too deeply");
        }
        if (position >= text.length()) {
            throw error("Unexpected end of the JSON text");
        }
        char current = text.charAt(position);
        return switch (current) {
            case '{' -> readObject(depth);
            case '[' -> readArray(depth);
            case '"' -> readString();
            case 't' -> readLiteral("true", Boolean.TRUE);
            case 'f' -> readLiteral("false", Boolean.FALSE);
            case 'n' -> readLiteral("null", null);
            default -> {
                if (current == '-' || (current >= '0' && current <= '9')) {
                    yield readNumber();
                }
                throw error("Unexpected character '" + current + "'");
            }
        };
    }

    private Map<String, Object> readObject(int depth) {
        Map<String, Object> result = new LinkedHashMap<>();
        position++; // {
        skipWhitespace();
        if (peek() == '}') {
            position++;
            return result;
        }
        while (true) {
            skipWhitespace();
            if (peek() != '"') {
                throw error("Expected a property name");
            }
            String key = readString();
            skipWhitespace();
            expect(':');
            skipWhitespace();
            result.put(key, readValue(depth + 1));
            skipWhitespace();
            char next = next();
            if (next == '}') {
                return result;
            }
            if (next != ',') {
                position--;
                throw error("Expected ',' or '}'");
            }
        }
    }

    private List<Object> readArray(int depth) {
        List<Object> result = new ArrayList<>();
        position++; // [
        skipWhitespace();
        if (peek() == ']') {
            position++;
            return result;
        }
        while (true) {
            skipWhitespace();
            result.add(readValue(depth + 1));
            skipWhitespace();
            char next = next();
            if (next == ']') {
                return result;
            }
            if (next != ',') {
                position--;
                throw error("Expected ',' or ']'");
            }
        }
    }

    private String readString() {
        position++; // opening quote
        StringBuilder result = new StringBuilder();
        while (true) {
            char current = next();
            if (current == '"') {
                return result.toString();
            }
            if (current < 0x20) {
                position--;
                throw error("Unescaped control character in a string");
            }
            if (current != '\\') {
                result.append(current);
                continue;
            }
            char escaped = next();
            switch (escaped) {
                case '"', '\\', '/' -> result.append(escaped);
                case 'b' -> result.append('\b');
                case 'f' -> result.append('\f');
                case 'n' -> result.append('\n');
                case 'r' -> result.append('\r');
                case 't' -> result.append('\t');
                case 'u' -> result.append(readUnicodeEscape());
                default -> {
                    position--;
                    throw error("Invalid escape sequence");
                }
            }
        }
    }

    private char readUnicodeEscape() {
        if (position + 4 > text.length()) {
            throw error("Incomplete unicode escape");
        }
        int code = 0;
        for (int index = 0; index < 4; index++) {
            int digit = Character.digit(text.charAt(position + index), 16);
            if (digit < 0) {
                throw error("Invalid unicode escape");
            }
            code = code * 16 + digit;
        }
        position += 4;
        return (char) code;
    }

    private Object readNumber() {
        int start = position;
        if (peek() == '-') {
            position++;
        }
        int integerStart = position;
        skipDigits();
        if (position == integerStart) {
            throw error("Invalid number");
        }
        if (text.charAt(integerStart) == '0' && position - integerStart > 1) {
            throw error("Numbers cannot have leading zeros");
        }
        boolean integer = true;
        if (position < text.length() && text.charAt(position) == '.') {
            integer = false;
            position++;
            int fractionStart = position;
            skipDigits();
            if (position == fractionStart) {
                throw error("Invalid number");
            }
        }
        if (position < text.length() && (text.charAt(position) == 'e' || text.charAt(position) == 'E')) {
            integer = false;
            position++;
            if (position < text.length() && (text.charAt(position) == '+' || text.charAt(position) == '-')) {
                position++;
            }
            int exponentStart = position;
            skipDigits();
            if (position == exponentStart) {
                throw error("Invalid number");
            }
        }

        String literal = text.substring(start, position);
        if (integer) {
            try {
                return Long.parseLong(literal);
            } catch (NumberFormatException tooBig) {
                return Double.parseDouble(literal);
            }
        }
        return Double.parseDouble(literal);
    }

    private void skipDigits() {
        while (position < text.length() && text.charAt(position) >= '0' && text.charAt(position) <= '9') {
            position++;
        }
    }

    private Object readLiteral(String literal, Object value) {
        if (!text.startsWith(literal, position)) {
            throw error("Unexpected token");
        }
        position += literal.length();
        return value;
    }

    private void skipWhitespace() {
        while (position < text.length()) {
            char current = text.charAt(position);
            if (current != ' ' && current != '\t' && current != '\n' && current != '\r') {
                return;
            }
            position++;
        }
    }

    private char peek() {
        if (position >= text.length()) {
            throw error("Unexpected end of the JSON text");
        }
        return text.charAt(position);
    }

    private char next() {
        char current = peek();
        position++;
        return current;
    }

    private void expect(char expected) {
        if (peek() != expected) {
            throw error("Expected '" + expected + "'");
        }
        position++;
    }

    private JsonException error(String message) {
        return new JsonException("Invalid JSON: " + message + " at position " + position + ".");
    }

    // ---------- Writing ----------

    private static void writeValue(StringBuilder out, Object value) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof CharSequence sequence) {
            writeString(out, sequence);
        } else if (value instanceof Boolean flag) {
            out.append(flag.booleanValue());
        } else if (value instanceof Double || value instanceof Float) {
            double number = ((Number) value).doubleValue();
            if (Double.isNaN(number) || Double.isInfinite(number)) {
                throw new JsonException("NaN and infinity have no JSON form.");
            }
            out.append(number);
        } else if (value instanceof Number number) {
            out.append(number);
        } else if (value instanceof Map<?, ?> map) {
            out.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                writeString(out, String.valueOf(entry.getKey()));
                out.append(':');
                writeValue(out, entry.getValue());
            }
            out.append('}');
        } else if (value instanceof Iterable<?> items) {
            out.append('[');
            boolean first = true;
            for (Object item : items) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                writeValue(out, item);
            }
            out.append(']');
        } else {
            throw new JsonException("The type " + value.getClass().getName() + " has no JSON form.");
        }
    }

    private static void writeString(StringBuilder out, CharSequence text) {
        out.append('"');
        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            switch (current) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (current < 0x20) {
                        out.append(String.format("\\u%04x", (int) current));
                    } else {
                        out.append(current);
                    }
                }
            }
        }
        out.append('"');
    }
}
