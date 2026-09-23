package dev.bluepitaya.phpmagik;

import dev.bluepitaya.phpmagik.phpsymbol.PhpPropertyDeclaration;
import dev.bluepitaya.phpmagik.testing.Fixture;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PhpPropertyDeclarationTest {

    @Test
    void recordsEveryElementAndPromotedParameter() throws IOException {
        try (Fixture fixture = Fixture.index("php_property_declaration")) {
            var propertyDeclarations = fixture.workspace()
                    .symbols()
                    .propertyDeclarations();
            assertEquals(
                    List.of("$a", "$b", "$c", "$engine"),
                    propertyDeclarations.stream()
                            .map(PhpPropertyDeclaration::name)
                            .toList()
            );
        }
    }
}
