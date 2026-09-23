package dev.bluepitaya.phpmagik.phpsymbol;

import dev.bluepitaya.phpmagik.PhpFile;

import java.util.ArrayList;
import java.util.List;

public final class PhpSymbolCollection {

    private final List<PhpVarDefinition> varDefinitions = new ArrayList<>();
    private final List<PhpVarUsage> varUsages = new ArrayList<>();
    private final List<PhpFunctionDefinition> functions = new ArrayList<>();
    private final List<PhpFunctionUsage> functionUsages = new ArrayList<>();
    private final List<PhpMethodDefinition> methods = new ArrayList<>();
    private final List<PhpMethodUsage> methodUsages = new ArrayList<>();
    private final List<PhpPropertyDefinition> properties = new ArrayList<>();
    private final List<PhpPropertyUsage> propertyUsages = new ArrayList<>();
    private final List<PhpClassDefinition> classes = new ArrayList<>();
    private final List<PhpClassUsage> classUsages = new ArrayList<>();
    private final List<PhpUseStatement> uses = new ArrayList<>();
    private final List<PhpNamespaceDefinition> nsDefinitions = new ArrayList<>();
    private final List<PhpClassDeclaration> classDeclarations = new ArrayList<>();
    private final List<PhpPropertyDeclaration> propertyDeclarations = new ArrayList<>();

    public void add(PhpSymbol symbol) {
        switch (symbol) {
            case PhpVarDefinition x -> varDefinitions.add(x);
            case PhpVarUsage x -> varUsages.add(x);
            case PhpFunctionDefinition x -> functions.add(x);
            case PhpFunctionUsage x -> functionUsages.add(x);
            case PhpMethodDefinition x -> methods.add(x);
            case PhpMethodUsage x -> methodUsages.add(x);
            case PhpPropertyDefinition x -> properties.add(x);
            case PhpPropertyUsage x -> propertyUsages.add(x);
            case PhpClassDefinition x -> classes.add(x);
            case PhpClassUsage x -> classUsages.add(x);
            case PhpUseStatement x -> uses.add(x);
            case PhpNamespaceDefinition x -> nsDefinitions.add(x);
            case PhpClassDeclaration x -> classDeclarations.add(x);
            case PhpPropertyDeclaration x -> propertyDeclarations.add(x);
        }
    }

    public List<List<? extends PhpSymbol>> lists() {
        return List.of(
                varDefinitions,
                varUsages,
                functions,
                functionUsages,
                methods,
                methodUsages,
                properties,
                propertyUsages,
                classes,
                classUsages,
                uses,
                nsDefinitions,
                classDeclarations,
                propertyDeclarations
        );
    }

    public void addAll(PhpSymbolCollection other) {
        for (List<? extends PhpSymbol> symbols : other.lists()) {
            symbols.forEach(this::add);
        }
    }

    public void removeFile(PhpFile file) {
        for (List<? extends PhpSymbol> symbols : lists()) {
            symbols.removeIf(symbol -> symbol.file() == file);
        }
    }

    public List<PhpVarDefinition> varDefinitions() {
        return varDefinitions;
    }

    public List<PhpVarUsage> varUsages() {
        return varUsages;
    }

    public List<PhpFunctionDefinition> functions() {
        return functions;
    }

    public List<PhpFunctionUsage> functionUsages() {
        return functionUsages;
    }

    public List<PhpMethodDefinition> methods() {
        return methods;
    }

    public List<PhpMethodUsage> methodUsages() {
        return methodUsages;
    }

    public List<PhpPropertyDefinition> properties() {
        return properties;
    }

    public List<PhpPropertyUsage> propertyUsages() {
        return propertyUsages;
    }

    public List<PhpClassDefinition> classes() {
        return classes;
    }

    public List<PhpClassUsage> classUsages() {
        return classUsages;
    }

    public List<PhpUseStatement> uses() {
        return uses;
    }

    public List<PhpNamespaceDefinition> nsDefinitions() {
        return nsDefinitions;
    }

    public List<PhpClassDeclaration> classDeclarations() {
        return classDeclarations;
    }

    public List<PhpPropertyDeclaration> propertyDeclarations() {
        return propertyDeclarations;
    }
}
