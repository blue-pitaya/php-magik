package dev.bluepitaya.phpmagik;

import dev.bluepitaya.phpmagik.lsp.dto.Position;
import dev.bluepitaya.phpmagik.lsp.dto.TextDocumentIdentifier;
import dev.bluepitaya.phpmagik.lsp.dto.TextDocumentPosition;
import dev.bluepitaya.phpmagik.lsp.handler.HoverHandler;
import dev.bluepitaya.phpmagik.phpsymbol.PhpFunctionDefinition;
import dev.bluepitaya.phpmagik.phpsymbol.PhpMethodDeclaration;
import dev.bluepitaya.phpmagik.phpsymbol.PhpType;
import dev.bluepitaya.phpmagik.testing.Fixture;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PhpReturnTypeInfererTest {

    @Test
    void takesTheHintThenTheDocThenTheLastReturnOfAMethod() throws IOException {
        try (Fixture fixture = Fixture.index("php_return_type")) {
            assertEquals(
                    List.of(
                            PhpType.named("App\\Foo"),
                            PhpType.named("App\\Models\\Bar"),
                            PhpType.named("App\\Models\\Bar"),
                            PhpType.named("static")
                    ),
                    fixture.symbols().methodDeclarations().stream()
                            .map(PhpMethodDeclaration::returnType)
                            .toList()
            );
        }
    }

    @Test
    void takesTheDocThenTheLastReturnOfAFunction() throws IOException {
        try (Fixture fixture = Fixture.index("php_return_type")) {
            assertEquals(
                    List.of(PhpType.Builtin.Array, PhpType.named("App\\Models\\Bar")),
                    fixture.symbols().functionDefinitions().stream()
                            .map(PhpFunctionDefinition::returnType)
                            .toList()
            );
        }
    }

    @Test
    void hoversTheInferredReturnTypeWhenThereIsNoHint() throws IOException {
        try (Fixture fixture = Fixture.index("php_return_type")) {
            assertEquals("```php\nfunction App\\Foo::documented(): App\\Models\\Bar\n```", hover(fixture, 19, 21));
            assertEquals("```php\nfunction App\\Foo::returned(): App\\Models\\Bar\n```", hover(fixture, 24, 21));
            assertEquals("```php\nfunction made(): App\\Models\\Bar\n```", hover(fixture, 46, 10));
        }
    }

    private static String hover(Fixture fixture, int line, int character) {
        var handler = new HoverHandler(fixture.workspace(), fixture.finder(), fixture.log());
        String uri = fixture.file("Foo.php").uri();
        return handler.handle(new TextDocumentPosition(new TextDocumentIdentifier(uri), new Position(line, character)))
                .contents()
                .value();
    }
}
