package dev.bluepitaya.phpmagik.lsp.handler;

import dev.bluepitaya.phpmagik.*;
import dev.bluepitaya.phpmagik.phpsymbol.*;
import dev.bluepitaya.phpmagik.resolver.*;
import dev.bluepitaya.phpmagik.lsp.Logger;
import dev.bluepitaya.phpmagik.lsp.dto.*;
import dev.bluepitaya.phpmagik.ts.Point;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@NullMarked
public final class CompletionHandler {

    private static final int CIK_METHOD = 2;
    private static final int CIK_FIELD = 5;
    private static final int CIK_VARIABLE = 6;

    private final Workspace app;
    private final SymbolFinder symbols;
    private final VariableResolver variables;
    private final MethodResolver methods;
    private final PropertyResolver properties;
    private final Logger log;

    public CompletionHandler(Workspace app, SymbolFinder symbols, VariableResolver variables,
                             MethodResolver methods, PropertyResolver properties, Logger log) {
        this.app = app;
        this.symbols = symbols;
        this.variables = variables;
        this.methods = methods;
        this.properties = properties;
        this.log = log;
    }

    public List<CompletionItem> handle(TextDocumentPosition params) {
        TextDocumentIdentifier td = params.textDocument();
        Position pos = params.position();
        List<CompletionItem> items = new ArrayList<>();
        String uri = td.uri();
        int line = pos.line();
        int col = pos.character();
        log.log("completion: " + uri + " " + line + ":" + col);

        PhpFile file = app.findFile(uri);
        if (file == null) return items;

        byte[] content = file.content();
        int off = byteOffsetFor(content, line, col);

        /* "ClassName::" - static member completion is not supported (no
         * static-member tracking in the index yet); say so with an empty list
         * rather than offering irrelevant local variables */
        if (endsWith(content, off, "::")) return items;

        var pt = new Point(line, col);
        String fn = symbols.enclosingFunctionName(file.fileId(), pt);
        PhpClass enclosingClass = symbols.enclosingClass(file.fileId(), pt);

        String obj = varBeforeArrow(content, off);
        if (obj != null) {
            String cls;
            if (obj.equals("$this")) {
                cls = enclosingClass == null ? null : enclosingClass.name();
            } else {
                cls = SymbolFinder.stripNs(variables.typeAt(file.fileId(), fn, obj, pt));
            }
            if (cls != null) addMemberCompletions(items, cls);
            return items;
        }

        if (enclosingClass != null) {
            items.add(new CompletionItem("$this", CIK_VARIABLE, enclosingClass.name()));
        }
        addVarCompletions(items, file.fileId(), fn);
        return items;
    }

    /**
     * {@code $obj->}: properties and methods of obj's resolved class, from
     * anywhere in the workspace, since a class can live in a different file
     * than the usage.
     */
    private void addMemberCompletions(List<CompletionItem> items, String cls) {
        for (PhpPropertyDefinition p : properties.declaredIn(cls)) {
            /* p.name() is "$prop", as the declaration spells it; strip the "$"
             * since nothing is typed after "->" */
            String label = p.name().startsWith("$") ? p.name().substring(1) : p.name();
            items.add(new CompletionItem(label, CIK_FIELD, p.type()));
        }
        for (PhpMethodDefinition m : methods.declaredIn(cls)) {
            items.add(new CompletionItem(m.name(), CIK_METHOD, m.returnType()));
        }
    }

    /** Each in-scope variable, with whatever type is known for it in that scope. */
    private void addVarCompletions(List<CompletionItem> items, int fileId, @Nullable String functionName) {
        for (PhpVarDefinition def : variables.inScope(fileId, functionName)) {
            String type = def.type();
            if (type == null) {
                type = variables.typeAnywhere(fileId, functionName, def.name());
            }
            items.add(new CompletionItem(def.name(), CIK_VARIABLE, type));
        }
    }

    private static int byteOffsetFor(byte[] content, int line, int character) {
        int off = 0;
        int curLine = 0;
        while (curLine < line && off < content.length) {
            if (content[off] == '\n') curLine++;
            off++;
        }
        return off + character;
    }

    private static boolean endsWith(byte[] content, int off, String suffix) {
        int len = suffix.length();
        if (off < len || off > content.length) return false;
        for (int i = 0; i < len; i++) {
            if (content[off - len + i] != (byte) suffix.charAt(i)) return false;
        }
        return true;
    }

    /**
     * The {@code $name} immediately before the {@code ->} ending at byte offset
     * {@code off}, or {@code null} if that is not what is there.
     *
     * <p>Text-based on purpose: at completion time the member name after
     * {@code ->} usually is not typed yet, so there is often no well-formed
     * member_access_expression node to read the object out of.
     */
    private static @Nullable String varBeforeArrow(byte[] content, int off) {
        if (!endsWith(content, off, "->")) return null;
        int end = off - 2;
        int start = end;
        while (start > 0 && (isWordByte(content[start - 1]))) {
            start--;
        }
        if (start == 0 || content[start - 1] != '$' || start == end) return null;
        start--;
        return new String(content, start, end - start, StandardCharsets.UTF_8);
    }

    private static boolean isWordByte(byte b) {
        return (b >= 'a' && b <= 'z') || (b >= 'A' && b <= 'Z') || (b >= '0' && b <= '9')
                || b == '_';
    }
}
