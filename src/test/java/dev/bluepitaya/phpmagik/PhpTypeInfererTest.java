package dev.bluepitaya.phpmagik;

import dev.bluepitaya.phpmagik.phpsymbol.PhpMethodLocalVarDeclaration;
import dev.bluepitaya.phpmagik.phpsymbol.PhpType;
import dev.bluepitaya.phpmagik.testing.Fixture;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PhpTypeInfererTest {

    @Test
    void localsTakeTheTypeOfTheirAssignedExpression() throws IOException {
        try (Fixture fixture = Fixture.index("php_vars")) {
            var locals = fixture.symbols().localVarDeclarations();
            assertEquals(
                    Arrays.asList(PhpType.named("Engine"), null, PhpType.Builtin.Integer),
                    locals.stream().map(PhpMethodLocalVarDeclaration::type).toList()
            );
        }
    }
}
