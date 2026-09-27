package dev.bluepitaya.phpmagik.phpsymbol;

import dev.bluepitaya.phpmagik.PhpFile;
import dev.bluepitaya.phpmagik.phpdoc.PhpDoc;
import dev.bluepitaya.phpmagik.ts.Range;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public sealed interface PhpFunctionLike extends PhpSymbolOwner permits PhpMethodDeclaration, PhpFunctionDefinition {

    PhpFile file();

    @Nullable String name();

    @Nullable Range scope();

    void scope(Range scope);

    String parameters();

    void parameters(String parameters);

    @Nullable String declaredReturnType();

    void declaredReturnType(String declaredReturnType);

    @Nullable PhpDoc doc();

    void doc(PhpDoc doc);

    @Nullable PhpType returnType();

    void returnType(PhpType returnType);
}
