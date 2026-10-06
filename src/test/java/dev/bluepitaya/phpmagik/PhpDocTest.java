package dev.bluepitaya.phpmagik;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PhpDocTest {

    @Test
    void parsesLaravelsAppHelper() {
        PhpDoc doc = PhpDoc.parse("""
                /**
                     * Get the available container instance.
                     *
                     * @template TClass of object
                     *
                     * @param  string|class-string<TClass>|null  $abstract
                     * @return ($abstract is class-string<TClass> ? TClass : ($abstract is null ? \\Illuminate\\Foundation\\Application : mixed))
                     */""");

        assertEquals(
                new PhpDoc(
                        "Get the available container instance.",
                        "",
                        List.of(new PhpDoc.Template("TClass", "object")),
                        List.of(new PhpDoc.Param("string|class-string<TClass>|null", "$abstract", "")),
                        new PhpDoc.Return(
                                "($abstract is class-string<TClass> ? TClass"
                                        + " : ($abstract is null ? \\Illuminate\\Foundation\\Application : mixed))",
                                ""
                        ),
                        List.of()
                ),
                doc
        );
    }

    @Test
    void parsesParagraphsMultilineDescriptionsAndUntypedParams() {
        PhpDoc doc = PhpDoc.parse("""
                /**
                 * Sends the invoice.
                 *
                 * Retries twice when the gateway
                 * is down.
                 *
                 * Logs every attempt.
                 *
                 * @param Invoice $invoice The invoice
                 *                         to send.
                 * @param $force
                 * @return bool Whether it was sent.
                 */""");

        assertEquals(
                new PhpDoc(
                        "Sends the invoice.",
                        "Retries twice when the gateway\nis down.\n\nLogs every attempt.",
                        List.of(),
                        List.of(
                                new PhpDoc.Param("Invoice", "$invoice", "The invoice\nto send."),
                                new PhpDoc.Param(null, "$force", "")
                        ),
                        new PhpDoc.Return("bool", "Whether it was sent."),
                        List.of()
                ),
                doc
        );
    }

    @Test
    void keepsSpacesInsideCallableShapeAndUnionTypes() {
        PhpDoc doc = PhpDoc.parse("""
                /**
                 * @param callable(int, string): bool $filter
                 * @param array{id: int, name: string} $row
                 * @param Foo|Bar ...$rest
                 * @return int | string
                 */""");

        assertEquals(
                new PhpDoc(
                        "",
                        "",
                        List.of(),
                        List.of(
                                new PhpDoc.Param("callable(int, string): bool", "$filter", ""),
                                new PhpDoc.Param("array{id: int, name: string}", "$row", ""),
                                new PhpDoc.Param("Foo|Bar", "...$rest", "")
                        ),
                        new PhpDoc.Return("int | string", ""),
                        List.of()
                ),
                doc
        );
    }

    @Test
    void parsesVarTagsWithAndWithoutAName() {
        PhpDoc doc = PhpDoc.parse("""
                /**
                 * @var array<int, Bar>
                 * @var Bar|null $second The fallback.
                 */""");

        assertEquals(
                List.of(
                        new PhpDoc.Var("array<int, Bar>", null, ""),
                        new PhpDoc.Var("Bar|null", "$second", "The fallback.")
                ),
                doc.vars()
        );
    }
}
