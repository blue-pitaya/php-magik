package dev.bluepitaya.phpmagik.phpsymbol;

import dev.bluepitaya.phpmagik.phpdoc.PhpDoc;
import dev.bluepitaya.phpmagik.ts.Range;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public sealed interface PhpFunctionLike extends PhpSymbolOwner permits PhpMethodDeclaration, PhpFunctionDefinition {

    @Nullable String name();

    void scope(Range scope);

    String parameters();

    void parameters(String parameters);

    @Nullable String declaredReturnType();

    void declaredReturnType(String declaredReturnType);

    @Nullable PhpDoc doc();

    void doc(PhpDoc doc);

    @Nullable PhpType returnType();

    void returnType(PhpType returnType);

    default String signature(String name) {
        String signature = name + "(" + parameters() + ")";
        String returns = declaredReturnType();
        PhpType inferred = returnType();
        if (returns == null && inferred != null) {
            returns = inferred.qualified();
        }
        return returns == null ? signature : signature + ": " + returns;
    }
}
