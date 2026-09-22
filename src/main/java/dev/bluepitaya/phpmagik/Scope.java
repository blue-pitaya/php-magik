package dev.bluepitaya.phpmagik;

import dev.bluepitaya.phpmagik.phpsymbol.PhpClassDefinition;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
record Scope(@Nullable PhpClassDefinition cls, @Nullable String function) {

    Scope inClass(@Nullable PhpClassDefinition declared) {
        return new Scope(declared, function);
    }

    Scope inFunction(@Nullable String declared) {
        return new Scope(cls, declared);
    }
}
