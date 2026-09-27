package dev.bluepitaya.phpmagik;

import dev.bluepitaya.phpmagik.phpsymbol.PhpFunctionDefinition;
import dev.bluepitaya.phpmagik.phpsymbol.PhpMethodDeclaration;
import dev.bluepitaya.phpmagik.phpsymbol.PhpType;
import dev.bluepitaya.phpmagik.testing.Fixture;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PhpReturnTypeInfererTest {

    @Test
    void takesTheHintThenTheDocThenTheLastReturnOfAMethod() throws IOException {
        try (Fixture fixture = Fixture.index("php_return_type")) {
            assertEquals(
                    List.of(
                            PhpType.named("App\\Foo"),
                            PhpType.named("App\\Models\\Bar"),
                            PhpType.named("App\\Models\\Bar"),
                            PhpType.named("static")
                    ),
                    fixture.symbols().methodDeclarations().stream()
                            .map(PhpMethodDeclaration::returnType)
                            .toList()
            );
        }
    }

    @Test
    void takesTheDocThenTheLastReturnOfAFunction() throws IOException {
        try (Fixture fixture = Fixture.index("php_return_type")) {
            assertEquals(
                    List.of(PhpType.Builtin.Array, PhpType.named("App\\Models\\Bar")),
                    fixture.symbols().functionDefinitions().stream()
                            .map(PhpFunctionDefinition::returnType)
                            .toList()
            );
        }
    }
}
