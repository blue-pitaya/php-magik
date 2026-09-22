package dev.bluepitaya.phpmagik;

import dev.bluepitaya.phpmagik.phpsymbol.ClassKind;
import dev.bluepitaya.phpmagik.phpsymbol.PhpClassDefinition;
import dev.bluepitaya.phpmagik.testing.Fixture;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class Test42IndexTest {

    @Test
    void indexesClassesAndTypesAcrossFiles() throws IOException {
        try (Fixture fixture = Fixture.index("test_42")) {
            List<String> classes = fixture.symbols()
                    .classes()
                    .stream()
                    .map(PhpClassDefinition::name)
                    .sorted()
                    .toList();
            assertEquals(List.of("Container", "Engine"), classes);

            PhpClassDefinition container = fixture.symbols()
                    .classes()
                    .stream()
                    .filter(declared -> declared.name().equals("Container"))
                    .findFirst()
                    .orElseThrow();
            assertEquals(ClassKind.CLASS, container.kind());
            assertEquals("Container", container.fqn());
            assertSame(fixture.file("Container.php"), container.file());
        }
    }
}
