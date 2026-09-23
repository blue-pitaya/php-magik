package dev.bluepitaya.phpmagik;

import dev.bluepitaya.phpmagik.phpsymbol.PhpSymbol;
import dev.bluepitaya.phpmagik.testing.Fixture;
import dev.bluepitaya.phpmagik.ts.Point;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DefinitionFillerTest {

    @Test
    void everyVariableUsagePointsAtItsAssignmentOrParameter() throws IOException {
        try (Fixture fixture = Fixture.index("php_vars")) {
            var usages = fixture.symbols().varUsages();
            assertEquals(
                    List.of("$e", "$a", "$b", "$c", "$d", "$e"),
                    usages.stream().map(usage -> usage.name()).toList()
            );
            assertEquals(
                    List.of("8:8", "6:28", "6:36", "10:8", "11:8", "8:8"),
                    usages.stream().map(usage -> startOf(usage.definition())).toList()
            );
        }
    }

    private static String startOf(PhpSymbol definition) {
        if (definition == null) return "none";

        Point start = definition.range().start();

        return start.getRow() + ":" + start.getColumn();
    }
}
