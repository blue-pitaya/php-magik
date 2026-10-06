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

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PhpDocReferenceTest {

    @Test
    void jumpsFromAClassInsideAnInlineVarDocToItsDeclaration() throws IOException {
        try (Fixture fixture = Fixture.index("php_doc_reference")) {
            assertEquals(barDeclaration(fixture), definition(fixture, 8, 20));
        }
    }

    @Test
    void jumpsFromAClassInsideAMultilineVarDocToItsDeclaration() throws IOException {
        try (Fixture fixture = Fixture.index("php_doc_reference")) {
            assertEquals(barDeclaration(fixture), definition(fixture, 12, 24));
        }
    }

    private static Object barDeclaration(Fixture fixture) {
        return Json.location(fixture.file("Bar.php").uri(), new Range(new Point(4, 6), new Point(4, 9)));
    }

    private static Object definition(Fixture fixture, int line, int character) {
        var handler = new DefinitionHandler(fixture.workspace(), fixture.finder(), fixture.log());
        String uri = fixture.file("Foo.php").uri();
        return handler.handle(new TextDocumentPosition(new TextDocumentIdentifier(uri), new Position(line, character)));
    }
}
