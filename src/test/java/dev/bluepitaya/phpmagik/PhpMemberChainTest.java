package dev.bluepitaya.phpmagik;

import dev.bluepitaya.phpmagik.lsp.Json;
import dev.bluepitaya.phpmagik.lsp.dto.Hover;
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

class PhpMemberChainTest {

    @Test
    void hoversAMethodCalledOnAPromotedPropertyWithItsFqn() throws IOException {
        try (Fixture fixture = Fixture.index("php_member_chain")) {
            var handler = new HoverHandler(fixture.workspace(), fixture.finder(), fixture.log());
            Hover hover = handler.handle(at(fixture, 12, 21));

            assertEquals("```php\n<?php\nfunction App\\Models\\Bar::baz($value)\n```", hover.contents().value());
        }
    }

    @Test
    void jumpsFromAMethodCalledOnAPropertyToItsDeclaration() throws IOException {
        try (Fixture fixture = Fixture.index("php_member_chain")) {
            var handler = new DefinitionHandler(fixture.workspace(), fixture.finder(), fixture.log());

            assertEquals(
                    Json.location(fixture.file("Bar.php").uri(), new Range(new Point(6, 20), new Point(6, 23))),
                    handler.handle(at(fixture, 12, 21))
            );
        }
    }

    @Test
    void hoversAPropertyReadOnAPromotedPropertyWithItsType() throws IOException {
        try (Fixture fixture = Fixture.index("php_member_chain")) {
            var handler = new HoverHandler(fixture.workspace(), fixture.finder(), fixture.log());
            Hover hover = handler.handle(at(fixture, 13, 25));

            assertEquals("```php\n<?php\nbool $qux\n```", hover.contents().value());
        }
    }

    @Test
    void hoversAPropertyOfThisWithTheFqnOfItsType() throws IOException {
        try (Fixture fixture = Fixture.index("php_member_chain")) {
            var handler = new HoverHandler(fixture.workspace(), fixture.finder(), fixture.log());
            Hover hover = handler.handle(at(fixture, 12, 16));

            assertEquals("```php\n<?php\nApp\\Models\\Bar $bar\n```", hover.contents().value());
        }
    }

    @Test
    void jumpsFromAPropertyReadOnAPropertyToItsDeclaration() throws IOException {
        try (Fixture fixture = Fixture.index("php_member_chain")) {
            var handler = new DefinitionHandler(fixture.workspace(), fixture.finder(), fixture.log());

            assertEquals(
                    Json.location(fixture.file("Bar.php").uri(), new Range(new Point(7, 16), new Point(7, 20))),
                    handler.handle(at(fixture, 13, 25))
            );
        }
    }

    private static TextDocumentPosition at(Fixture fixture, int line, int character) {
        String uri = fixture.file("Foo.php").uri();
        return new TextDocumentPosition(new TextDocumentIdentifier(uri), new Position(line, character));
    }
}
