package dev.bluepitaya.phpmagik;

import dev.bluepitaya.phpmagik.phpsymbol.PhpClassDeclaration;
import dev.bluepitaya.phpmagik.phpsymbol.PhpFunctionDefinition;
import dev.bluepitaya.phpmagik.phpsymbol.PhpMethodDeclaration;
import dev.bluepitaya.phpmagik.phpsymbol.PhpMethodLocalVarDeclaration;
import dev.bluepitaya.phpmagik.phpsymbol.PhpMethodVarUsage;
import dev.bluepitaya.phpmagik.phpsymbol.PhpNamespaceDefinition;
import dev.bluepitaya.phpmagik.phpsymbol.PhpParameterDeclaration;
import dev.bluepitaya.phpmagik.phpsymbol.PhpPropertyDeclaration;
import dev.bluepitaya.phpmagik.phpsymbol.PhpSymbol;
import org.jspecify.annotations.NullMarked;

import java.util.List;

@NullMarked
final class SymbolPrinter {

    void print(Workspace workspace) {
        var count = 0;
        for (List<? extends PhpSymbol> symbols : workspace.symbols().lists()) {
            for (PhpSymbol symbol : symbols) {
                System.out.println(line(symbol));
                count++;
            }
        }
        System.out.println(count + " symbol(s) in " + workspace.files().size() + " file(s)");
    }

    private String line(PhpSymbol symbol) {
        return symbol.getClass().getSimpleName()
                + " " + name(symbol)
                + " " + symbol.file().path()
                + " " + range(symbol);
    }

    private String name(PhpSymbol symbol) {
        var name = switch (symbol) {
            case PhpNamespaceDefinition x -> x.name();
            case PhpClassDeclaration x -> x.name();
            case PhpPropertyDeclaration x -> x.name();
            case PhpMethodDeclaration x -> x.name();
            case PhpFunctionDefinition x -> x.name();
            case PhpParameterDeclaration x -> x.name();
            case PhpMethodLocalVarDeclaration x -> x.name();
            case PhpMethodVarUsage x -> x.name();
        };
        return name == null ? "<unnamed>" : name;
    }

    private String range(PhpSymbol symbol) {
        var range = symbol.range();
        return range == null ? "<no range>" : range.toString();
    }
}
