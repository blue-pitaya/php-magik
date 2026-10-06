package dev.bluepitaya.phpmagik;

import dev.bluepitaya.phpmagik.phpsymbol.PhpPropertyDeclaration;
import dev.bluepitaya.phpmagik.phpsymbol.PhpType;
import dev.bluepitaya.phpmagik.testing.Fixture;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PhpVarDocTest {

    private static final PhpType BAR = PhpType.named("App\\Models\\Bar");

    @Test
    void varDocTypeShadowsTheDeclaredPropertyType() throws IOException {
        try (Fixture fixture = Fixture.index("php_var_doc")) {
            assertEquals(
                    List.of(
                            PhpType.arrayOf(BAR),
                            PhpType.arrayOf(BAR),
                            PhpType.arrayOf(BAR),
                            PhpType.arrayOf(PhpType.Builtin.String),
                            BAR,
                            PhpType.union(List.of(BAR, PhpType.Builtin.Null))
                    ),
                    fixture.symbols()
                            .propertyDeclarations()
                            .stream()
                            .map(PhpPropertyDeclaration::type)
                            .toList()
            );
        }
    }

    @Test
    void hoversThePropertyWithItsArrayElementType() throws IOException {
        try (Fixture fixture = Fixture.index("php_var_doc")) {
            assertEquals(
                    "```php\n<?php\nprivate array<App\\Models\\Bar> $bars\n```",
                    fixture.symbols().propertyDeclarations().getFirst().hover()
            );
        }
    }
}
