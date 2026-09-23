package dev.bluepitaya.phpmagik;

import dev.bluepitaya.phpmagik.phpsymbol.PhpNamespaceDefinition;
import dev.bluepitaya.phpmagik.testing.Fixture;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PhpNamespaceDefinitionTest {

    @Test
    void recordsEveryDefinitionWithItsQualifiedName() throws IOException {
        try (Fixture fixture = Fixture.index("php_namespace_definition")) {
            var nsDefinitions = fixture.workspace()
                    .symbols()
                    .nsDefinitions();
            assertEquals(
                    List.of("App", "App\\Models", "App\\Support"),
                    nsDefinitions.stream()
                            .map(PhpNamespaceDefinition::name)
                            .sorted()
                            .toList()
            );
        }
    }
}
