package dev.bluepitaya.phpmagik;

import dev.bluepitaya.phpmagik.lsp.dto.Position;
import dev.bluepitaya.phpmagik.lsp.dto.TextDocumentIdentifier;
import dev.bluepitaya.phpmagik.lsp.dto.TextDocumentPosition;
import dev.bluepitaya.phpmagik.lsp.handler.HoverHandler;
import dev.bluepitaya.phpmagik.phpsymbol.PhpParameterDeclaration;
import dev.bluepitaya.phpmagik.phpsymbol.PhpType;
import dev.bluepitaya.phpmagik.testing.Fixture;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PhpUnionTypeTest {

    @Test
    void typesNullableAndUnionParameters() throws IOException {
        try (Fixture fixture = Fixture.index("php_union_type")) {
            assertEquals(
                    List.of(
                            PhpType.union(List.of(PhpType.Builtin.Array, PhpType.Builtin.Null)),
                            PhpType.union(List.of(PhpType.Builtin.Integer, PhpType.Builtin.String))
                    ),
                    fixture.symbols()
                            .parameterDeclarations()
                            .stream()
                            .map(PhpParameterDeclaration::type)
                            .toList()
            );
        }
    }

    @Test
    void hoversAUsageOfANullableParameterWithNull() throws IOException {
        try (Fixture fixture = Fixture.index("php_union_type")) {
            var handler = new HoverHandler(fixture.workspace(), fixture.finder(), fixture.log());
            String uri = fixture.file("Foo.php").uri();

            assertEquals(
                    "```php\n<?php\narray|null $bars\n```",
                    handler.handle(new TextDocumentPosition(new TextDocumentIdentifier(uri), new Position(6, 16)))
                            .contents()
                            .value()
            );
        }
    }
}
