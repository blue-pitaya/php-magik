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
    void test_8_multiFileNamespaces() throws IOException {
        try (Fixture f = Fixture.index("old_tests/test_8")) {
            assertEquals(php("namespace App"), hover(f, "Foo.php", 2, 10));
            assertEquals(php("class Foo"), hover(f, "Foo.php", 7, 6));
            assertEquals(php("function Foo::call"), hover(f, "Foo.php", 9, 20));
            assertEquals(php("call(): $a"), hover(f, "Foo.php", 11, 8));
            assertEquals(php("call(): $b"), hover(f, "Foo.php", 12, 8));
            assertEquals(php("call(): $a"), hover(f, "Foo.php", 14, 13));
            assertEquals(php("call(): $b"), hover(f, "Foo.php", 15, 13));

            assertEquals(php("namespace App\\NSA"), hover(f, "A.php", 2, 10));
            assertEquals(php("class A"), hover(f, "A.php", 4, 6));
            assertEquals(php("function A::get"), hover(f, "A.php", 6, 20));

            assertEquals(php("namespace App\\NSB"), hover(f, "B.php", 2, 10));
            assertEquals(php("class B"), hover(f, "B.php", 4, 6));
            assertEquals(php("function B::get"), hover(f, "B.php", 6, 20));
        }
    }

    @Test
    void test_9_globalVarNotSupported() throws IOException {
        try (Fixture f = Fixture.index("old_tests/test_9")) {
            assertNull(definition(f, "main.php", 3, 5));
        }
    }

    @Test
    void test_10_multiFileClassAndGlobalScope() throws IOException {
        try (Fixture f = Fixture.index("old_tests/test_10")) {
            assertEquals(php("class Greeter"), hover(f, "Greeter.php", 2, 6));
            assertEquals(php("Greeter::$name"), hover(f, "Greeter.php", 4, 18));
            assertEquals(php("function Greeter::__construct"), hover(f, "Greeter.php", 6, 20));
            assertEquals(php("__construct(string $name)"), hover(f, "Greeter.php", 6, 39));
            assertEquals(php("__construct(): string $name"), hover(f, "Greeter.php", 8, 22));
            assertEquals(php("function Greeter::greet"), hover(f, "Greeter.php", 11, 20));
            assertEquals(php("function Greeter::shout"), hover(f, "Greeter.php", 16, 20));

            assertNull(hover(f, "main.php", 2, 0));
        }
    }

    @Test
    void test_11_globalScopeVarsNotSupported() throws IOException {
        try (Fixture f = Fixture.index("old_tests/test_11")) {
            assertNull(hover(f, "main.php", 2, 0));
            assertNull(hover(f, "main.php", 3, 5));
        }
    }

    @Test
    void test_12_classAndStandaloneFunction() throws IOException {
        try (Fixture f = Fixture.index("old_tests/test_12")) {
            assertEquals(php("class Box"), hover(f, "main.php", 2, 6));
            assertEquals(php("Box::$value"), hover(f, "main.php", 4, 15));
            assertEquals(php("function Box::get"), hover(f, "main.php", 6, 20));
            assertEquals(php("function main"), hover(f, "main.php", 12, 9));
            assertEquals(php("main(): $b"), hover(f, "main.php", 14, 4));
            assertEquals(php("main(): $x"), hover(f, "main.php", 15, 4));
        }
    }

    @Test
    void test_13_functionCallNotResolved() throws IOException {
        try (Fixture f = Fixture.index("old_tests/test_13")) {
            assertEquals(php("function greet"), hover(f, "main.php", 2, 9));
            assertNull(hover(f, "main.php", 7, 5));
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
    void test_7_propertyChainAndNamedParamType() throws IOException {
        try (Fixture f = Fixture.index("old_tests/test_7")) {
            assertEquals(php("class Engine"), hover(f, "main.php", 2, 6));
            assertEquals(php("class Car"), hover(f, "main.php", 9, 6));
            assertEquals(php("Engine::$power"), hover(f, "main.php", 4, 15));
            assertEquals(php("Engine::$fuel"), hover(f, "main.php", 6, 18));
            assertEquals(php("Car::$engine"), hover(f, "main.php", 11, 19));
            assertEquals(php("function Car::__construct"), hover(f, "main.php", 13, 20));
            assertEquals(php("function Car::describe"), hover(f, "main.php", 18, 20));
            assertEquals(php("__construct(Engine $engine)"), hover(f, "main.php", 13, 39));
            assertEquals(php("__construct(): Engine $engine"), hover(f, "main.php", 15, 24));
            assertEquals(php("describe(): $total"), hover(f, "main.php", 20, 8));
            assertEquals(php("describe(): $total"), hover(f, "main.php", 22, 39));
        }
    }

    @Test
    void test_42_namedParamTypeInStandaloneFunction() throws IOException {
        try (Fixture f = Fixture.index("old_tests/test_42")) {
            assertEquals(php("function run"), hover(f, "main.php", 2, 9));
            assertEquals(php("run(Container $c)"), hover(f, "main.php", 2, 23));
            assertEquals(php("run(): $e"), hover(f, "main.php", 4, 4));
            assertEquals(php("run(): Container $c"), hover(f, "main.php", 4, 9));
            assertEquals(php("run(): $e"), hover(f, "main.php", 6, 11));
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

    @Test
    void test_40_functionsWithDocblocks() throws IOException {
        try (Fixture f = Fixture.index("old_tests/test_40")) {
            assertEquals(php("function add"), hover(f, "main.php", 8, 9));
            assertEquals(php("function plain"), hover(f, "main.php", 14, 9));
            assertEquals(php("function undocumented"), hover(f, "main.php", 19, 9));
            assertEquals(php("class Calc"), hover(f, "main.php", 24, 6));
            assertEquals(php("function Calc::double"), hover(f, "main.php", 29, 20));
        }
    }

    @Test
    void test_41_bracedNamespaces() throws IOException {
        try (Fixture f = Fixture.index("old_tests/test_41")) {
            assertEquals(php("class A"), hover(f, "main.php", 3, 10));
            assertEquals(php("function A::get"), hover(f, "main.php", 5, 24));
            assertEquals(php("class Foo"), hover(f, "main.php", 16, 10));
            assertEquals(php("function Foo::call"), hover(f, "main.php", 18, 24));
            assertEquals(php("call(A $a)"), hover(f, "main.php", 18, 31));
            assertEquals(php("call(int $n)"), hover(f, "main.php", 18, 51));
            assertEquals(php("call(Foo $self)"), hover(f, "main.php", 18, 59));
        }
    }

    @Test
    void test_43_classImplementsInterface() throws IOException {
        try (Fixture f = Fixture.index("old_tests/test_43")) {
            assertEquals(php("class Square"), hover(f, "main.php", 2, 6));
            assertEquals(php("function make"), hover(f, "main.php", 6, 9));
        }
    }

    @Test
    void test_15_propertyDefinition() throws IOException {
        try (Fixture f = Fixture.index("old_tests/test_15")) {
            String uri = f.file("main.php").uri();
            assertEquals(loc(uri, 4, 15, 4, 17), definition(f, "main.php", 9, 15));
            assertEquals(loc(uri, 4, 15, 4, 17), definition(f, "main.php", 4, 15));
        }
    }

    @Test
    void test_16_forwardFunctionReference() throws IOException {
        try (Fixture f = Fixture.index("old_tests/test_16")) {
            String uri = f.file("main.php").uri();
            assertEquals(loc(uri, 4, 9, 4, 15), definition(f, "main.php", 2, 5));
        }
    }

    @Test
    void test_17_objectMethodCall() throws IOException {
        try (Fixture f = Fixture.index("old_tests/test_17")) {
            String uri = f.file("main.php").uri();
            assertEquals(loc(uri, 4, 20, 4, 29), definition(f, "main.php", 13, 15));
        }
    }

    @Test
    void test_23_functionReferences() throws IOException {
        try (Fixture f = Fixture.index("old_tests/test_23")) {
            String uri = f.file("main.php").uri();
            ArrayNode withDecl = references(f, "main.php", 2, 9, true);
            ArrayNode expectedWith = Json.array();
            expectedWith.add(loc(uri, 2, 9, 2, 15));
            expectedWith.add(loc(uri, 7, 5, 7, 11));
            expectedWith.add(loc(uri, 8, 5, 8, 11));
            assertEquals(expectedWith, withDecl);

            ArrayNode withoutDecl = references(f, "main.php", 2, 9, false);
            ArrayNode expectedWithout = Json.array();
            expectedWithout.add(loc(uri, 7, 5, 7, 11));
            expectedWithout.add(loc(uri, 8, 5, 8, 11));
            assertEquals(expectedWithout, withoutDecl);
        }
    }

    @Test
    void test_24_propertyReferences() throws IOException {
        try (Fixture f = Fixture.index("old_tests/test_24")) {
            String uri = f.file("main.php").uri();
            ArrayNode refs = references(f, "main.php", 4, 15, true);
            ArrayNode expected = Json.array();
            expected.add(loc(uri, 4, 15, 4, 17));
            expected.add(loc(uri, 8, 15, 8, 16));
            expected.add(loc(uri, 14, 15, 14, 16));
            assertEquals(expected, refs);
        }
    }

    @Test
    void test_26_methodReferencesAcrossFiles() throws IOException {
        try (Fixture f = Fixture.index("old_tests/test_26")) {
            String loggerUri = f.file("Logger.php").uri();
            String aUri = f.file("A.php").uri();
            String bUri = f.file("B.php").uri();
            ArrayNode refs = references(f, "Logger.php", 4, 20, true);
            ArrayNode expected = Json.array();
            expected.add(loc(loggerUri, 4, 20, 4, 23));
            expected.add(loc(aUri, 4, 8, 4, 11));
            expected.add(loc(bUri, 4, 8, 4, 11));
            assertEquals(expected, refs);
        }
    }

    @Test
    void test_27_methodWithNoUsages() throws IOException {
        try (Fixture f = Fixture.index("old_tests/test_27")) {
            String uri = f.file("main.php").uri();
            assertEquals(Json.array(), references(f, "main.php", 4, 20, false));

            ArrayNode withDecl = references(f, "main.php", 4, 20, true);
            ArrayNode expected = Json.array();
            expected.add(loc(uri, 4, 20, 4, 26));
            assertEquals(expected, withDecl);
        }
    }

    @Test
    void test_28_functionReferencesFromCallSite() throws IOException {
        try (Fixture f = Fixture.index("old_tests/test_28")) {
            String uri = f.file("main.php").uri();
            ArrayNode refs = references(f, "main.php", 7, 5, true);
            ArrayNode expected = Json.array();
            expected.add(loc(uri, 2, 9, 2, 15));
            expected.add(loc(uri, 7, 5, 7, 11));
            expected.add(loc(uri, 8, 5, 8, 11));
            assertEquals(expected, refs);
        }
    }

    @Test
    void test_29_methodReferencesIsolatedByClass() throws IOException {
        try (Fixture f = Fixture.index("old_tests/test_29")) {
            String uri = f.file("main.php").uri();
            ArrayNode refs = references(f, "main.php", 4, 20, true);
            ArrayNode expected = Json.array();
            expected.add(loc(uri, 4, 20, 4, 25));
            expected.add(loc(uri, 22, 10, 22, 15));
            assertEquals(expected, refs);
        }
    }

    @Test
    void test_33_propertyUsedOnceViaThis() throws IOException {
        try (Fixture f = Fixture.index("old_tests/test_33")) {
            String uri = f.file("main.php").uri();
            ArrayNode withoutDecl = references(f, "main.php", 4, 15, false);
            ArrayNode expectedWithout = Json.array();
            expectedWithout.add(loc(uri, 8, 22, 8, 27));
            assertEquals(expectedWithout, withoutDecl);

            ArrayNode withDecl = references(f, "main.php", 4, 15, true);
            ArrayNode expectedWith = Json.array();
            expectedWith.add(loc(uri, 4, 15, 4, 21));
            expectedWith.add(loc(uri, 8, 22, 8, 27));
            assertEquals(expectedWith, withDecl);
        }
    }

    @Test
    void test_34_twoCallsSameLine() throws IOException {
        try (Fixture f = Fixture.index("old_tests/test_34")) {
            String uri = f.file("main.php").uri();
            ArrayNode refs = references(f, "main.php", 2, 9, true);
            ArrayNode expected = Json.array();
            expected.add(loc(uri, 2, 9, 2, 12));
            expected.add(loc(uri, 7, 5, 7, 8));
            expected.add(loc(uri, 7, 14, 7, 17));
            assertEquals(expected, refs);
        }
    }

    @Test
    void test_35_globalFunctionNotMethod() throws IOException {
        try (Fixture f = Fixture.index("old_tests/test_35")) {
            String uri = f.file("main.php").uri();
            ArrayNode refs = references(f, "main.php", 2, 9, true);
            ArrayNode expected = Json.array();
            expected.add(loc(uri, 2, 9, 2, 15));
            expected.add(loc(uri, 17, 4, 17, 10));
            assertEquals(expected, refs);
        }
    }
}
