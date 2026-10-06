package dev.bluepitaya.phpmagik.phpsymbol;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public sealed interface PhpReference extends PhpSymbol permits
        PhpClassReference,
        PhpFunctionCall,
        PhpMemberReference,
        PhpStaticCall {

    String name();

    @Nullable PhpSymbol definition();

    @Override
    default @Nullable String hover() {
        PhpSymbol definition = definition();
        return definition == null ? null : definition.hover();
    }
}
