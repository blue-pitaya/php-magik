package dev.bluepitaya.phpmagik;

import dev.bluepitaya.phpmagik.phpsymbol.PhpMethodLocalVarDeclaration;
import dev.bluepitaya.phpmagik.phpsymbol.PhpMethodVarUsage;
import dev.bluepitaya.phpmagik.testing.Fixture;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PhpMethodVarTest {

    @Test
    void assignedVariablesAreDeclarationsAndTheRestAreUsages() throws IOException {
        try (Fixture fixture = Fixture.index("php_vars")) {
            var symbols = fixture.workspace().symbols();
            assertEquals(
                    List.of("$e", "$c", "$d"),
                    symbols.localVarDeclarations().stream()
                            .map(PhpMethodLocalVarDeclaration::name)
                            .toList()
            );
            assertEquals(
                    List.of("$e", "$a", "$b", "$c", "$d", "$e"),
                    symbols.varUsages().stream()
                            .map(PhpMethodVarUsage::name)
                            .toList()
            );
            assertEquals(
                    List.of("foo"),
                    symbols.localVarDeclarations().stream()
                            .map(declaration -> declaration.owner().name())
                            .distinct()
                            .toList()
            );
        }
    }
}
