package dev.bluepitaya.phpmagik;

import dev.bluepitaya.phpmagik.phpsymbol.PhpClassDeclaration;
import dev.bluepitaya.phpmagik.testing.Fixture;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PhpClassDeclarationTest {

    @Test
    void resolvesImportedNameButNotTheUseKeyword() throws IOException {
        try (Fixture fixture = Fixture.index("php_class_declaration")) {
            PhpFile file = fixture.file("Service1.php");
            var classDeclarations = fixture.workspace()
                    .symbols()
                    .classDeclarations();
            assertEquals(
                    List.of("InnerClass", "Service1"),
                    classDeclarations.stream()
                            .map(PhpClassDeclaration::name)
                            .toList()
            );
        }
    }
}
