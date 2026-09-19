package dev.bluepitaya.phpmagik.lsp;

import dev.bluepitaya.phpmagik.index.FuncKind;
import dev.bluepitaya.phpmagik.index.PhpFile;
import dev.bluepitaya.phpmagik.index.PhpFunction;
import dev.bluepitaya.phpmagik.index.PhpVar;
import dev.bluepitaya.phpmagik.index.VarKind;
import dev.bluepitaya.phpmagik.index.Workspace;
import dev.bluepitaya.phpmagik.json.Json;
import dev.bluepitaya.phpmagik.ts.Node;
import dev.bluepitaya.phpmagik.ts.Nodes;
import dev.bluepitaya.phpmagik.ts.Point;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** Speaks LSP over stdio, answering from the {@link Workspace} index. */
public final class LspServer {

    private static final int SK_NAMESPACE = 3;
    private static final int SK_CLASS = 5;
    private static final int SK_METHOD = 6;
    private static final int SK_PROPERTY = 7;
    private static final int SK_CONSTRUCTOR = 9;
    private static final int SK_ENUM = 10;
    private static final int SK_INTERFACE = 11;
    private static final int SK_FUNCTION = 12;
    private static final int SK_ENUM_MEMBER = 22;

    private static final int CIK_METHOD = 2;
    private static final int CIK_FIELD = 5;
    private static final int CIK_VARIABLE = 6;

    private final Workspace app;
    private final InputStream in;
    private final OutputStream out;

    private Writer log;

    public LspServer(Workspace app) {
        this(app, System.in, System.out);
    }

    LspServer(Workspace app, InputStream in, OutputStream out) {
        this.app = app;
        this.in = new BufferedInputStream(in);
        this.out = out;
    }

    // -----------------------------------------------------------------------
    // Index lookups
    // -----------------------------------------------------------------------

    /** Whichever indexed var or function usage/declaration sits exactly at the position. */
    private Resolved resolveAt(PhpFile file, int line, int character) {
        Point pt = new Point(line, character);
        Node root = file.tree().getRootNode();
        /* named variant: variable_name is ["$" (anonymous), name (named)], so
         * the plain descendant lookup would land on the bare "$" leaf when
         * hovering exactly on it instead of variable_name itself */
        Node node = root.getNamedDescendant(pt, pt);
        if (node == null) return Resolved.NONE;

        String t = node.getType();
        Point p = node.getStartPoint();
        String text = node.getContent();

        if (t.equals("variable_name")) {
            return new Resolved(findVarAt(file.fileId(), p, text), null);
        }
        if (t.equals("name") || t.equals("qualified_name")) {
            PhpFunction func = findFuncAt(file.fileId(), p, text);
            if (func != null) return new Resolved(null, func);
            return new Resolved(findVarAt(file.fileId(), p, "$" + text), null);
        }
        return Resolved.NONE;
    }

    private PhpVar findVarAt(int fileId, Point p, String name) {
        for (PhpVar v : app.vars()) {
            if (v.fileId() == fileId && v.line() == p.getRow() && v.col() == p.getColumn()
                    && v.name().equals(name)) {
                return v;
            }
        }
        return null;
    }

    private PhpFunction findFuncAt(int fileId, Point p, String name) {
        for (PhpFunction f : app.funcs()) {
            if (f.fileId() == fileId && f.line() == p.getRow() && f.col() == p.getColumn()
                    && f.name().equals(name)) {
                return f;
            }
        }
        return null;
    }

    /**
     * Definition target for a function/method usage: the matching declaration,
     * same class for methods, no class for plain function calls.
     */
    private PhpFunction findFuncDef(PhpFunction call) {
        if (call.kind() == FuncKind.DEF) return call;
        for (PhpFunction f : app.funcs()) {
            if (f.kind() != FuncKind.DEF || !f.name().equals(call.name())) continue;
            if (call.kind() == FuncKind.METHOD) {
                if (f.className() == null || call.className() == null
                        || !f.className().equals(call.className())) {
                    continue;
                }
            } else if (f.className() != null) {
                continue;
            }
            return f;
        }
        return null;
    }

