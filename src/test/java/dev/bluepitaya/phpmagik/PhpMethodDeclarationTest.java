package dev.bluepitaya.phpmagik;

import dev.bluepitaya.phpmagik.phpsymbol.PhpMethodDeclaration;
import dev.bluepitaya.phpmagik.testing.Fixture;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PhpMethodDeclarationTest {

    @Test
    void recordsEveryMethodWithItsOwner() throws IOException {
        try (Fixture fixture = Fixture.index("php_method_declaration")) {
            var methodDeclarations = fixture.workspace()
                    .symbols()
                    .methodDeclarations();
            assertEquals(
                    List.of("one", "two"),
                    methodDeclarations.stream()
                            .map(PhpMethodDeclaration::name)
                            .toList()
            );
            assertEquals(
                    List.of("Service1", "Service1"),
                    methodDeclarations.stream()
                            .map(method -> method.owner().name())
                            .toList()
            );
        }
    }
}
