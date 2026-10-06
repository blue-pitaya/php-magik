package dev.bluepitaya.phpmagik.phpsymbol;

import dev.bluepitaya.phpmagik.PhpDoc;
import dev.bluepitaya.phpmagik.PhpFile;
import dev.bluepitaya.phpmagik.ts.Range;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class PhpFunctionDefinition implements PhpSymbol, PhpFunctionLike {

    private final PhpFile file;
    private final int depth;

    private @Nullable String name;
    private String parameters = "";
    private @Nullable String declaredReturnType;
    private @Nullable PhpDoc doc;
    private @Nullable PhpType returnType;
    private @Nullable Range range;
    private @Nullable Range scope;

    public PhpFunctionDefinition(PhpFile file, int depth) {
        this.file = file;
        this.depth = depth;
    }

    public int depth() {
        return depth;
    }

    @Override
    public @Nullable String hover() {
        if (name == null) return null;

        return PhpSymbol.code("function " + signature(name));
    }

    public String parameters() {
        return parameters;
    }

    public void parameters(String parameters) {
        this.parameters = parameters;
    }

    public @Nullable String declaredReturnType() {
        return declaredReturnType;
    }

    public void declaredReturnType(String declaredReturnType) {
        this.declaredReturnType = declaredReturnType;
    }

    public @Nullable PhpDoc doc() {
        return doc;
    }

    public void doc(PhpDoc doc) {
        this.doc = doc;
    }

    public @Nullable PhpType returnType() {
        return returnType;
    }

    public void returnType(PhpType returnType) {
        this.returnType = returnType;
    }

    public @Nullable String name() {
        return name;
    }

    public void name(String name) {
        this.name = name;
    }

    @Override
    public @Nullable Range range() {
        return range;
    }

    public void range(Range range) {
        this.range = range;
    }

    public @Nullable Range scope() {
        return scope;
    }

    public void scope(Range scope) {
        this.scope = scope;
    }

    @Override
    public PhpFile file() {
        return file;
    }
}
