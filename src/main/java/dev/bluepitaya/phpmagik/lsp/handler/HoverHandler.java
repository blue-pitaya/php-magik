package dev.bluepitaya.phpmagik.lsp.handler;

import dev.bluepitaya.phpmagik.index.*;
import dev.bluepitaya.phpmagik.lsp.Logger;
import dev.bluepitaya.phpmagik.lsp.dto.*;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;

@NullMarked
public final class HoverHandler {

    private final Workspace app;
    private final SymbolFinder symbols;
    private final Logger log;

    public HoverHandler(Workspace app, SymbolFinder symbols, Logger log) {
        this.app = app;
        this.symbols = symbols;
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
        if (sym instanceof PhpVar var) {
            String type = var.type() != null ? var.type() : symbols.latestVarType(var);
            /* $this and any unresolved local have no type to show */
            text = type != null
                    ? "```php\n" + var.name() + ": " + type + "\n```"
                    : "```php\n" + var.name() + "\n```";
        } else if (sym instanceof PhpFunction func) {
            /* the declaration carries the signature, the return type and the doc
             * block, and findFuncDef hands back func itself when it already is
             * one; a call to something unindexed leaves only the name */
            PhpFunction def = symbols.findFuncDef(func);
            PhpFunction shown = def != null ? def : func;
            String signature = def != null ? symbols.signature(def) : null;
            text = functionHover(shown, signature != null ? signature : nameOnly(shown));
        }
        if (text == null) return null;

        return new Hover(new MarkupContent("markdown", text));
    }

    /**
     * Bold qualified name, the doc block's description, the declaration in a php
     * block, then one PHPDoc tag per paragraph.
     */
    private String functionHover(PhpFunction shown, String signature) {
        var out = new StringBuilder();
        /* markdown would swallow a lone backslash between namespace segments */
        out.append("__").append(qualifiedName(shown).replace("\\", "\\\\")).append("__\n");

        String doc = symbols.docComment(shown);
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

    private static String qualifiedName(PhpFunction func) {
        var out = new StringBuilder();
        if (func.ns() != null) {
            out.append(func.ns()).append('\\');
        }
        if (func.className() != null) {
            out.append(func.className()).append("::");
        }
        return out.append(func.name()).toString();
    }

    /** All that is left for a call whose declaration is not in the index. */
    private static String nameOnly(PhpFunction func) {
        return func.returnType() != null
                ? "function " + func.name() + "(): " + func.returnType()
                : "function " + func.name() + "()";
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
