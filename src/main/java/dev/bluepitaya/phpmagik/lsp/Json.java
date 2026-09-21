package dev.bluepitaya.phpmagik.lsp;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.jspecify.annotations.NullMarked;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;

@NullMarked
public final class Json {

    public static final ObjectMapper RejectsNullFieldsMapper = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES)
            .changeDefaultPropertyInclusion(incl -> incl.withValueInclusion(JsonInclude.Include.NON_NULL))
            .build();

    public static ObjectNode object() {
        return RejectsNullFieldsMapper.createObjectNode();
    }

    public static ArrayNode array() {
        return RejectsNullFieldsMapper.createArrayNode();
    }

    public static ObjectNode location(String uri, int line, int col, int len) {
        ObjectNode loc = object();
        loc.put("uri", uri);

        ObjectNode range = object();
        range.set("start", object().put("line", line).put("character", col));
        range.set("end", object().put("line", line).put("character", col + len));
        loc.set("range", range);

        return loc;
    }

    /** Ranges are byte offsets into the UTF-8 source, so a name's length is its encoded length. */
    public static int byteLength(String s) {
        return s.getBytes(StandardCharsets.UTF_8).length;
    }
}
