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

    public void add(PhpVarDefinition symbol) {
        varDefinitions.add(symbol);
    }

    public void add(PhpVarUsage symbol) {
        varUsages.add(symbol);
    }

    public void add(PhpFunctionDefinition symbol) {
        functions.add(symbol);
    }

    public void add(PhpFunctionUsage symbol) {
        functionUsages.add(symbol);
    }

    public void add(PhpMethodDefinition symbol) {
        methods.add(symbol);
    }

    public void add(PhpMethodUsage symbol) {
        methodUsages.add(symbol);
    }

    public void add(PhpPropertyDefinition symbol) {
        properties.add(symbol);
    }

    public void add(PhpPropertyUsage symbol) {
        propertyUsages.add(symbol);
    }

    public void add(PhpClassDefinition symbol) {
        classes.add(symbol);
    }

    public void add(PhpClassUsage symbol) {
        classUsages.add(symbol);
    }

    public void add(PhpUseStatement symbol) {
        uses.add(symbol);
    }

    public void addAll(PhpSymbolCollection other) {
        varDefinitions.addAll(other.varDefinitions);
        varUsages.addAll(other.varUsages);
        functions.addAll(other.functions);
        functionUsages.addAll(other.functionUsages);
        methods.addAll(other.methods);
        methodUsages.addAll(other.methodUsages);
        properties.addAll(other.properties);
        propertyUsages.addAll(other.propertyUsages);
        classes.addAll(other.classes);
        classUsages.addAll(other.classUsages);
        uses.addAll(other.uses);
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

    public List<List<? extends PhpSymbol>> lists() {
        return List.of(varDefinitions, varUsages, functions, functionUsages, methods,
                methodUsages, properties, propertyUsages, classes, classUsages, uses);
    }
}
