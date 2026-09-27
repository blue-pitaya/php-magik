package dev.bluepitaya.phpmagik;

import dev.bluepitaya.phpmagik.lsp.Json;
import dev.bluepitaya.phpmagik.lsp.dto.Hover;
import dev.bluepitaya.phpmagik.lsp.dto.Position;
import dev.bluepitaya.phpmagik.lsp.dto.TextDocumentIdentifier;
import dev.bluepitaya.phpmagik.lsp.dto.TextDocumentPosition;
import dev.bluepitaya.phpmagik.lsp.handler.DefinitionHandler;
import dev.bluepitaya.phpmagik.lsp.handler.HoverHandler;
import dev.bluepitaya.phpmagik.phpsymbol.PhpClassDeclaration;
import dev.bluepitaya.phpmagik.phpsymbol.PhpReference;
import dev.bluepitaya.phpmagik.testing.Fixture;
import dev.bluepitaya.phpmagik.ts.Point;
import dev.bluepitaya.phpmagik.ts.Range;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PhpClassReferenceTest {

    @Test
    void recordsTheScopeOfStaticAccessesButNotTheMember() throws IOException {
        try (Fixture fixture = Fixture.index("php_class_reference")) {
            var classReferences = classReferences(fixture);
            assertEquals(
                    List.of("User", "Missing", "User"),
                    classReferences.stream()
                            .map(PhpReference::name)
                            .toList()
            );
            assertEquals(
                    List.of(range(10, 16, 20), range(11, 8, 15), range(13, 15, 31)),
                    classReferences.stream()
                            .map(PhpReference::range)
                            .toList()
            );
            assertEquals(
                    Arrays.asList("User", null, "User"),
                    classReferences.stream()
                            .map(PhpReference::definition)
                            .map(definition -> definition instanceof PhpClassDeclaration declaration
                                    ? declaration.name()
                                    : null)
                            .toList()
            );
        }
    }

    @Test
    void hoversTheClassOfAStaticCall() throws IOException {
        try (Fixture fixture = Fixture.index("php_class_reference")) {
            assertEquals(php("class User"), hover(fixture, 10, 17));
        }
    }

    @Test
    void hoversTheClassOfAQualifiedClassConstant() throws IOException {
        try (Fixture fixture = Fixture.index("php_class_reference")) {
            assertEquals(php("class User"), hover(fixture, 13, 16));
            assertEquals(php("class User"), hover(fixture, 13, 28));
        }
    }

    @Test
    void doesNotHoverTheMemberOrAnUnknownClass() throws IOException {
        try (Fixture fixture = Fixture.index("php_class_reference")) {
            assertNull(hover(fixture, 10, 24));
            assertNull(hover(fixture, 11, 9));
        }
    }

    @Test
    void jumpsToTheDeclarationInAnotherFile() throws IOException {
        try (Fixture fixture = Fixture.index("php_class_reference")) {
            var handler = new DefinitionHandler(fixture.workspace(), fixture.finder(),
                    fixture.log());

            assertEquals(
                    Json.location(fixture.file("User.php").uri(), range(4, 6, 10)),
                    handler.handle(at(fixture, 10, 17))
            );
        }
    }

    private static List<PhpReference> classReferences(Fixture fixture) {
        return fixture.symbols().references().stream()
                .filter(reference -> reference.kind() == PhpReference.Kind.CLASS)
                .toList();
    }

    private static String hover(Fixture fixture, int line, int character) {
        var handler = new HoverHandler(fixture.workspace(), fixture.finder(), fixture.log());
        Hover result = handler.handle(at(fixture, line, character));
        return result == null ? null : result.contents().value();
    }

    private static TextDocumentPosition at(Fixture fixture, int line, int character) {
        String uri = fixture.file("Service.php").uri();
        return new TextDocumentPosition(new TextDocumentIdentifier(uri), new Position(line, character));
    }

    private static String php(String text) {
        return "```php\n" + text + "\n```";
    }

    private static Range range(int line, int start, int end) {
        return new Range(new Point(line, start), new Point(line, end));
    }
}
