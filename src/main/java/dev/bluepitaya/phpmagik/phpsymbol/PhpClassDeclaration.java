package dev.bluepitaya.phpmagik.phpsymbol;

import dev.bluepitaya.phpmagik.PhpFile;
import dev.bluepitaya.phpmagik.ts.Range;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class PhpClassDeclaration implements PhpSymbol, PhpSymbolOwner {

    private final PhpFile file;
    private final String namespace;
    private final String $modifier;
    private final String name;
    private final Range range;
    private final Range scope;

    public PhpClassDeclaration(PhpFile file, String namespace, String $modifier,
                               String name, Range range, Range scope) {
        this.file = file;
        this.namespace = namespace;
        this.$modifier = $modifier;
        this.name = name;
        this.range = range;
        this.scope = scope;
    }

    @Override
    public String hover() {
        String declaration = $modifier.isEmpty() ? "class " + name : $modifier + " class " + name;
        return PhpSymbol.code(namespace.isEmpty() ? declaration : "namespace " + namespace + ";\n" + declaration);
    }

    public String fqn() {
        return namespace.isEmpty() ? name : namespace + "\\" + name;
    }

    public String $modifier() {
        return $modifier;
    }

    public String name() {
        return name;
    }

    @Override
    public Range range() {
        return range;
    }

    public Range scope() {
        return scope;
    }

    @Override
    public PhpFile file() {
        return file;
    }
}
