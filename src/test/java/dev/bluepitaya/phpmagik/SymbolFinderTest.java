package dev.bluepitaya.phpmagik;

import dev.bluepitaya.phpmagik.phpsymbol.PhpClassDefinition;
import dev.bluepitaya.phpmagik.phpsymbol.PhpUseStatement;
import dev.bluepitaya.phpmagik.testing.Fixture;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

class SymbolFinderTest {

    @Test
    void resolvesImportedNameButNotTheUseKeyword() throws IOException {
        try (Fixture fixture = Fixture.index("shared")) {
            PhpFile file = fixture.file("main.php");

            var classes = fixture.symbols().classes().stream()
                    .map(PhpClassDefinition::fqn)
                    .sorted()
                    .toList();
            assertEquals(
                    List.of("App\\Models\\User", "App\\Service", "App\\Support\\View"), classes
            );

            PhpUseStatement aliased = assertInstanceOf(PhpUseStatement.class,
                    fixture.finder().resolveAt(file, 4, 10));
            assertEquals("App\\Models\\User", aliased.fqn());
            assertEquals("UserModel", aliased.alias());

            PhpUseStatement plain = assertInstanceOf(PhpUseStatement.class,
                    fixture.finder().resolveAt(file, 5, 10));
            assertEquals("App\\Support\\View", plain.fqn());
            assertEquals("View", plain.alias());

            assertNull(fixture.finder().resolveAt(file, 4, 1));
            assertNull(fixture.finder().resolveAt(file, 5, 20));
        }
    }
}
