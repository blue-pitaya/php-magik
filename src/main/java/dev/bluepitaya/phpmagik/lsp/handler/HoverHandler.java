package dev.bluepitaya.phpmagik.lsp.handler;

import dev.bluepitaya.phpmagik.*;
import dev.bluepitaya.phpmagik.phpsymbol.*;
import dev.bluepitaya.phpmagik.resolver.*;
import dev.bluepitaya.phpmagik.lsp.Logger;
import dev.bluepitaya.phpmagik.lsp.dto.*;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;

@NullMarked
public final class HoverHandler {

    private final Workspace workspace;
    private final SymbolFinder symbols;
    private final TypeInference types;
    private final FunctionResolver functions;
    private final MethodResolver methods;
    private final PropertyResolver properties;
    private final Logger log;

    public HoverHandler(Workspace workspace, SymbolFinder symbols, TypeInference types,
                        FunctionResolver functions, MethodResolver methods,
                        PropertyResolver properties, Logger log) {
        this.workspace = workspace;
        this.symbols = symbols;
        this.types = types;
        this.functions = functions;
        this.methods = methods;
        this.properties = properties;
        this.log = log;
    }

    public @Nullable Hover handle(TextDocumentPosition params) {
        String uri = params.textDocument().uri();
        int line = params.position().line();
        int col = params.position().character();

        log.log("hover: " + uri + " " + line + ":" + col);

        PhpFile file = workspace.findFile(uri);
        if (file == null) {
            throw new AppException("file not found: " + uri);
        }

        PhpSymbol sym = symbols.resolveAt(file, line, col);
        if (sym == null) {
            return null;
        }

        @Nullable String text = switch (sym) {
            case PhpVarDefinition def -> typedHover(def.name(),
                    symbols.resolveType(def.file(), def.ns(), types.typeOf(def)));
            case PhpVarUsage usage -> typedHover(usage.name(),
                    symbols.resolveType(usage.file(), usage.ns(), types.typeOf(usage)));
            case PhpPropertyDefinition property -> propertyHover(property);
            case PhpPropertyUsage usage -> {
                PhpPropertyDefinition declared = properties.definitionOf(usage);
                yield declared != null ? propertyHover(declared) : typedHover(usage.name(), null);
            }
            case PhpMethodDefinition method -> methodHover(method);
            case PhpMethodUsage usage -> {
                PhpMethodDefinition declared = methods.definitionOf(usage);
                yield declared != null
                        ? methodHover(declared)
                        : callableHover(callName(types.classOf(usage), usage.name()),
                        nameOnly(usage.name(), null), null);
            }
            case PhpFunctionDefinition def -> functionHover(def);
            case PhpFunctionUsage usage -> {
                PhpFunctionDefinition declared = functions.definitionOf(usage);
                yield declared != null
                        ? functionHover(declared)
                        : callableHover(qualifiedName(usage.ns(), usage.name()),
                        nameOnly(usage.name(), null), null);
            }
            case PhpUseStatement use -> typedHover(use.alias(), use.fqn());
            case PhpClassDefinition cls -> null;
            case PhpClassUsage usage -> null;
        };
        if (text == null) return null;

        return new Hover(new MarkupContent("markdown", text));
    }

    private String propertyHover(PhpPropertyDefinition property) {
        return typedHover(property.name(), symbols.resolveType(property.file(),
                property.owner().ns(), types.typeOf(property)));
    }

    private static String typedHover(String name, @Nullable String type) {
        return type != null
                ? "```php\n" + name + ": " + type + "\n```"
                : "```php\n" + name + "\n```";
    }

    private String methodHover(PhpMethodDefinition method) {
        return callableHover(method.qualifiedName(),
                declaration(method.signature(), method.name(), method.returnType(),
                        types.returnTypeOf(method)),
                method.doc());
    }

    private static String declaration(@Nullable String signature, String name,
                                      @Nullable String storedReturn,
                                      @Nullable String inferredReturn) {
        if (signature == null) return nameOnly(name, inferredReturn);
        return storedReturn == null && inferredReturn != null
                ? signature + ": " + inferredReturn
                : signature;
    }

    private String functionHover(PhpFunctionDefinition func) {
        return callableHover(func.qualifiedName(),
                declaration(func.signature(), func.name(), func.returnType(),
                        types.returnTypeOf(func)),
                func.doc());
    }

    private String callableHover(String qualifiedName, String signature, @Nullable String doc) {
        var out = new StringBuilder();
        /* markdown would swallow a lone backslash between namespace segments */
        out.append("__").append(qualifiedName.replace("\\", "\\\\")).append("__\n");

        String description = doc == null ? "" : description(doc);
        if (!description.isEmpty()) {
            out.append('\n').append(description).append('\n');
        }

        out.append("\n```php\n<?php\n").append(signature).append(" { }\n```\n");

        if (doc != null) {
            for (String tag : tags(doc)) {
                out.append('\n').append(formatTag(tag)).append('\n');
            }
        }

        return out.toString().strip();
    }

    private static String qualifiedName(@Nullable String ns, String name) {
        return ns == null ? name : ns + "\\" + name;
    }

    private static String callName(@Nullable String cls, String name) {
        return cls == null ? name : cls + "::" + name;
    }

    private static String nameOnly(String name, @Nullable String returnType) {
        return returnType != null
                ? "function " + name + "(): " + returnType
                : "function " + name + "()";
    }

    private static String description(String doc) {
        var out = new StringBuilder();
        for (String line : doc.lines().toList()) {
            if (line.startsWith("@")) break;
            out.append(line).append('\n');
        }
        return out.toString().strip();
    }

    private static List<String> tags(String doc) {
        return doc.lines().filter(line -> line.startsWith("@")).toList();
    }

    private static String formatTag(String tag) {
        int space = tag.indexOf(' ');
        if (space < 0) return "_" + tag + "_";
        return "_" + tag.substring(0, space) + "_ `" + tag.substring(space + 1).strip() + "`";
    }
}
