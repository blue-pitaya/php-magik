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
        return innermost(file, range, nsDefinitions, null);
    }

    public @Nullable PhpClassDeclaration classOf(PhpFile file, Range range) {
        return innermost(file, range, classDeclarations, null);
    }

    public @Nullable PhpFunctionLike functionLikeOf(PhpFile file, Range range) {
        return innermost(file, range, functionDefinitions, innermost(file, range, methodDeclarations, null));
    }

    public @Nullable PhpSymbolOwner ownerOf(PhpFile file, Range range) {
        return innermost(file, range, classDeclarations, functionLikeOf(file, range));
    }

    public List<PhpPropertyDeclaration> propertiesIn(PhpFile file, Range range) {
        List<PhpPropertyDeclaration> properties = new ArrayList<>();
        for (PhpPropertyDeclaration property : propertyDeclarations) {
            Range propertyRange = property.range();
            if (property.file() == file && propertyRange != null && propertyRange.isWithin(range)) {
                properties.add(property);
            }
        }
        return properties;
    }

    private static <T extends PhpSymbolOwner> @Nullable T innermost(
            PhpFile file, Range range, List<? extends T> owners, @Nullable T found
    ) {
        T innermost = found;
        Range innermostScope = found == null ? null : found.scope();
        for (T owner : owners) {
            Range scope = owner.scope();
            if (owner.file() == file && scope != null && range.isWithin(scope)
                    && (innermostScope == null || scope.isWithin(innermostScope))) {
                innermost = owner;
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
