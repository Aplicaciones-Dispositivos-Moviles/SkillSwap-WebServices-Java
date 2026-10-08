package com.innovify.skillswap.shared.infrastructure.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class JsonTest {

    @Test
    void parse_readsEveryJsonType() {
        Object value = Json.parse(" {\"a\": [1, 2.5, -3e2, \"x\\n\\u00e9\\\"\", true, null], \"b\": {}} ");

        Map<?, ?> map = (Map<?, ?>) value;
        assertThat(map.get("a")).isEqualTo(Arrays.asList(1L, 2.5, -300.0, "x\n\u00e9\"", true, null));
        assertThat(map.get("b")).isEqualTo(Map.of());
    }

    @Test
    void parse_keepsThePropertyOrder() {
        Map<?, ?> map = (Map<?, ?>) Json.parse("{\"z\": 1, \"a\": 2, \"m\": 3}");

        assertThat(new ArrayList<Object>(map.keySet())).containsExactly("z", "a", "m");
    }

    @Test
    void parse_readsIntegersAsLongAndTooBigOnesAsDouble() {
        assertThat(Json.parse("42")).isEqualTo(42L);
        assertThat(Json.parse("99999999999999999999")).isEqualTo(1e20);
    }

    @Test
    void parse_readsSurrogatePairsAndEscapes() {
        assertThat(Json.parse("\"\\ud83d\\ude00 \\/ \\t\"")).isEqualTo("\uD83D\uDE00 / \t");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "{", "[1,]", "01", "{\"a\"}", "\"x", "nul", "[1] x", "{\"a\":1,}", "'x'",
            "1.", "-", "\"\\q\"", "\"\\u12\"", "[\"a\nb\"]"})
    void parse_rejectsInvalidJson(String text) {
        assertThatThrownBy(() -> Json.parse(text)).isInstanceOf(JsonException.class);
    }

    @Test
    void parse_rejectsNull() {
        assertThatThrownBy(() -> Json.parse(null)).isInstanceOf(JsonException.class);
    }

    @Test
    void parse_rejectsDocumentsNestedTooDeeply() {
        String text = "[".repeat(500) + "]".repeat(500);

        assertThatThrownBy(() -> Json.parse(text)).isInstanceOf(JsonException.class);
    }

    @Test
    void write_producesCompactJsonInInsertionOrder() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("rawText", "hello");
        map.put("tags", List.of("a", "b"));
        map.put("count", 3);
        map.put("ratio", 0.5);
        map.put("done", false);
        map.put("none", null);

        assertThat(Json.write(map))
                .isEqualTo("{\"rawText\":\"hello\",\"tags\":[\"a\",\"b\"],\"count\":3,\"ratio\":0.5,\"done\":false,\"none\":null}");
    }

    @Test
    void write_escapesQuotesBackslashesAndControlCharacters() {
        assertThat(Json.write("a\"b\\c\n\t\u0001")).isEqualTo("\"a\\\"b\\\\c\\n\\t\\u0001\"");
    }

    @Test
    void write_keepsNonAsciiTextAsIs() {
        assertThat(Json.write("autenticaci\u00f3n")).isEqualTo("\"autenticaci\u00f3n\"");
    }

    @Test
    void write_thenParse_returnsTheSameValue() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("text", "line 1\nline 2 \"quoted\" \\ \u00e9");
        map.put("list", List.of(1L, 2L));

        assertThat(Json.parse(Json.write(map))).isEqualTo(map);
    }

    @Test
    void write_rejectsTypesWithoutJsonForm() {
        assertThatThrownBy(() -> Json.write(new Object())).isInstanceOf(JsonException.class);
        assertThatThrownBy(() -> Json.write(Double.NaN)).isInstanceOf(JsonException.class);
    }
}