    /**
     * Definition target for a variable usage: the class property for
     * {@code $this}/{@code $obj} access, else the earliest occurrence in the
     * same scope.
     */
    private PhpVar findVarDef(PhpVar use) {
        if (use.kind() == VarKind.PROPERTY) return use;
        if (use.kind() == VarKind.OBJ || use.kind() == VarKind.THIS) {
            for (PhpVar v : app.vars()) {
                if (v.kind() == VarKind.PROPERTY && v.className() != null
                        && use.className() != null
                        && v.className().equals(use.className())
                        && v.name().equals(use.name())) {
                    return v;
                }
            }
            return null;
        }

        PhpVar best = null;
        for (PhpVar v : app.vars()) {
            if (v.fileId() != use.fileId() || !v.name().equals(use.name())) continue;
            if (!Objects.equals(v.functionName(), use.functionName())) continue;
            if (best == null || v.line() < best.line()
                    || (v.line() == best.line() && v.col() < best.col())) {
                best = v;
            }
        }
        return best;
    }

    /**
     * Most recent known type of variable {@code name}, at or before
     * {@code before}, in the same file and function scope. Mirrors the
     * indexer's scope lookup, but at query time over the whole index rather
     * than during a single parse pass.
     */
    private String typeOfVarBefore(int fileId, String functionName, String name, Point before) {
        PhpVar best = null;
        for (PhpVar v : app.vars()) {
            if (v.fileId() != fileId || v.type() == null || !v.name().equals(name)) continue;
            if (!Objects.equals(v.functionName(), functionName)) continue;
            if (v.line() > before.getRow()
                    || (v.line() == before.getRow() && v.col() > before.getColumn())) {
                continue;
            }
            if (best == null || v.line() > best.line()
                    || (v.line() == best.line() && v.col() > best.col())) {
                best = v;
            }
        }
        return best == null ? null : best.type();
    }

    private String latestVarType(PhpVar at) {
        return typeOfVarBefore(at.fileId(), at.functionName(), at.name(),
                new Point(at.line(), at.col()));
    }

    /** {@code ?Foo}, {@code \Foo} become {@code Foo}. */
    private static String stripNs(String t) {
        if (t == null) return null;
        int i = 0;
        while (i < t.length() && (t.charAt(i) == '?' || t.charAt(i) == '\\')) {
            i++;
        }
        return t.substring(i);
    }

    private static String enclosingFunctionName(Node node) {
        while (node != null) {
            String t = node.getType();
            if (t.equals("function_definition") || t.equals("method_declaration")) {
                return Nodes.text(node.getChildByFieldName("name"));
            }
            node = node.getParent();
        }
        return null;
    }

    private static String enclosingClassName(Node node) {
        while (node != null) {
            String t = node.getType();
            if (t.equals("class_declaration") || t.equals("interface_declaration")
                    || t.equals("trait_declaration") || t.equals("enum_declaration")) {
                return Nodes.text(node.getChildByFieldName("name"));
            }
            node = node.getParent();
        }
        return null;
    }

