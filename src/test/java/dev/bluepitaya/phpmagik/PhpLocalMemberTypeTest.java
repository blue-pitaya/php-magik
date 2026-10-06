package dev.bluepitaya.phpmagik;

import dev.bluepitaya.phpmagik.lsp.dto.Position;
import dev.bluepitaya.phpmagik.lsp.dto.TextDocumentIdentifier;
import dev.bluepitaya.phpmagik.lsp.dto.TextDocumentPosition;
import dev.bluepitaya.phpmagik.lsp.handler.HoverHandler;
import dev.bluepitaya.phpmagik.phpsymbol.PhpMethodLocalVarDeclaration;
import dev.bluepitaya.phpmagik.phpsymbol.PhpType;
import dev.bluepitaya.phpmagik.testing.Fixture;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PhpLocalMemberTypeTest {

    @Test
    void typesLocalsAssignedFromMembersOfAClassInAnotherFile() throws IOException {
        try (Fixture fixture = Fixture.index("php_local_member_type")) {
            assertEquals(
                    List.of(PhpType.Builtin.Float, PhpType.named("App\\Models\\Bar"), PhpType.Builtin.Float),
                    fixture.symbols()
                            .localVarDeclarations()
                            .stream()
                            .map(PhpMethodLocalVarDeclaration::type)
                            .toList()
            );
        }
    }

    @Test
    void hoversAUsageOfALocalTypedFromAnotherFile() throws IOException {
        try (Fixture fixture = Fixture.index("php_local_member_type")) {
            var handler = new HoverHandler(fixture.workspace(), fixture.finder(), fixture.log());
            String uri = fixture.file("Foo.php").uri();

            assertEquals(
                    "```php\n<?php\nfloat $a\n```",
                    handler.handle(new TextDocumentPosition(new TextDocumentIdentifier(uri), new Position(16, 16)))
                            .contents()
                            .value()
            );
        }
    }
}
