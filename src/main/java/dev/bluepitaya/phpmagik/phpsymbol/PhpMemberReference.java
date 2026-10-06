package dev.bluepitaya.phpmagik.phpsymbol;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public sealed interface PhpMemberReference extends PhpReference permits PhpPropertyAccess, PhpMethodCall {

    @Nullable PhpSymbolOwner owner();

    @Nullable String variable();

    @Nullable PhpMemberReference receiver();
}
