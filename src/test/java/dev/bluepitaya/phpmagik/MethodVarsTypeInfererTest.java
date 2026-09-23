package dev.bluepitaya.phpmagik;

import dev.bluepitaya.phpmagik.phpsymbol.PhpMethodVarUsage;
import dev.bluepitaya.phpmagik.phpsymbol.PhpType;
import dev.bluepitaya.phpmagik.testing.Fixture;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MethodVarsTypeInfererTest {

    @Test
    void aUsageDefinedByAParameterTakesItsType() throws IOException {
        try (Fixture fixture = Fixture.index("php_vars")) {
            var usages = fixture.symbols().varUsages();
            /* "$e", "$a", "$b", "$c", "$d", "$e" - only the two from "foo(int $a, int $b)" */
            assertEquals(
                    Arrays.asList(null, PhpType.Integer, PhpType.Integer, null, null, null),
                    usages.stream().map(PhpMethodVarUsage::phpType).toList()
            );
        }
    }
}
