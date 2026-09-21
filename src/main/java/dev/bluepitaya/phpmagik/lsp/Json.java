package dev.bluepitaya.phpmagik.lsp;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.bluepitaya.phpmagik.ts.Point;
import dev.bluepitaya.phpmagik.ts.Range;
import org.jspecify.annotations.NullMarked;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

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

    public static ObjectNode location(String uri, Range range) {
        ObjectNode loc = object();
        loc.put("uri", uri);
        loc.set("range", range(range));
        return loc;
    }

    public static ObjectNode range(Range range) {
        Point start = range.start();
        Point end = range.end();
        ObjectNode out = object();
        out.set("start", object().put("line", start.getRow()).put("character", start.getColumn()));
        out.set("end", object().put("line", end.getRow()).put("character", end.getColumn()));
        return out;
    }
}
