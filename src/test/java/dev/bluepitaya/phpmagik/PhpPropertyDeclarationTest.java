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
            assertEquals(
                    List.of("Service1", "Service1", "Service1", "Service1"),
                    propertyDeclarations.stream()
                            .map(property -> property.owner().name())
                            .toList()
            );
        }
    }

    @Test
    void hoversWithModifiersInSourceOrder() throws IOException {
        try (Fixture fixture = Fixture.index("php_property_modifiers")) {
            assertEquals(
                    List.of(
                            php("public static int $count"),
                            php("protected readonly string $label"),
                            php("var mixed $legacy"),
                            php("private int $x"),
                            php("private int $y"),
                            php("private readonly Engine $engine")
                    ),
                    fixture.symbols()
                            .propertyDeclarations()
                            .stream()
                            .map(PhpPropertyDeclaration::hover)
                            .toList()
            );
        }
    }

    @Test
    void ownerIsInnermostClassWhoseScopeContainsIt() throws IOException {
        try (Fixture fixture = Fixture.index("php_property_owner")) {
            assertEquals(
                    List.of("$outer Outer", "$inner Inner"),
                    fixture.symbols()
                            .propertyDeclarations()
                            .stream()
                            .map(property -> property.name() + " " + property.owner().name())
                            .toList()
            );
        }
    }

    private static String php(String text) {
        return "```php\n<?php\n" + text + "\n```";
    }
}
