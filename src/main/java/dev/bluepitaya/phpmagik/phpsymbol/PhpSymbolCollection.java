package dev.bluepitaya.phpmagik.phpsymbol;

import dev.bluepitaya.phpmagik.PhpFile;
import dev.bluepitaya.phpmagik.ts.Range;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class PhpSymbolCollection {

    private final List<PhpNamespaceDefinition> nsDefinitions = new ArrayList<>();
    private final List<PhpClassDeclaration> classDeclarations = new ArrayList<>();
    private final List<PhpPropertyDeclaration> propertyDeclarations = new ArrayList<>();
    private final List<PhpMethodDeclaration> methodDeclarations = new ArrayList<>();
    private final List<PhpFunctionDefinition> functionDefinitions = new ArrayList<>();
    private final List<PhpParameterDeclaration> parameterDeclarations = new ArrayList<>();
    private final List<PhpMethodLocalVarDeclaration> localVarDeclarations = new ArrayList<>();
    private final List<PhpMethodVarUsage> varUsages = new ArrayList<>();
    private final List<PhpReference> references = new ArrayList<>();

    public void add(PhpSymbol symbol) {
        switch (symbol) {
            case PhpNamespaceDefinition x -> nsDefinitions.add(x);
            case PhpClassDeclaration x -> classDeclarations.add(x);
            case PhpPropertyDeclaration x -> propertyDeclarations.add(x);
            case PhpMethodDeclaration x -> methodDeclarations.add(x);
            case PhpFunctionDefinition x -> functionDefinitions.add(x);
            case PhpParameterDeclaration x -> parameterDeclarations.add(x);
            case PhpMethodLocalVarDeclaration x -> localVarDeclarations.add(x);
            case PhpMethodVarUsage x -> varUsages.add(x);
            case PhpReference x -> references.add(x);
        }
    }

    public List<List<? extends PhpSymbol>> lists() {
        return List.of(
                nsDefinitions,
                classDeclarations,
                propertyDeclarations,
                methodDeclarations,
                functionDefinitions,
                parameterDeclarations,
                localVarDeclarations,
                varUsages,
                references
        );
    }

    public void addAll(PhpSymbolCollection other) {
        for (List<? extends PhpSymbol> symbols : other.lists()) {
            symbols.forEach(this::add);
        }
    }

    public void removeSymbolsOfFile(PhpFile file) {
        for (List<? extends PhpSymbol> symbols : lists()) {
            symbols.removeIf(symbol -> symbol.file() == file);
        }
    }

    public List<PhpNamespaceDefinition> nsDefinitions() {
        return nsDefinitions;
    }

    public @Nullable PhpNamespaceDefinition namespaceOf(PhpFile file, Range range) {
        for (PhpNamespaceDefinition definition : nsDefinitions) {
            Range scope = definition.scope();
            if (definition.file() == file && scope != null && range.isWithin(scope)) {
                return definition;
            }
        }
        return null;
    }

    public @Nullable PhpClassDeclaration classOf(PhpFile file, Range range) {
        PhpClassDeclaration innermost = null;
        Range innermostScope = null;
        for (PhpClassDeclaration declaration : classDeclarations) {
            Range scope = declaration.scope();
            if (declaration.file() == file && scope != null && range.isWithin(scope)
                    && (innermostScope == null || scope.isWithin(innermostScope))) {
                innermost = declaration;
                innermostScope = scope;
            }
        }
        return innermost;
    }

    public List<PhpClassDeclaration> classDeclarations() {
        return classDeclarations;
    }

    public List<PhpPropertyDeclaration> propertyDeclarations() {
        return propertyDeclarations;
    }

    public List<PhpMethodDeclaration> methodDeclarations() {
        return methodDeclarations;
    }

    public List<PhpFunctionDefinition> functionDefinitions() {
        return functionDefinitions;
    }

    public List<PhpFunctionLike> functionLikes() {
        List<PhpFunctionLike> functionLikes = new ArrayList<>(methodDeclarations);
        functionLikes.addAll(functionDefinitions);
        return functionLikes;
    }

    public List<PhpParameterDeclaration> parameterDeclarations() {
        return parameterDeclarations;
    }

    public List<PhpMethodLocalVarDeclaration> localVarDeclarations() {
        return localVarDeclarations;
    }

    public List<PhpMethodVarUsage> varUsages() {
        return varUsages;
    }

    public List<PhpReference> references() {
        return references;
    }
}
