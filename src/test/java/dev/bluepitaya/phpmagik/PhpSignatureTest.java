package dev.bluepitaya.phpmagik;

import dev.bluepitaya.phpmagik.lsp.dto.Hover;
import dev.bluepitaya.phpmagik.lsp.dto.Position;
import dev.bluepitaya.phpmagik.lsp.dto.TextDocumentIdentifier;
import dev.bluepitaya.phpmagik.lsp.dto.TextDocumentPosition;
import dev.bluepitaya.phpmagik.lsp.handler.HoverHandler;
import dev.bluepitaya.phpmagik.testing.Fixture;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PhpSignatureTest {

    @Test
    void hoversAConstructorWithItsPromotedParameter() throws IOException {
        try (Fixture fixture = Fixture.index("php_signature")) {
            assertEquals(
                    php("function App\\Foo::__construct(App\\Models\\Bar $bar)"),
                    hover(fixture, 8, 21)
            );
        }
    }

    @Test
    void hoversAMethodWithNullableUnionAndVariadicParametersAndItsReturnType() throws IOException {
        try (Fixture fixture = Fixture.index("php_signature")) {
            assertEquals(
                    php("function App\\Foo::run(?App\\Models\\Bar $bar, int|App\\Models\\Bar $mixed, string ...$rest)"
                            + ": ?App\\Models\\Bar"),
                    hover(fixture, 10, 21)
            );
        }
    }

    @Test
    void hoversAFunctionWithItsParametersAndReturnType() throws IOException {
        try (Fixture fixture = Fixture.index("php_signature")) {
            assertEquals(
                    php("function helper(App\\Models\\Bar $bar): App\\Models\\Bar"),
                    hover(fixture, 15, 10)
            );
        }
    }

    private static String hover(Fixture fixture, int line, int character) {
        var handler = new HoverHandler(fixture.workspace(), fixture.finder(), fixture.log());
        String uri = fixture.file("Foo.php").uri();
        Hover result = handler.handle(new TextDocumentPosition(
                new TextDocumentIdentifier(uri), new Position(line, character)));
        return result.contents().value();
    }

    private static String php(String text) {
        return "```php\n" + text + "\n```";
    }
}
