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

    private final Workspace app;
    private final SymbolFinder symbols;
    private final TypeInference types;
    private final FunctionResolver functions;
    private final MethodResolver methods;
    private final PropertyResolver properties;
    private final Logger log;

    public HoverHandler(Workspace app, SymbolFinder symbols, TypeInference types,
                        FunctionResolver functions, MethodResolver methods,
                        PropertyResolver properties, Logger log) {
        this.app = app;
        this.symbols = symbols;
        this.types = types;
        this.functions = functions;
        this.methods = methods;
        this.properties = properties;
        this.log = log;
    }

    public @Nullable Hover handle(TextDocumentPosition params) {
        TextDocumentIdentifier td = params.textDocument();
        Position pos = params.position();
        String uri = td.uri();
        int line = pos.line();
        int col = pos.character();
        log.log("hover: " + uri + " " + line + ":" + col);

        PhpFile file = app.findFile(uri);
        if (file == null) return null;

        PhpSymbol sym = symbols.resolveAt(file, line, col);
        String text = null;
        if (sym instanceof PhpVarDefinition def) {
            text = typedHover(def.name(),
                    symbols.resolveType(def.file(), def.ns(), types.typeOf(def)));
        } else if (sym instanceof PhpVarUsage usage) {
            text = typedHover(usage.name(),
                    symbols.resolveType(usage.file(), usage.ns(), types.typeOf(usage)));
        } else if (sym instanceof PhpPropertyDefinition property) {
            text = propertyHover(property);
        } else if (sym instanceof PhpPropertyUsage usage) {
            /* the access carries no type of its own: it is worth what the
             * declaration it reads says, and just a name without one */
            PhpPropertyDefinition declared = properties.definitionOf(usage);
            text = declared != null ? propertyHover(declared) : typedHover(usage.name(), null);
        } else if (sym instanceof PhpMethodDefinition method) {
            text = methodHover(method);
        } else if (sym instanceof PhpMethodUsage usage) {
            /* the call site carries nothing but a name: everything worth showing
             * - the signature, the return type, the doc block - is on the
             * declaration, and a call to an unindexed method leaves only the name */
            PhpMethodDefinition declared = methods.definitionOf(usage);
            text = declared != null
                    ? methodHover(declared)
                    : callableHover(callName(types.classOf(usage), usage.name()),
                            nameOnly(usage.name(), null), null);
        } else if (sym instanceof PhpFunctionDefinition def) {
            text = functionHover(def);
        } else if (sym instanceof PhpFunctionUsage usage) {
            PhpFunctionDefinition declared = functions.definitionOf(usage);
            text = declared != null
                    ? functionHover(declared)
                    : callableHover(qualifiedName(usage.ns(), usage.name()),
                            nameOnly(usage.name(), null), null);
        }
        if (text == null) return null;

        return new Hover(new MarkupContent("markdown", text));
    }

    /** A type is resolved where it was written, which for a property is its class's file. */
    private String propertyHover(PhpPropertyDefinition property) {
        return typedHover(property.name(), symbols.resolveType(property.file(),
                property.owner().ns(), types.typeOf(property)));
    }

    /**
     * {@code $name: type}, or just the name when nothing types it - {@code $this}
     * and any unresolved local.
     */
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

    /**
     * The declaration as the indexer sliced it out, with a return type appended
     * when the source declared none and the indexer could not name one either -
     * which is the whole point of inferring it from what the body returns. It
     * appends at most once: a stored return type is already in the slice.
     */
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

    /**
     * Bold qualified name, the doc block's description, the declaration in a php
     * block, then one PHPDoc tag per paragraph.
     */
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

    /** {@code Cls::foo} when the call's object resolves to a class, else the bare name. */
    private static String callName(@Nullable String cls, String name) {
        return cls == null ? name : cls + "::" + name;
    }

    /** All that is left for a call whose declaration is not in the index. */
    private static String nameOnly(String name, @Nullable String returnType) {
        return returnType != null
                ? "function " + name + "(): " + returnType
                : "function " + name + "()";
    }

    /** The doc block down to its first tag line. */
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

    /** {@code @param int $a} becomes {@code _@param_ `int $a`}. */
    private static String formatTag(String tag) {
        int space = tag.indexOf(' ');
        if (space < 0) return "_" + tag + "_";
        return "_" + tag.substring(0, space) + "_ `" + tag.substring(space + 1).strip() + "`";
    }
}
