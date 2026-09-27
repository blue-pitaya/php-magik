package dev.bluepitaya.phpmagik.phpsymbol;

import dev.bluepitaya.phpmagik.PhpFile;
import dev.bluepitaya.phpmagik.ts.Range;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;

@NullMarked
public final class PhpReference implements PhpSymbol {

    public enum Kind {
        FUNCTION,
        PROPERTY,
        METHOD,
        CLASS
    }

    public record Step(String name, boolean call) {
    }

    private final PhpFile file;
    private final Kind kind;

    private @Nullable PhpSymbolOwner owner;
    private @Nullable String name;
    private @Nullable String receiverVar;
    private List<Step> receiverPath = List.of();
    private @Nullable PhpSymbol definition;
    private @Nullable Range range;

    public PhpReference(PhpFile file, Kind kind) {
        this.file = file;
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    @Override
    public @Nullable String hover() {
        return definition == null ? null : definition.hover();
    }

    public @Nullable PhpSymbolOwner owner() {
        return owner;
    }

    public void owner(PhpSymbolOwner owner) {
        this.owner = owner;
    }

    public @Nullable String name() {
        return name;
    }

    public void name(String name) {
        this.name = name;
    }

    public @Nullable String receiverVar() {
        return receiverVar;
    }

    public void receiverVar(String receiverVar) {
        this.receiverVar = receiverVar;
    }

    public List<Step> receiverPath() {
        return receiverPath;
    }

    public void receiverPath(List<Step> receiverPath) {
        this.receiverPath = receiverPath;
    }

    public @Nullable PhpSymbol definition() {
        return definition;
    }

    public void definition(PhpSymbol definition) {
        this.definition = definition;
    }

    @Override
    public @Nullable Range range() {
        return range;
    }

    public void range(Range range) {
        this.range = range;
    }

    @Override
    public PhpFile file() {
        return file;
    }
}
