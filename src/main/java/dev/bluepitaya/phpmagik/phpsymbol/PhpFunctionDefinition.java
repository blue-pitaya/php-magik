package dev.bluepitaya.phpmagik.phpsymbol;

import dev.bluepitaya.phpmagik.PhpFile;
import dev.bluepitaya.phpmagik.ts.Range;

public final class PhpFunctionDefinition implements PhpSymbol {

    private final String name;
    private final String ns;
    private final String returnType;
    private final Range returnSource;
    private final String signature;
    private final String doc;
    private final Range range;
    private final Range scope;
    private final PhpFile file;

    public PhpFunctionDefinition(
            String name,
            String ns,
            String returnType,
            Range returnSource,
            String signature,
            String doc,
            Range range,
            Range scope,
            PhpFile file) {
        this.name = name;
        this.ns = ns;
        this.returnType = returnType;
        this.returnSource = returnSource;
        this.signature = signature;
        this.doc = doc;
        this.range = range;
        this.scope = scope;
        this.file = file;
    }

    public String qualifiedName() {
        return ns == null ? name : ns + "\\" + name;
    }

    public String name() {
        return name;
    }

    public String ns() {
        return ns;
    }

    public String returnType() {
        return returnType;
    }

    public Range returnSource() {
        return returnSource;
    }

    public String signature() {
        return signature;
    }

    public String doc() {
        return doc;
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