    // -----------------------------------------------------------------------
    // Source-text helpers
    // -----------------------------------------------------------------------

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
    private static String varBeforeArrow(byte[] content, int off) {
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

    private static int byteLength(String s) {
        return s.getBytes(StandardCharsets.UTF_8).length;
    }

    // -----------------------------------------------------------------------
    // JSON shapes
    // -----------------------------------------------------------------------

    private static Json locationJson(String uri, int line, int col, int len) {
        Json loc = Json.object();
        loc.put("uri", uri);
        Json range = Json.object();
        range.put("start", Json.object().put("line", line).put("character", col));
        range.put("end", Json.object().put("line", line).put("character", col + len));
        loc.put("range", range);
        return loc;
    }

    /**
     * A bare {@code {start,end}} range spanning {@code node}, for
     * documentSymbol. Unlike {@link #locationJson} this reads a live tree node,
     * so it gets the whole span - a function's entire body, say - rather than
     * just a name's length.
     */
    private static Json rangeJson(Node node) {
        Point s = node.getStartPoint();
        Point e = node.getEndPoint();
        Json range = Json.object();
        range.put("start", Json.object().put("line", s.getRow()).put("character", s.getColumn()));
        range.put("end", Json.object().put("line", e.getRow()).put("character", e.getColumn()));
        return range;
    }

    /**
     * Every func-index entry that {@link #findFuncDef} would resolve to
     * {@code def}. {@code def} itself is included only if {@code includeDecl},
     * matched by identity since names repeat across unrelated classes.
     */
    private void collectFuncRefs(PhpFunction def, boolean includeDecl, Json locs) {
        for (PhpFunction f : app.funcs()) {
            boolean match;
            if (f == def) {
                match = includeDecl;
            } else if (f.kind() == FuncKind.DEF || !f.name().equals(def.name())) {
                match = false;
            } else if (def.className() != null) {
                match = f.className() != null && f.className().equals(def.className());
            } else {
                match = f.className() == null;
            }

            if (!match) continue;
            PhpFile file = app.file(f.fileId());
            locs.add(locationJson(file.uri(), f.line(), f.col(), byteLength(f.name())));
        }
    }

    /** Every var-index entry that {@link #findVarDef} would resolve to {@code def}. */
    private void collectVarRefs(PhpVar def, boolean includeDecl, Json locs) {
        for (PhpVar v : app.vars()) {
            boolean match;
            if (v == def) {
                match = includeDecl;
            } else if (!v.name().equals(def.name())) {
                match = false;
            } else if (def.kind() == VarKind.PROPERTY) {
                match = (v.kind() == VarKind.THIS || v.kind() == VarKind.OBJ)
                        && v.className() != null && def.className() != null
                        && v.className().equals(def.className());
            } else {
                match = v.fileId() == def.fileId()
                        && Objects.equals(v.functionName(), def.functionName());
            }

            if (!match) continue;
            /* $this->prop / $obj->prop store a synthetic "$"-prefixed name (to
             * match against property declarations), but the source text at this
             * position is just the bare property name - there is no literal "$" */
            int len = byteLength(v.name());
            if (v.kind() == VarKind.THIS || v.kind() == VarKind.OBJ) {
                len -= 1;
            }
            PhpFile file = app.file(v.fileId());
            locs.add(locationJson(file.uri(), v.line(), v.col(), len));
        }
    }

    // -----------------------------------------------------------------------
    // Request handlers
    // -----------------------------------------------------------------------

    private static Json handleInitialize() {
        Json result = Json.object();
        Json caps = Json.object();
        caps.put("textDocumentSync", 1);
        caps.put("hoverProvider", true);
        caps.put("definitionProvider", true);
        caps.put("referencesProvider", true);
        caps.put("documentSymbolProvider", true);
        caps.put("workspaceSymbolProvider", true);

        Json completion = Json.object();
        Json triggers = Json.array();
        triggers.add(">");
        triggers.add("$");
        triggers.add(":");
        completion.put("triggerCharacters", triggers);
        caps.put("completionProvider", completion);

        result.put("capabilities", caps);
        return result;
    }

    private void handleDidOpen(Json params) {
        Json td = params.get("textDocument");
        String uri = td == null ? null : td.getString("uri");
        String text = td == null ? null : td.getString("text");
        if (uri == null || text == null) return;
        byte[] content = text.getBytes(StandardCharsets.UTF_8);
        log("didOpen: " + uri + " (" + content.length + " bytes)\n");

        if (!app.reparseFile(uri, content)) {
            log("didOpen: " + uri + " not in index, skipping reparse\n");
        }
    }

    private void handleDidChange(Json params) {
        Json td = params.get("textDocument");
        String uri = td == null ? null : td.getString("uri");
        Json changes = params.get("contentChanges");
        Json change = changes == null ? null : changes.at(0);
        String text = change == null ? null : change.getString("text");
        if (uri == null || text == null) return;
        byte[] content = text.getBytes(StandardCharsets.UTF_8);
        log("didChange: " + uri + " (" + content.length + " bytes)\n");

        /* textDocumentSync is Full (1): `text` is always the whole document */
        if (!app.reparseFile(uri, content)) {
            log("didChange: " + uri + " not in index, skipping reparse\n");
        }
    }

    private void handleDidClose(Json params) {
        Json td = params.get("textDocument");
        log("didClose: " + (td == null ? null : td.getString("uri")) + "\n");
    }

    private Json handleHover(Json params) {
        Json td = params.get("textDocument");
        Json pos = params.get("position");
        if (td == null || pos == null) return Json.nullValue();
        String uri = td.getString("uri");
        int line = pos.getInt("line");
        int col = pos.getInt("character");
        log("hover: " + uri + " " + line + ":" + col + "\n");

        PhpFile file = app.findFile(uri);
        if (file == null) return Json.nullValue();

        Resolved r = resolveAt(file, line, col);
        String text = null;
        if (r.var() != null) {
            PhpVar var = r.var();
            String type = var.type() != null ? var.type() : latestVarType(var);
            text = type != null
                    ? "```php\n" + var.name() + ": " + type + "\n```"
                    : "```php\n" + var.name() + "\n```";
        } else if (r.func() != null) {
            PhpFunction func = r.func();
            String ret = func.returnType();
            if (ret == null && func.kind() != FuncKind.DEF) {
                PhpFunction def = findFuncDef(func);
                if (def != null) ret = def.returnType();
            }
            text = ret != null
                    ? "```php\nfunction " + func.name() + "(): " + ret + "\n```"
                    : "```php\nfunction " + func.name() + "()\n```";
        }
        if (text == null) return Json.nullValue();

        Json result = Json.object();
        Json contents = Json.object();
        contents.put("kind", "markdown");
        contents.put("value", text);
        result.put("contents", contents);
        return result;
    }

    private Json handleDefinition(Json params) {
        Json td = params.get("textDocument");
        Json pos = params.get("position");
        if (td == null || pos == null) return Json.nullValue();
        String uri = td.getString("uri");
        int line = pos.getInt("line");
        int col = pos.getInt("character");
        log("definition: " + uri + " " + line + ":" + col + "\n");

        PhpFile file = app.findFile(uri);
        if (file == null) return Json.nullValue();

        Resolved r = resolveAt(file, line, col);

        if (r.func() != null) {
            PhpFunction def = findFuncDef(r.func());
            if (def != null) {
                PhpFile target = app.file(def.fileId());
                return locationJson(target.uri(), def.line(), def.col(),
                        byteLength(def.name()));
            }
        } else if (r.var() != null) {
            PhpVar def = findVarDef(r.var());
            if (def != null) {
                PhpFile target = app.file(def.fileId());
                return locationJson(target.uri(), def.line(), def.col(),
                        byteLength(def.name()));
            }
        }

        return Json.nullValue();
    }

    private Json handleReferences(Json params) {
        Json td = params.get("textDocument");
        Json pos = params.get("position");
        Json rctx = params.get("context");
        Json locs = Json.array();
        if (td == null || pos == null) return locs;
        String uri = td.getString("uri");
        int line = pos.getInt("line");
        int col = pos.getInt("character");
        boolean includeDecl = rctx != null && rctx.getBool("includeDeclaration");
        log("references: " + uri + " " + line + ":" + col + "\n");

        PhpFile file = app.findFile(uri);
        if (file == null) return locs;

        Resolved r = resolveAt(file, line, col);

        if (r.func() != null) {
            PhpFunction def = findFuncDef(r.func());
            if (def != null) collectFuncRefs(def, includeDecl, locs);
        } else if (r.var() != null) {
            PhpVar def = findVarDef(r.var());
            if (def != null) collectVarRefs(def, includeDecl, locs);
        }

        return locs;
    }

    // -----------------------------------------------------------------------
    // documentSymbol
    // -----------------------------------------------------------------------

    /**
     * Builds a DocumentSymbol for {@code nameNode} (its own name becomes the
     * selectionRange) spanning {@code whole} (its full declaration becomes the
     * range), appends it to {@code parentChildren}, and returns it so the
     * caller can add a "children" array of its own.
     */
    private static Json symbolNew(Json parentChildren, String name, int kind, Node whole,
            Node nameNode) {
        Json sym = Json.object();
        sym.put("name", name);
        sym.put("kind", kind);
        sym.put("range", rangeJson(whole));
        sym.put("selectionRange", rangeJson(nameNode));
        parentChildren.add(sym);
        return sym;
    }

    /** A class/interface/trait/enum body: its properties and methods. */
    private static void collectClassMembers(Node list, Json children) {
        int count = list.getNamedChildCount();
        for (int i = 0; i < count; i++) {
            Node node = list.getNamedChild(i);
            String type = node.getType();

            if (type.equals("method_declaration")) {
                Node name = node.getChildByFieldName("name");
                if (name == null) continue;
                String text = name.getContent();
                int kind = text.equals("__construct") ? SK_CONSTRUCTOR : SK_METHOD;
                symbolNew(children, text, kind, node, name);
            } else if (type.equals("property_declaration")) {
                /* one `public int $a, $b;` declares multiple */
                int pc = node.getNamedChildCount();
                for (int j = 0; j < pc; j++) {
                    Node el = node.getNamedChild(j);
                    if (!el.getType().equals("property_element")) continue;
                    Node varName = el.getChildByFieldName("name");
                    if (varName == null) continue;
                    symbolNew(children, varName.getContent(), SK_PROPERTY, node, varName);
                }
            } else if (type.equals("enum_case")) {
                Node name = node.getChildByFieldName("name");
                if (name == null) continue;
                symbolNew(children, name.getContent(), SK_ENUM_MEMBER, node, name);
            }
        }
    }

    private static void collectClassSymbol(Node node, Json out, int kind) {
        Node name = node.getChildByFieldName("name");
        if (name == null) return;
        Json sym = symbolNew(out, name.getContent(), kind, node, name);

        Json children = Json.array();
        sym.put("children", children);
        int count = node.getNamedChildCount();
        for (int i = 0; i < count; i++) {
            Node c = node.getNamedChild(i);
            String t = c.getType();
            if (t.equals("declaration_list") || t.equals("enum_declaration_list")) {
                collectClassMembers(c, children);
            }
        }
    }

    /**
     * Top-level (or namespace-body) symbols: functions, classes and friends,
     * and braced namespaces, recursed into. The unbraced {@code namespace X;}
     * form has no body to nest, so its members stay ordinary top-level symbols.
     */
    private static void collectDocumentSymbols(Node root, Json out) {
        int count = root.getNamedChildCount();
        for (int i = 0; i < count; i++) {
            Node node = root.getNamedChild(i);
            String t = node.getType();

            if (t.equals("function_definition")) {
                Node name = node.getChildByFieldName("name");
                if (name == null) continue;
                symbolNew(out, name.getContent(), SK_FUNCTION, node, name);
            } else if (t.equals("class_declaration") || t.equals("trait_declaration")) {
                collectClassSymbol(node, out, SK_CLASS);
            } else if (t.equals("interface_declaration")) {
                collectClassSymbol(node, out, SK_INTERFACE);
            } else if (t.equals("enum_declaration")) {
                collectClassSymbol(node, out, SK_ENUM);
            } else if (t.equals("namespace_definition")) {
                Node body = node.getChildByFieldName("body");
                Node nsName = node.getChildByFieldName("name");
                if (body != null && nsName != null) {
                    Json sym = symbolNew(out, nsName.getContent(), SK_NAMESPACE, node, nsName);
                    Json children = Json.array();
                    sym.put("children", children);
                    collectDocumentSymbols(body, children);
                }
            }
        }
    }

    private Json handleDocumentSymbol(Json params) {
        Json td = params.get("textDocument");
        Json symbols = Json.array();
        if (td == null) return symbols;
        String uri = td.getString("uri");
        log("documentSymbol: " + uri + "\n");

        PhpFile file = app.findFile(uri);
        if (file == null) return symbols;

        collectDocumentSymbols(file.tree().getRootNode(), symbols);
        return symbols;
    }

    // -----------------------------------------------------------------------
    // workspace/symbol
    // -----------------------------------------------------------------------

    private static boolean matchesQuery(String name, String query) {
        if (query.isEmpty()) return true;
        return name.toLowerCase().contains(query.toLowerCase());
    }

    /**
     * A flat SymbolInformation: unlike DocumentSymbol it needs a uri, since
     * results span every indexed file, and has one location rather than a
     * range plus a selectionRange.
     */
    private static Json symbolInfoJson(String name, int kind, String uri, Node nameNode,
            String container) {
        Json sym = Json.object();
        sym.put("name", name);
        sym.put("kind", kind);
        Point p = nameNode.getStartPoint();
        int len = nameNode.getEndByte() - nameNode.getStartByte();
        sym.put("location", locationJson(uri, p.getRow(), p.getColumn(), len));
        if (container != null) sym.put("containerName", container);
        return sym;
    }

    /**
     * Flat counterpart to {@link #collectClassMembers}: same shape, but
     * filtered by {@code query} and emitting SymbolInformation with
     * {@code container} set to the class name.
     */
    private static void collectClassMembersFlat(Node list, String uri, String query,
            String container, Json out) {
        int count = list.getNamedChildCount();
        for (int i = 0; i < count; i++) {
            Node node = list.getNamedChild(i);
            String type = node.getType();

            if (type.equals("method_declaration")) {
                Node name = node.getChildByFieldName("name");
                if (name == null) continue;
                String text = name.getContent();
                if (matchesQuery(text, query)) {
                    int kind = text.equals("__construct") ? SK_CONSTRUCTOR : SK_METHOD;
                    out.add(symbolInfoJson(text, kind, uri, name, container));
                }
            } else if (type.equals("property_declaration")) {
                int pc = node.getNamedChildCount();
                for (int j = 0; j < pc; j++) {
                    Node el = node.getNamedChild(j);
                    if (!el.getType().equals("property_element")) continue;
                    Node varName = el.getChildByFieldName("name");
                    if (varName == null) continue;
                    String text = varName.getContent();
                    if (matchesQuery(text, query)) {
                        out.add(symbolInfoJson(text, SK_PROPERTY, uri, varName, container));
                    }
                }
            } else if (type.equals("enum_case")) {
                Node name = node.getChildByFieldName("name");
                if (name == null) continue;
                String text = name.getContent();
                if (matchesQuery(text, query)) {
                    out.add(symbolInfoJson(text, SK_ENUM_MEMBER, uri, name, container));
                }
            }
        }
    }

    /**
     * Flat counterpart to {@link #collectDocumentSymbols}: called once per
     * indexed file, emitting matching symbols into one array spanning the
     * whole workspace.
     */
    private static void collectWorkspaceSymbols(Node root, String uri, String query, Json out) {
        int count = root.getNamedChildCount();
        for (int i = 0; i < count; i++) {
            Node node = root.getNamedChild(i);
            String t = node.getType();

            if (t.equals("function_definition")) {
                Node name = node.getChildByFieldName("name");
                if (name == null) continue;
                String text = name.getContent();
                if (matchesQuery(text, query)) {
                    out.add(symbolInfoJson(text, SK_FUNCTION, uri, name, null));
                }
            } else if (t.equals("class_declaration") || t.equals("trait_declaration")
                    || t.equals("interface_declaration") || t.equals("enum_declaration")) {
                int kind = t.equals("interface_declaration") ? SK_INTERFACE
                        : t.equals("enum_declaration") ? SK_ENUM : SK_CLASS;
                Node name = node.getChildByFieldName("name");
                if (name == null) continue;
                String text = name.getContent();
                if (matchesQuery(text, query)) {
                    out.add(symbolInfoJson(text, kind, uri, name, null));
                }
                int cc = node.getNamedChildCount();
                for (int j = 0; j < cc; j++) {
                    Node c = node.getNamedChild(j);
                    String ct = c.getType();
                    if (ct.equals("declaration_list") || ct.equals("enum_declaration_list")) {
                        collectClassMembersFlat(c, uri, query, text, out);
                    }
                }
            } else if (t.equals("namespace_definition")) {
                Node body = node.getChildByFieldName("body");
                if (body != null) {
                    collectWorkspaceSymbols(body, uri, query, out);
                }
            }
        }
    }

    private Json handleWorkspaceSymbol(Json params) {
        String query = params.getString("query");
        if (query == null) query = "";
        log("workspace/symbol: " + query + "\n");

        Json out = Json.array();
        for (PhpFile file : app.files()) {
            collectWorkspaceSymbols(file.tree().getRootNode(), file.uri(), query, out);
        }
        return out;
    }

    // -----------------------------------------------------------------------
    // completion
    // -----------------------------------------------------------------------

    private static Json completionItem(String label, int kind, String detail) {
        Json item = Json.object();
        item.put("label", label);
        item.put("kind", kind);
        if (detail != null) item.put("detail", detail);
        return item;
    }

    /**
     * {@code $obj->}: properties and methods of obj's resolved class, from
     * anywhere in the workspace, since a class can live in a different file
     * than the usage.
     */
    private void addMemberCompletions(Json items, String cls) {
        for (PhpVar v : app.vars()) {
            if (v.kind() == VarKind.PROPERTY && v.className() != null
                    && v.className().equals(cls)) {
                /* v.name() is "$prop" (real, from the declaration); strip the
                 * "$" since nothing is typed after "->" */
                String label = v.name().startsWith("$") ? v.name().substring(1) : v.name();
                items.add(completionItem(label, CIK_FIELD, v.type()));
            }
        }
        for (PhpFunction f : app.funcs()) {
            if (f.kind() == FuncKind.DEF && f.className() != null
                    && f.className().equals(cls)) {
                items.add(completionItem(f.name(), CIK_METHOD, f.returnType()));
            }
        }
    }

    /**
     * Every variable name in scope (same file plus enclosing function), each
     * with whatever type is known for it anywhere in that scope, deduped by
     * name.
     */
    private void addVarCompletions(Json items, int fileId, String functionName) {
        Set<String> seen = new LinkedHashSet<>();
        for (PhpVar v : app.vars()) {
            if (v.fileId() != fileId) continue;
            if (!Objects.equals(v.functionName(), functionName)) continue;
            if (!seen.add(v.name())) continue;

            String type = v.type();
            if (type == null) {
                type = typeOfVarBefore(fileId, functionName, v.name(),
                        new Point(Integer.MAX_VALUE, Integer.MAX_VALUE));
            }
            items.add(completionItem(v.name(), CIK_VARIABLE, type));
        }
    }

    private Json handleCompletion(Json params) {
        Json td = params.get("textDocument");
        Json pos = params.get("position");
        Json items = Json.array();
        if (td == null || pos == null) return items;
        String uri = td.getString("uri");
        int line = pos.getInt("line");
        int col = pos.getInt("character");
        log("completion: " + uri + " " + line + ":" + col + "\n");

        PhpFile file = app.findFile(uri);
        if (file == null) return items;

        byte[] content = file.content();
        int off = byteOffsetFor(content, line, col);

        /* "ClassName::" - static member completion is not supported (no
         * static-member tracking in the index yet); say so with an empty list
         * rather than offering irrelevant local variables */
        if (endsWith(content, off, "::")) return items;

        Point pt = new Point(line, col);
        Node at = file.tree().getRootNode().getNamedDescendant(pt, pt);

        String obj = varBeforeArrow(content, off);
        if (obj != null) {
            String cls;
            if (obj.equals("$this")) {
                cls = enclosingClassName(at);
            } else {
                String fn = enclosingFunctionName(at);
                cls = stripNs(typeOfVarBefore(file.fileId(), fn, obj, pt));
            }
            if (cls != null) addMemberCompletions(items, cls);
            return items;
        }

        String fn = enclosingFunctionName(at);
        String cls = enclosingClassName(at);
        if (cls != null) {
            items.add(completionItem("$this", CIK_VARIABLE, cls));
        }
        addVarCompletions(items, file.fileId(), fn);
        return items;
    }

    // -----------------------------------------------------------------------
    // Transport
    // -----------------------------------------------------------------------

    /** @return the message body, or {@code null} at end of input or on a bad header */
    private byte[] readMessage() throws IOException {
        int contentLength = -1;
        String line;
        while ((line = readHeaderLine()) != null) {
            if (line.isEmpty()) break;
            if (line.startsWith("Content-Length:")) {
                try {
                    contentLength = Integer.parseInt(line.substring(15).trim());
                } catch (NumberFormatException ignored) {
                    contentLength = -1;
                }
            }
        }
        if (line == null || contentLength <= 0) return null;

        byte[] body = in.readNBytes(contentLength);
        return body.length == contentLength ? body : null;
    }

    private String readHeaderLine() throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') break;
            buf.write(c);
        }
        if (c == -1 && buf.size() == 0) return null;
        String line = buf.toString(StandardCharsets.US_ASCII);
        return line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
    }

    private void write(Json doc) throws IOException {
        byte[] body = doc.write().getBytes(StandardCharsets.UTF_8);
        out.write(("Content-Length: " + body.length + "\r\n\r\n")
                .getBytes(StandardCharsets.US_ASCII));
        out.write(body);
        out.flush();
    }

    private void respond(Json id, Json result) throws IOException {
        Json root = Json.object();
        root.put("jsonrpc", "2.0");
        root.put("id", id == null ? Json.nullValue() : id);
        root.put("result", result);
        write(root);
    }

    private void respondMethodNotFound(Json id) throws IOException {
        Json error = Json.object();
        error.put("code", -32601);
        error.put("message", "method not found");
        Json root = Json.object();
        root.put("jsonrpc", "2.0");
        root.put("id", id);
        root.put("error", error);
        write(root);
    }

    private void log(String message) {
        if (log == null) return;
        try {
            log.write(message);
            log.flush();
        } catch (IOException ignored) {
            /* logging must never take the server down */
        }
    }

    // -----------------------------------------------------------------------
    // Main loop
    // -----------------------------------------------------------------------

    public int run() throws IOException {
        try {
            log = Files.newBufferedWriter(Path.of("/tmp/php-magik.log"));
        } catch (IOException cause) {
            return -1;
        }

        boolean running = true;
        boolean shutdown = false;

        try {
            while (running) {
                byte[] message = readMessage();
                if (message == null) break;

                Json request;
                try {
                    request = Json.parse(new String(message, StandardCharsets.UTF_8));
                } catch (IllegalArgumentException malformed) {
                    continue;
                }

                Json id = request.get("id");
                String method = request.getString("method");
                Json params = request.get("params");
                if (params == null) params = Json.object();

                if (method == null) {
                    log("message without method\n");
                    continue;
                }

                switch (method) {
                    case "initialize" -> respond(id, handleInitialize());
                    case "initialized" -> log("initialized\n");
                    case "shutdown" -> {
                        shutdown = true;
                        respond(id, Json.nullValue());
                    }
                    case "exit" -> running = false;
                    case "textDocument/didOpen" -> handleDidOpen(params);
                    case "textDocument/didChange" -> handleDidChange(params);
                    case "textDocument/didClose" -> handleDidClose(params);
                    case "textDocument/hover" -> respond(id, handleHover(params));
                    case "textDocument/definition" -> respond(id, handleDefinition(params));
                    case "textDocument/references" -> respond(id, handleReferences(params));
                    case "textDocument/documentSymbol" ->
                            respond(id, handleDocumentSymbol(params));
                    case "workspace/symbol" -> respond(id, handleWorkspaceSymbol(params));
                    case "textDocument/completion" -> respond(id, handleCompletion(params));
                    default -> {
                        if (id != null) respondMethodNotFound(id);
                    }
                }
            }
        } finally {
            log.close();
        }

        return shutdown ? 0 : 1;
    }

    private record Resolved(PhpVar var, PhpFunction func) {

        static final Resolved NONE = new Resolved(null, null);
    }
}
