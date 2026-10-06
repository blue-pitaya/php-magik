package dev.bluepitaya.phpmagik;

import dev.bluepitaya.phpmagik.lsp.Json;
import dev.bluepitaya.phpmagik.lsp.dto.Position;
import dev.bluepitaya.phpmagik.lsp.dto.TextDocumentIdentifier;
import dev.bluepitaya.phpmagik.lsp.dto.TextDocumentPosition;
import dev.bluepitaya.phpmagik.lsp.handler.DefinitionHandler;
import dev.bluepitaya.phpmagik.phpsymbol.PhpMethodDeclaration;
import dev.bluepitaya.phpmagik.phpsymbol.PhpStaticCall;
import dev.bluepitaya.phpmagik.testing.Fixture;
import dev.bluepitaya.phpmagik.ts.Point;
import dev.bluepitaya.phpmagik.ts.Range;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PhpStaticCallTest {

    @Test
    void recordsAStaticMethodCalledOnSelfAsAMethodReference() throws IOException {
        try (Fixture fixture = Fixture.index("php_static_call")) {
            PhpFile file = fixture.file("Foo.php");
            var staticCalls = fixture.symbols().references().stream()
                    .filter(reference -> reference.file() == file)
                    .filter(PhpStaticCall.class::isInstance)
                    .map(PhpStaticCall.class::cast)
                    .toList();

            assertEquals(List.of("baz"), staticCalls.stream().map(PhpStaticCall::name).toList());
            assertEquals(List.of(range(12, 21, 24)), staticCalls.stream().map(PhpStaticCall::range).toList());
            assertEquals(
                    List.of(range(8, 27, 30)),
                    staticCalls.stream()
                            .map(PhpStaticCall::definition)
                            .map(PhpMethodDeclaration::range)
                            .toList()
            );
        }
    }

    @Test
    void jumpsFromAStaticMethodCalledOnSelfToItsDeclaration() throws IOException {
        try (Fixture fixture = Fixture.index("php_static_call")) {
            var handler = new DefinitionHandler(fixture.workspace(), fixture.finder(), fixture.log());
            String uri = fixture.file("Foo.php").uri();

            assertEquals(
                    Json.location(uri, range(8, 27, 30)),
                    handler.handle(new TextDocumentPosition(new TextDocumentIdentifier(uri), new Position(12, 22)))
            );
        }
    }

    private static Range range(int line, int start, int end) {
        return new Range(new Point(line, start), new Point(line, end));
    }
}
