package dev.bluepitaya.phpmagik;

import dev.bluepitaya.phpmagik.phpsymbol.PhpMethodVarUsage;
import dev.bluepitaya.phpmagik.phpsymbol.PhpType;
import dev.bluepitaya.phpmagik.testing.Fixture;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VarUsageTypePropagatorTest {

    @Test
    void aUsageTakesItsDefinitionType() throws IOException {
        try (Fixture fixture = Fixture.index("php_vars")) {
            var usages = fixture.symbols().varUsages();
            assertEquals(
                    Arrays.asList(
                            PhpType.named("Engine"),
                            PhpType.Builtin.Integer,
                            PhpType.Builtin.Integer,
                            null,
                            PhpType.Builtin.Integer,
                            PhpType.named("Engine")
                    ),
                    usages.stream().map(PhpMethodVarUsage::type).toList()
            );
        }
    }
}
