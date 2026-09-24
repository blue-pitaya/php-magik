package dev.bluepitaya.phpmagik;

import dev.bluepitaya.phpmagik.phpsymbol.PhpMethodDeclaration;
import dev.bluepitaya.phpmagik.phpsymbol.PhpParameterDeclaration;
import dev.bluepitaya.phpmagik.phpsymbol.PhpType;
import dev.bluepitaya.phpmagik.testing.Fixture;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PhpParameterDeclarationTest {

    @Test
    void recordsEveryParameterWithItsMethod() throws IOException {
        try (Fixture fixture = Fixture.index("php_method_declaration")) {
            var parameterDeclarations = fixture.workspace()
                    .symbols()
                    .parameterDeclarations();
            assertEquals(
                    List.of("$a", "$b"),
                    parameterDeclarations.stream()
                            .map(PhpParameterDeclaration::name)
                            .toList()
            );
            assertEquals(
                    List.of("two", "two"),
                    parameterDeclarations.stream()
                            .map(parameter -> ((PhpMethodDeclaration) parameter.owner()).name())
                            .toList()
            );
            assertEquals(
                    List.of(PhpType.String, PhpType.Integer),
                    parameterDeclarations.stream()
                            .map(PhpParameterDeclaration::phpType)
                            .toList()
            );
        }
    }
}
