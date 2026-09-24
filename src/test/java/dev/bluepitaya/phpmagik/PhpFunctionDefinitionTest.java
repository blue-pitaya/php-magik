package dev.bluepitaya.phpmagik;

import dev.bluepitaya.phpmagik.phpsymbol.PhpFunctionDefinition;
import dev.bluepitaya.phpmagik.phpsymbol.PhpParameterDeclaration;
import dev.bluepitaya.phpmagik.testing.Fixture;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PhpFunctionDefinitionTest {

    @Test
    void recordsEveryFunction() throws IOException {
        try (Fixture fixture = Fixture.index("php_function_definition")) {
            var functionDefinitions = fixture.workspace()
                    .symbols()
                    .functionDefinitions();
            assertEquals(
                    List.of("hello", "greet"),
                    functionDefinitions.stream()
                            .map(PhpFunctionDefinition::name)
                            .toList()
            );
        }
    }

    @Test
    void recordsFunctionParameters() throws IOException {
        try (Fixture fixture = Fixture.index("php_function_definition")) {
            var parameters = fixture.workspace()
                    .symbols()
                    .parameterDeclarations();
            assertEquals(
                    List.of("$name"),
                    parameters.stream()
                            .map(PhpParameterDeclaration::name)
                            .toList()
            );
            assertEquals(
                    List.of("greet"),
                    parameters.stream()
                            .map(p -> ((PhpFunctionDefinition) p.owner()).name())
                            .toList()
            );
        }
    }
}
