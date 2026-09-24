package dev.bluepitaya.phpmagik.phpsymbol;

import dev.bluepitaya.phpmagik.PhpFile;
import dev.bluepitaya.phpmagik.ts.Range;
import org.jspecify.annotations.Nullable;

public sealed interface PhpSymbol permits
        PhpNamespaceDefinition,
        PhpClassDeclaration,
        PhpPropertyDeclaration,
        PhpMethodDeclaration,
        PhpFunctionDefinition,
        PhpParameterDeclaration,
        PhpMethodLocalVarDeclaration,
        PhpMethodVarUsage {

    static String code(String php) {
        return "```php\n" + php + "\n```";
    }

    @Nullable String hover();

    Range range();

    PhpFile file();
}
