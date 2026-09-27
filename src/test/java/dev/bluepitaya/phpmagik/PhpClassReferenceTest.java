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
import dev.bluepitaya.phpmagik.phpsymbol.PhpClassDeclaration;
import dev.bluepitaya.phpmagik.phpsymbol.PhpReference;
import dev.bluepitaya.phpmagik.testing.Fixture;
import dev.bluepitaya.phpmagik.ts.Point;
import dev.bluepitaya.phpmagik.ts.Range;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ArrayNode;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PhpClassReferenceTest {

    @Test
    void recordsTheScopeOfStaticAccessesButNotTheMember() throws IOException {
        try (Fixture fixture = Fixture.index("php_class_reference")) {
            var classReferences = classReferences(fixture, "Service.php");
            assertEquals(
                    List.of("App\\Models\\User", "App\\Models\\User", "App\\Missing", "App\\Models\\User"),
                    classReferences.stream()
                            .map(PhpReference::name)
                            .toList()
            );
            assertEquals(
                    List.of(range(4, 4, 19), range(10, 16, 20), range(11, 8, 15), range(13, 15, 31)),
                    classReferences.stream()
                            .map(PhpReference::range)
                            .toList()
            );
            assertEquals(
                    Arrays.asList("User", "User", null, "User"),
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
            assertEquals(php("namespace App\\Models;\nclass User"), hover(fixture, 10, 17));
        }
    }

    @Test
    void hoversTheClassOfAQualifiedClassConstant() throws IOException {
        try (Fixture fixture = Fixture.index("php_class_reference")) {
            assertEquals(php("namespace App\\Models;\nclass User"), hover(fixture, 13, 16));
            assertEquals(php("namespace App\\Models;\nclass User"), hover(fixture, 13, 28));
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

    @Test
    void recordsClassImportsAndClassNamesInPropertyAndParameterTypes() throws IOException {
        try (Fixture fixture = Fixture.index("php_class_reference_property")) {
            var classReferences = classReferences(fixture, "Car.php");
            assertEquals(
                    Collections.nCopies(7, "App\\Models\\Engine"),
                    classReferences.stream()
                            .map(PhpReference::name)
                            .toList()
            );
            assertEquals(
                    List.of(range(4, 4, 21), range(5, 4, 21), range(9, 11, 17), range(11, 13, 19),
                            range(13, 15, 21), range(15, 40, 46), range(15, 58, 64)),
                    classReferences.stream()
                            .map(PhpReference::range)
                            .toList()
            );
        }
    }

    @Test
    void hoversTheClassOfAnImport() throws IOException {
        try (Fixture fixture = Fixture.index("php_class_reference_property")) {
            String engine = php("namespace App\\Models;\nclass Engine");
            assertEquals(engine, hover(fixture, "Car.php", 4, 16));
            assertEquals(engine, hover(fixture, "Car.php", 5, 16));
        }
    }

    @Test
    void hoversTheClassOfAPropertyType() throws IOException {
        try (Fixture fixture = Fixture.index("php_class_reference_property")) {
            String engine = php("namespace App\\Models;\nclass Engine");
            assertEquals(engine, hover(fixture, "Car.php", 9, 12));
            assertEquals(engine, hover(fixture, "Car.php", 11, 14));
            assertEquals(engine, hover(fixture, "Car.php", 13, 16));
            assertEquals(engine, hover(fixture, "Car.php", 15, 41));
        }
    }

    @Test
    void recordsClassNamesInInheritanceInstanceofAndInstantiation() throws IOException {
        try (Fixture fixture = Fixture.index("php_class_reference_positions")) {
            var classReferences = classReferences(fixture, "Car.php");
            assertEquals(
                    List.of("App\\Base", "App\\Drivable", "App\\Base", "App\\Car", "App\\Car", "App\\Car", "App\\Car",
                            "App\\Car"),
                    classReferences.stream()
                            .map(PhpReference::name)
                            .toList()
            );
            assertEquals(
                    List.of(range(4, 18, 22), range(4, 34, 42), range(6, 32, 36), range(6, 45, 48),
                            range(8, 29, 32), range(9, 19, 23), range(12, 15, 21), range(12, 33, 36)),
                    classReferences.stream()
                            .map(PhpReference::range)
                            .toList()
            );
            assertEquals(
                    Arrays.asList("Base", null, "Base", "Car", "Car", "Car", "Car", "Car"),
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
    void hoversSelfAndStaticAsTheEnclosingClass() throws IOException {
        try (Fixture fixture = Fixture.index("php_class_reference_positions")) {
            String car = php("namespace App;\nclass Car");
            assertEquals(car, hover(fixture, "Car.php", 9, 20));
            assertEquals(car, hover(fixture, "Car.php", 12, 16));
        }
    }

    @Test
    void findsReferencesOfTheImportedClassAmongClassesSharingAShortName() throws IOException {
        try (Fixture fixture = Fixture.index("php_class_reference_fqn")) {
            var handler = new ReferencesHandler(fixture.workspace(), fixture.finder(), fixture.log());
            String declarationUri = fixture.file("Users/ShowProps.php").uri();
            String controllerUri = fixture.file("Controller.php").uri();

            ArrayNode expected = Json.array();
            expected.add(Json.location(controllerUri, range(4, 4, 28)));
            expected.add(Json.location(controllerUri, range(8, 28, 37)));
            expected.add(Json.location(controllerUri, range(10, 19, 28)));
            expected.add(Json.location(controllerUri, range(13, 26, 35)));
            assertEquals(expected, handler.handle(new ReferenceParams(
                    new TextDocumentIdentifier(declarationUri),
                    new Position(5, 6),
                    new ReferenceContext(false)
            )));
        }
    }

    @Test
    void jumpsFromAMethodCallToTheMethodOfTheImportedClass() throws IOException {
        try (Fixture fixture = Fixture.index("php_class_reference_fqn")) {
            var handler = new DefinitionHandler(fixture.workspace(), fixture.finder(), fixture.log());

            assertEquals(
                    Json.location(fixture.file("Users/ShowProps.php").uri(), range(7, 20, 25)),
                    handler.handle(at(fixture, "Controller.php", 15, 24))
            );
        }
    }

    private static List<PhpReference> classReferences(Fixture fixture, String file) {
        PhpFile phpFile = fixture.file(file);
        return fixture.symbols().references().stream()
                .filter(reference -> reference.kind() == PhpReference.Kind.CLASS)
                .filter(reference -> reference.file() == phpFile)
                .toList();
    }

    private static String hover(Fixture fixture, int line, int character) {
        return hover(fixture, "Service.php", line, character);
    }

    private static String hover(Fixture fixture, String file, int line, int character) {
        var handler = new HoverHandler(fixture.workspace(), fixture.finder(), fixture.log());
        Hover result = handler.handle(at(fixture, file, line, character));
        return result == null ? null : result.contents().value();
    }

    private static TextDocumentPosition at(Fixture fixture, int line, int character) {
        return at(fixture, "Service.php", line, character);
    }

    private static TextDocumentPosition at(Fixture fixture, String file, int line, int character) {
        String uri = fixture.file(file).uri();
        return new TextDocumentPosition(new TextDocumentIdentifier(uri), new Position(line, character));
    }

    private static String php(String text) {
        return "```php\n" + text + "\n```";
    }

    private static Range range(int line, int start, int end) {
        return new Range(new Point(line, start), new Point(line, end));
    }
}
