package dev.bluepitaya.phpmagik;

import dev.bluepitaya.phpmagik.lsp.Json;
import dev.bluepitaya.phpmagik.lsp.dto.Position;
import dev.bluepitaya.phpmagik.lsp.dto.TextDocumentIdentifier;
import dev.bluepitaya.phpmagik.lsp.dto.TextDocumentPosition;
import dev.bluepitaya.phpmagik.lsp.handler.DefinitionHandler;
import dev.bluepitaya.phpmagik.lsp.handler.HoverHandler;
import dev.bluepitaya.phpmagik.testing.Fixture;
import dev.bluepitaya.phpmagik.ts.Point;
import dev.bluepitaya.phpmagik.ts.Range;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PhpForeachElementTest {

    @Test
    void hoversTheForeachValueWithTheElementTypeOfTheArray() throws IOException {
        try (Fixture fixture = Fixture.index("php_foreach_element")) {
            assertEquals(php("App\\Models\\Bar $bar"), hover(fixture, 13, 33));
        }
    }

    @Test
    void hoversTheValueOfAKeyValueForeachWithTheElementType() throws IOException {
        try (Fixture fixture = Fixture.index("php_foreach_element")) {
            assertEquals(php("App\\Models\\Bar $value"), hover(fixture, 16, 41));
        }
    }

    @Test
    void jumpsFromAMethodCalledOnTheForeachValueToItsDeclaration() throws IOException {
        try (Fixture fixture = Fixture.index("php_foreach_element")) {
            var handler = new DefinitionHandler(fixture.workspace(), fixture.finder(), fixture.log());

            assertEquals(
                    Json.location(fixture.file("Bar.php").uri(), new Range(new Point(6, 20), new Point(6, 23))),
                    handler.handle(at(fixture, 14, 19))
            );
        }
    }

    private static String hover(Fixture fixture, int line, int character) {
        var handler = new HoverHandler(fixture.workspace(), fixture.finder(), fixture.log());
        return handler.handle(at(fixture, line, character)).contents().value();
    }

    private static TextDocumentPosition at(Fixture fixture, int line, int character) {
        String uri = fixture.file("Foo.php").uri();
        return new TextDocumentPosition(new TextDocumentIdentifier(uri), new Position(line, character));
    }

    private static String php(String text) {
        return "```php\n<?php\n" + text + "\n```";
    }
}
