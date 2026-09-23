package dev.bluepitaya.phpmagik;

import dev.bluepitaya.phpmagik.lsp.Json;
import dev.bluepitaya.phpmagik.lsp.dto.Position;
import dev.bluepitaya.phpmagik.lsp.dto.ReferenceContext;
import dev.bluepitaya.phpmagik.lsp.dto.ReferenceParams;
import dev.bluepitaya.phpmagik.lsp.dto.TextDocumentIdentifier;
import dev.bluepitaya.phpmagik.lsp.handler.ReferencesHandler;
import dev.bluepitaya.phpmagik.testing.Fixture;
import dev.bluepitaya.phpmagik.ts.Point;
import dev.bluepitaya.phpmagik.ts.Range;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ArrayNode;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReferencesHandlerTest {

    @Test
    void listsTheAssignmentAndEveryUsageOfALocalVariable() throws IOException {
        try (Fixture fixture = Fixture.index("php_vars")) {
            var handler = new ReferencesHandler(fixture.workspace(), fixture.finder(),
                    fixture.log());
            String uri = fixture.file("Service1.php").uri();

            /* the "$e" of "$e = new Engine;" */
            ArrayNode locations = handler.handle(at(uri, 8, 8, true));

            ArrayNode expected = Json.array();
            expected.add(Json.location(uri, range(8, 8)));
            expected.add(Json.location(uri, range(9, 8)));
            expected.add(Json.location(uri, range(13, 25)));
            assertEquals(expected, locations);
        }
    }

    @Test
    void findsTheSameUsagesFromAUsage() throws IOException {
        try (Fixture fixture = Fixture.index("php_vars")) {
            var handler = new ReferencesHandler(fixture.workspace(), fixture.finder(),
                    fixture.log());
            String uri = fixture.file("Service1.php").uri();

            /* the "$e" of "return $c + $d + $e->lol;" */
            ArrayNode locations = handler.handle(at(uri, 13, 25, false));

            ArrayNode expected = Json.array();
            expected.add(Json.location(uri, range(9, 8)));
            expected.add(Json.location(uri, range(13, 25)));
            assertEquals(expected, locations);
        }
    }

    private static Range range(int line, int character) {
        return new Range(new Point(line, character), new Point(line, character + 2));
    }

    private static ReferenceParams at(String uri, int line, int character, boolean declaration) {
        return new ReferenceParams(
                new TextDocumentIdentifier(uri),
                new Position(line, character),
                new ReferenceContext(declaration)
        );
    }
}
