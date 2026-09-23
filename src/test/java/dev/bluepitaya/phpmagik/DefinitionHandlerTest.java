package dev.bluepitaya.phpmagik;

import dev.bluepitaya.phpmagik.lsp.Json;
import dev.bluepitaya.phpmagik.lsp.dto.Position;
import dev.bluepitaya.phpmagik.lsp.dto.TextDocumentIdentifier;
import dev.bluepitaya.phpmagik.lsp.dto.TextDocumentPosition;
import dev.bluepitaya.phpmagik.lsp.handler.DefinitionHandler;
import dev.bluepitaya.phpmagik.testing.Fixture;
import dev.bluepitaya.phpmagik.ts.Point;
import dev.bluepitaya.phpmagik.ts.Range;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DefinitionHandlerTest {

    @Test
    void jumpsFromAVariableUsageToItsAssignment() throws IOException {
        try (Fixture fixture = Fixture.index("php_vars")) {
            var handler = new DefinitionHandler(fixture.workspace(), fixture.finder(),
                    fixture.log());
            String uri = fixture.file("Service1.php").uri();

            /* the "$c" of "return $c + $d + $e->lol;" */
            ObjectNode location = handler.handle(at(uri, 13, 15));

            /* the "$c" of "$c = $a + $b;" */
            assertEquals(
                    Json.location(uri, new Range(new Point(10, 8), new Point(10, 10))),
                    location
            );
        }
    }

    @Test
    void jumpsFromAVariableUsageToItsParameter() throws IOException {
        try (Fixture fixture = Fixture.index("php_vars")) {
            var handler = new DefinitionHandler(fixture.workspace(), fixture.finder(),
                    fixture.log());
            String uri = fixture.file("Service1.php").uri();

            /* the "$a" of "$c = $a + $b;" */
            ObjectNode location = handler.handle(at(uri, 10, 13));

            /* the "$a" of "public function foo(int $a, int $b)" */
            assertEquals(
                    Json.location(uri, new Range(new Point(6, 28), new Point(6, 30))),
                    location
            );
        }
    }

    private static TextDocumentPosition at(String uri, int line, int character) {
        return new TextDocumentPosition(new TextDocumentIdentifier(uri), new Position(line, character));
    }
}
