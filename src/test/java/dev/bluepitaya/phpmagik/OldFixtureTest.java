package dev.bluepitaya.phpmagik;

import dev.bluepitaya.phpmagik.lsp.Json;
import dev.bluepitaya.phpmagik.lsp.dto.Hover;
import dev.bluepitaya.phpmagik.lsp.dto.Position;
import dev.bluepitaya.phpmagik.lsp.dto.ReferenceContext;
import dev.bluepitaya.phpmagik.lsp.dto.ReferenceParams;
import dev.bluepitaya.phpmagik.lsp.dto.TextDocumentIdentifier;
import dev.bluepitaya.phpmagik.lsp.dto.TextDocumentPosition;
import dev.bluepitaya.phpmagik.lsp.handler.DefinitionHandler;
import dev.bluepitaya.phpmagik.lsp.handler.HoverHandler;
import dev.bluepitaya.phpmagik.lsp.handler.ReferencesHandler;
import dev.bluepitaya.phpmagik.resolver.PhpMethodDeclarationResolver;
import dev.bluepitaya.phpmagik.testing.Fixture;
import dev.bluepitaya.phpmagik.ts.Point;
import dev.bluepitaya.phpmagik.ts.Range;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class OldFixtureTest {

    private static String php(String text) {
        return "```php\n" + text + "\n```";
    }

    private static String hover(Fixture fixture, String fileName, int line, int character) {
        var handler = new HoverHandler(
                fixture.workspace(), fixture.finder(),
                new PhpMethodDeclarationResolver(fixture.workspace()),
                fixture.log());
        String uri = fixture.file(fileName).uri();
        Hover result = handler.handle(at(uri, line, character));
        return result == null ? null : result.contents().value();
    }

    private static ObjectNode definition(Fixture fixture, String fileName, int line, int character) {
        var handler = new DefinitionHandler(fixture.workspace(), fixture.finder(), fixture.log());
        String uri = fixture.file(fileName).uri();
        return handler.handle(at(uri, line, character));
    }

    private static ArrayNode references(Fixture fixture, String fileName, int line, int character,
                                         boolean includeDeclaration) {
        var handler = new ReferencesHandler(fixture.workspace(), fixture.finder(), fixture.log());
        String uri = fixture.file(fileName).uri();
        return handler.handle(ref(uri, line, character, includeDeclaration));
    }

    private static TextDocumentPosition at(String uri, int line, int character) {
        return new TextDocumentPosition(new TextDocumentIdentifier(uri), new Position(line, character));
    }

    private static ReferenceParams ref(String uri, int line, int character, boolean includeDeclaration) {
        return new ReferenceParams(
                new TextDocumentIdentifier(uri),
                new Position(line, character),
                new ReferenceContext(includeDeclaration));
    }

    private static ObjectNode loc(String uri, int startLine, int startChar, int endLine, int endChar) {
        return Json.location(uri, new Range(new Point(startLine, startChar), new Point(endLine, endChar)));
    }

    @Test
    void test_1_standaloneFunctionHover() throws IOException {
        try (Fixture f = Fixture.index("old_tests/test_1")) {
            assertEquals(php("function zero"), hover(f, "main.php", 2, 9));
            assertEquals(php("function zerof"), hover(f, "main.php", 7, 9));
            assertEquals(php("function empty_string"), hover(f, "main.php", 12, 9));
        }
    }

    @Test
    void test_2_standaloneFunctionHoverExpressions() throws IOException {
        try (Fixture f = Fixture.index("old_tests/test_2")) {
            assertEquals(php("function add"), hover(f, "main.php", 2, 9));
            assertEquals(php("function add2"), hover(f, "main.php", 7, 9));
            assertEquals(php("function add3"), hover(f, "main.php", 12, 9));
        }
    }

    @Test
    void test_3_declaredParamTypes() throws IOException {
        try (Fixture f = Fixture.index("old_tests/test_3")) {
            assertEquals(php("add(int $a)"), hover(f, "main.php", 2, 17));
            assertEquals(php("add(int $b)"), hover(f, "main.php", 2, 25));
            assertEquals(php("add(): int $a"), hover(f, "main.php", 4, 11));
            assertEquals(php("add(): int $b"), hover(f, "main.php", 4, 16));
        }
    }

    @Test
    void test_4_typesThroughLocalVarAssignments() throws IOException {
        try (Fixture f = Fixture.index("old_tests/test_4")) {
            assertEquals(php("add(int $a)"), hover(f, "main.php", 2, 17));
            assertEquals(php("add(int $b)"), hover(f, "main.php", 2, 25));
            assertEquals(php("add(): $c"), hover(f, "main.php", 4, 4));
            assertEquals(php("add(): int $a"), hover(f, "main.php", 4, 9));
            assertEquals(php("add(): int $b"), hover(f, "main.php", 4, 14));
            assertEquals(php("add(): $d"), hover(f, "main.php", 5, 4));
            assertEquals(php("add(): int $a"), hover(f, "main.php", 5, 9));
            assertEquals(php("add(): int $b"), hover(f, "main.php", 5, 14));
            assertEquals(php("add(): $e"), hover(f, "main.php", 6, 4));
            assertEquals(php("add(): $c"), hover(f, "main.php", 6, 9));
            assertEquals(php("add(): $d"), hover(f, "main.php", 6, 14));
            assertEquals(php("add(): $e"), hover(f, "main.php", 8, 11));
        }
    }

    @Test
    void test_5_methodsAcrossTwoClasses() throws IOException {
        try (Fixture f = Fixture.index("old_tests/test_5")) {
            assertEquals(php("print(int $a)"), hover(f, "main.php", 6, 30));
            assertEquals(php("print(): $b"), hover(f, "main.php", 8, 8));
            assertEquals(php("print(): int $a"), hover(f, "main.php", 8, 13));
            assertEquals(php("print(): $b"), hover(f, "main.php", 10, 15));
        }
    }

    @Test
    void test_6_typedPropertiesAndThis() throws IOException {
        try (Fixture f = Fixture.index("old_tests/test_6")) {
            assertEquals(php("Foo::$bar"), hover(f, "main.php", 6, 15));
            assertEquals(php("Foo::$x1"), hover(f, "main.php", 8, 18));
            assertEquals(php("Foo::$x2"), hover(f, "main.php", 10, 18));
            assertEquals(php("print(int $a)"), hover(f, "main.php", 12, 30));
            assertEquals(php("print(): $b"), hover(f, "main.php", 15, 8));
            assertEquals(php("print(): $c"), hover(f, "main.php", 16, 8));
            assertEquals(php("print(): int $a"), hover(f, "main.php", 16, 13));
            assertEquals(php("print(): $b"), hover(f, "main.php", 16, 18));
        }
    }

    @Test
    void test_9_globalVarNotSupported() throws IOException {
        try (Fixture f = Fixture.index("old_tests/test_9")) {
            assertNull(definition(f, "main.php", 3, 5));
        }
    }

    @Test
    void test_14_definitionParamUsage() throws IOException {
        try (Fixture f = Fixture.index("old_tests/test_14")) {
            String uri = f.file("main.php").uri();
            assertEquals(loc(uri, 2, 17, 2, 19), definition(f, "main.php", 4, 11));
        }
    }

    @Test
    void test_25_referencesLocalVar() throws IOException {
        try (Fixture f = Fixture.index("old_tests/test_25")) {
            String uri = f.file("main.php").uri();
            ArrayNode withDecl = references(f, "main.php", 5, 9, true);
            ArrayNode expectedWith = Json.array();
            expectedWith.add(loc(uri, 4, 4, 4, 8));
            expectedWith.add(loc(uri, 5, 9, 5, 13));
            expectedWith.add(loc(uri, 6, 11, 6, 15));
            assertEquals(expectedWith, withDecl);

            ArrayNode withoutDecl = references(f, "main.php", 5, 9, false);
            ArrayNode expectedWithout = Json.array();
            expectedWithout.add(loc(uri, 5, 9, 5, 13));
            expectedWithout.add(loc(uri, 6, 11, 6, 15));
            assertEquals(expectedWithout, withoutDecl);
        }
    }

    @Test
    void test_30_scopeIsolationLocalVars() throws IOException {
        try (Fixture f = Fixture.index("old_tests/test_30")) {
            String uri = f.file("main.php").uri();
            ArrayNode refs = references(f, "main.php", 4, 4, true);
            ArrayNode expected = Json.array();
            expected.add(loc(uri, 4, 4, 4, 6));
            expected.add(loc(uri, 5, 11, 5, 13));
            assertEquals(expected, refs);
        }
    }

    @Test
    void test_31_parameterReferences() throws IOException {
        try (Fixture f = Fixture.index("old_tests/test_31")) {
            String uri = f.file("main.php").uri();
            ArrayNode refs = references(f, "main.php", 2, 23, true);
            ArrayNode expected = Json.array();
            expected.add(loc(uri, 2, 23, 2, 25));
            expected.add(loc(uri, 4, 9, 4, 11));
            expected.add(loc(uri, 5, 9, 5, 11));
            expected.add(loc(uri, 6, 11, 6, 13));
            assertEquals(expected, refs);
        }
    }

    @Test
    void test_32_localVarReassignment() throws IOException {
        try (Fixture f = Fixture.index("old_tests/test_32")) {
            String uri = f.file("main.php").uri();
            ArrayNode refs = references(f, "main.php", 7, 18, true);
            ArrayNode expected = Json.array();
            expected.add(loc(uri, 7, 8, 7, 15));
            expected.add(loc(uri, 7, 18, 7, 25));
            expected.add(loc(uri, 8, 15, 8, 22));
            assertEquals(expected, refs);
        }
    }
}
