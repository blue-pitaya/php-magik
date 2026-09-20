package dev.bluepitaya.phpmagik.lsp;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.bluepitaya.phpmagik.index.FuncKind;
import dev.bluepitaya.phpmagik.index.PhpFile;
import dev.bluepitaya.phpmagik.index.PhpFunction;
import dev.bluepitaya.phpmagik.index.PhpVar;
import dev.bluepitaya.phpmagik.index.SymbolFinder;
import dev.bluepitaya.phpmagik.index.SymbolFinder.Resolved;
import dev.bluepitaya.phpmagik.index.VarKind;
import dev.bluepitaya.phpmagik.index.Workspace;
import dev.bluepitaya.phpmagik.lsp.dto.CompletionItem;
import dev.bluepitaya.phpmagik.lsp.dto.ContentChange;
import dev.bluepitaya.phpmagik.lsp.dto.DidChangeParams;
import dev.bluepitaya.phpmagik.lsp.dto.DidOpenParams;
import dev.bluepitaya.phpmagik.lsp.dto.Hover;
import dev.bluepitaya.phpmagik.lsp.dto.MarkupContent;
import dev.bluepitaya.phpmagik.lsp.dto.Position;
import dev.bluepitaya.phpmagik.lsp.dto.ReferenceContext;
import dev.bluepitaya.phpmagik.lsp.dto.ReferenceParams;
import dev.bluepitaya.phpmagik.lsp.dto.TextDocumentIdentifier;
import dev.bluepitaya.phpmagik.lsp.dto.TextDocumentItem;
import dev.bluepitaya.phpmagik.lsp.dto.TextDocumentParams;
import dev.bluepitaya.phpmagik.lsp.dto.TextDocumentPosition;
import dev.bluepitaya.phpmagik.lsp.dto.WorkspaceSymbolParams;
import dev.bluepitaya.phpmagik.ts.Node;
import dev.bluepitaya.phpmagik.ts.Point;
import org.jspecify.annotations.Nullable;
import org.jspecify.annotations.NullMarked;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.NullNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@NullMarked
public final class LspServer {

    private static final ObjectMapper Json = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .changeDefaultPropertyInclusion(incl -> incl.withValueInclusion(JsonInclude.Include.NON_NULL))
            .build();

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
    private final SymbolFinder symbols;
    private final InputStream in;
    private final OutputStream out;

    private final Writer log;

    public LspServer(Workspace app) throws IOException {
        this.app = app;
        this.symbols = new SymbolFinder(app);
        this.in = new BufferedInputStream(System.in);
        this.out = System.out;
        log = Files.newBufferedWriter(Path.of("/tmp/php-magik.log"));
    }

    public void run() throws IOException {
        var running = true;

        try {
            while (running) {
                var message = readMessage();
                if (message == null) {
                    break;
                }

                JsonNode request;
                try {
                    request = Json.readTree(message);
                } catch (JacksonException malformed) {
                    continue;
                }

                var id = request.get("id");
                var method = getStringOrNull(request, "method");
                if (method == null) {
                    log("message without method\n");
                    continue;
                }
                var params = request.get("params");
                if (params == null) {
                    params = Json.createObjectNode();
                }

                try {
                    switch (method) {
                        case "initialize" -> respond(id, handleInitialize());
                        case "initialized" -> log("initialized\n");
                        case "shutdown" -> respond(id, null);
                        case "exit" -> running = false;
                        case "textDocument/didOpen" -> handleDidOpen(Json.treeToValue(params, DidOpenParams.class));
                        case "textDocument/didChange" ->
                                handleDidChange(Json.treeToValue(params, DidChangeParams.class));
                        case "textDocument/didClose" ->
                                handleDidClose(Json.treeToValue(params, TextDocumentParams.class));
                        case "textDocument/hover" ->
                                respond(id, handleHover(Json.treeToValue(params, TextDocumentPosition.class)));
                        case "textDocument/definition" ->
                                respond(id, handleDefinition(Json.treeToValue(params, TextDocumentPosition.class)));
                        case "textDocument/references" ->
                                respond(id, handleReferences(Json.treeToValue(params, ReferenceParams.class)));
                        case "textDocument/documentSymbol" ->
                                respond(id, handleDocumentSymbol(Json.treeToValue(params, TextDocumentParams.class)));
                        case "workspace/symbol" ->
                                respond(id, handleWorkspaceSymbol(Json.treeToValue(params, WorkspaceSymbolParams.class)));
                        case "textDocument/completion" ->
                                respond(id, handleCompletion(Json.treeToValue(params, TextDocumentPosition.class)));
                        default -> {
                            if (id != null) respondMethodNotFound(id);
                        }
                    }
                } catch (JacksonException badParams) {
                    log("unbindable params for " + method + ": " + badParams.getMessage() + "\n");
                }
            }
        } finally {
            log.close();
        }
    }

    private @Nullable String getStringOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isString() ? value.stringValue() : null;
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

    private static int byteLength(String s) {
        return s.getBytes(StandardCharsets.UTF_8).length;
    }

    // -----------------------------------------------------------------------
    // JSON shapes
    // -----------------------------------------------------------------------

    private static ObjectNode locationJson(String uri, int line, int col, int len) {
        ObjectNode loc = Json.createObjectNode();
        loc.put("uri", uri);
        ObjectNode range = Json.createObjectNode();
        range.set("start", Json.createObjectNode().put("line", line).put("character", col));
        range.set("end", Json.createObjectNode().put("line", line).put("character", col + len));
        loc.set("range", range);
        return loc;
    }

    /**
     * A bare {@code {start,end}} range spanning {@code node}, for
     * documentSymbol. Unlike {@link #locationJson} this reads a live tree node,
     * so it gets the whole span - a function's entire body, say - rather than
     * just a name's length.
     */
    private static ObjectNode rangeJson(Node node) {
        var s = node.getStartPoint();
        var e = node.getEndPoint();
        ObjectNode range = Json.createObjectNode();
        range.set("start", Json.createObjectNode().put("line", s.getRow()).put("character", s.getColumn()));
        range.set("end", Json.createObjectNode().put("line", e.getRow()).put("character", e.getColumn()));
        return range;
    }

    private void collectFuncRefs(PhpFunction def, boolean includeDecl, ArrayNode locs) {
        for (PhpFunction f : symbols.funcRefs(def, includeDecl)) {
            PhpFile file = app.file(f.fileId());
            locs.add(locationJson(file.uri(), f.line(), f.col(), byteLength(f.name())));
        }
    }

    private void collectVarRefs(PhpVar def, boolean includeDecl, ArrayNode locs) {
        for (PhpVar v : symbols.varRefs(def, includeDecl)) {
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

    private static ObjectNode handleInitialize() {
        ObjectNode result = Json.createObjectNode();
        ObjectNode caps = Json.createObjectNode();
        caps.put("textDocumentSync", 1);
        caps.put("hoverProvider", true);
        caps.put("definitionProvider", true);
        caps.put("referencesProvider", true);
        caps.put("documentSymbolProvider", true);
        caps.put("workspaceSymbolProvider", true);

        ObjectNode completion = Json.createObjectNode();
        ArrayNode triggers = Json.createArrayNode();
        triggers.add(">");
        triggers.add("$");
        triggers.add(":");
        completion.set("triggerCharacters", triggers);
        caps.set("completionProvider", completion);

        result.set("capabilities", caps);
        return result;
    }

    private void handleDidOpen(DidOpenParams params) {
        TextDocumentItem td = params.textDocument();
        String uri = td == null ? null : td.uri();
        String text = td == null ? null : td.text();
        if (uri == null || text == null) return;
        byte[] content = text.getBytes(StandardCharsets.UTF_8);
        log("didOpen: " + uri + " (" + content.length + " bytes)\n");

        if (!app.reparseFile(uri, content)) {
            log("didOpen: " + uri + " not in index, skipping reparse\n");
        }
    }

    private void handleDidChange(DidChangeParams params) {
        TextDocumentIdentifier td = params.textDocument();
        String uri = td == null ? null : td.uri();
        List<ContentChange> changes = params.contentChanges();
        ContentChange change = changes == null || changes.isEmpty() ? null : changes.get(0);
        String text = change == null ? null : change.text();
        if (uri == null || text == null) return;
        byte[] content = text.getBytes(StandardCharsets.UTF_8);
        log("didChange: " + uri + " (" + content.length + " bytes)\n");

        /* textDocumentSync is Full (1): `text` is always the whole document */
        if (!app.reparseFile(uri, content)) {
            log("didChange: " + uri + " not in index, skipping reparse\n");
        }
    }

    private void handleDidClose(TextDocumentParams params) {
        TextDocumentIdentifier td = params.textDocument();
        log("didClose: " + (td == null ? null : td.uri()) + "\n");
    }

    private @Nullable Hover handleHover(TextDocumentPosition params) {
        TextDocumentIdentifier td = params.textDocument();
        Position pos = params.position();
        if (td == null || pos == null) return null;
        String uri = td.uri();
        int line = pos.line();
        int col = pos.character();
        log("hover: " + uri + " " + line + ":" + col + "\n");

        PhpFile file = app.findFile(uri);
        if (file == null) return null;

        Resolved r = symbols.resolveAt(file, line, col);
        String text = null;
        if (r.var() != null) {
            PhpVar var = r.var();
            String type = var.type() != null ? var.type() : symbols.latestVarType(var);
            text = type != null
                    ? "```php\n" + var.name() + ": " + type + "\n```"
                    : "```php\n" + var.name() + "\n```";
        } else if (r.func() != null) {
            PhpFunction func = r.func();
            String ret = func.returnType();
            if (ret == null && func.kind() != FuncKind.DEF) {
                PhpFunction def = symbols.findFuncDef(func);
                if (def != null) ret = def.returnType();
            }
            text = ret != null
                    ? "```php\nfunction " + func.name() + "(): " + ret + "\n```"
                    : "```php\nfunction " + func.name() + "()\n```";
        }
        if (text == null) return null;

        return new Hover(new MarkupContent("markdown", text));
    }

    private @Nullable ObjectNode handleDefinition(TextDocumentPosition params) {
        TextDocumentIdentifier td = params.textDocument();
        Position pos = params.position();
        if (td == null || pos == null) return null;
        String uri = td.uri();
        int line = pos.line();
        int col = pos.character();
        log("definition: " + uri + " " + line + ":" + col + "\n");

        PhpFile file = app.findFile(uri);
        if (file == null) return null;

        Resolved r = symbols.resolveAt(file, line, col);

        if (r.func() != null) {
            PhpFunction def = symbols.findFuncDef(r.func());
            if (def != null) {
                PhpFile target = app.file(def.fileId());
                return locationJson(target.uri(), def.line(), def.col(),
                        byteLength(def.name()));
            }
        } else if (r.var() != null) {
            PhpVar def = symbols.findVarDef(r.var());
            if (def != null) {
                PhpFile target = app.file(def.fileId());
                return locationJson(target.uri(), def.line(), def.col(),
                        byteLength(def.name()));
            }
        }

        return null;
    }

    private ArrayNode handleReferences(ReferenceParams params) {
        TextDocumentIdentifier td = params.textDocument();
        Position pos = params.position();
        ReferenceContext rctx = params.context();
        ArrayNode locs = Json.createArrayNode();
        if (td == null || pos == null) return locs;
        String uri = td.uri();
        int line = pos.line();
        int col = pos.character();
        boolean includeDecl = rctx != null && rctx.includeDeclaration();
        log("references: " + uri + " " + line + ":" + col + "\n");

        PhpFile file = app.findFile(uri);
        if (file == null) return locs;

        Resolved r = symbols.resolveAt(file, line, col);

        if (r.func() != null) {
            PhpFunction def = symbols.findFuncDef(r.func());
            if (def != null) collectFuncRefs(def, includeDecl, locs);
        } else if (r.var() != null) {
            PhpVar def = symbols.findVarDef(r.var());
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
    private static ObjectNode symbolNew(ArrayNode parentChildren, String name, int kind, Node whole,
                                        Node nameNode) {
        ObjectNode sym = Json.createObjectNode();
        sym.put("name", name);
        sym.put("kind", kind);
        sym.set("range", rangeJson(whole));
        sym.set("selectionRange", rangeJson(nameNode));
        parentChildren.add(sym);
        return sym;
    }

    /** A class/interface/trait/enum body: its properties and methods. */
    private static void collectClassMembers(Node list, ArrayNode children) {
        int count = list.getNamedChildCount();
        for (int i = 0; i < count; i++) {
            Node node = list.getNamedChild(i);
            String type = node.getType();

            switch (type) {
                case "method_declaration" -> {
                    Node name = node.getChildByFieldName("name");
                    if (name == null) continue;
                    String text = name.getContent();
                    int kind = text.equals("__construct") ? SK_CONSTRUCTOR : SK_METHOD;
                    symbolNew(children, text, kind, node, name);
                }
                case "property_declaration" -> {
                    /* one `public int $a, $b;` declares multiple */
                    int pc = node.getNamedChildCount();
                    for (int j = 0; j < pc; j++) {
                        Node el = node.getNamedChild(j);
                        if (!el.getType().equals("property_element")) continue;
                        Node varName = el.getChildByFieldName("name");
                        if (varName == null) continue;
                        symbolNew(children, varName.getContent(), SK_PROPERTY, node, varName);
                    }
                }
                case "enum_case" -> {
                    Node name = node.getChildByFieldName("name");
                    if (name == null) continue;
                    symbolNew(children, name.getContent(), SK_ENUM_MEMBER, node, name);
                }
            }
        }
    }

    private static void collectClassSymbol(Node node, ArrayNode out, int kind) {
        Node name = node.getChildByFieldName("name");
        if (name == null) return;
        ObjectNode sym = symbolNew(out, name.getContent(), kind, node, name);

        ArrayNode children = Json.createArrayNode();
        sym.set("children", children);
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
    private static void collectDocumentSymbols(Node root, ArrayNode out) {
        int count = root.getNamedChildCount();
        for (int i = 0; i < count; i++) {
            Node node = root.getNamedChild(i);
            String t = node.getType();

            switch (t) {
                case "function_definition" -> {
                    Node name = node.getChildByFieldName("name");
                    if (name == null) continue;
                    symbolNew(out, name.getContent(), SK_FUNCTION, node, name);
                }
                case "class_declaration", "trait_declaration" -> collectClassSymbol(node, out, SK_CLASS);
                case "interface_declaration" -> collectClassSymbol(node, out, SK_INTERFACE);
                case "enum_declaration" -> collectClassSymbol(node, out, SK_ENUM);
                case "namespace_definition" -> {
                    Node body = node.getChildByFieldName("body");
                    Node nsName = node.getChildByFieldName("name");
                    if (body != null && nsName != null) {
                        ObjectNode sym = symbolNew(out, nsName.getContent(), SK_NAMESPACE, node, nsName);
                        ArrayNode children = Json.createArrayNode();
                        sym.set("children", children);
                        collectDocumentSymbols(body, children);
                    }
                }
            }
        }
    }

    private ArrayNode handleDocumentSymbol(TextDocumentParams params) {
        TextDocumentIdentifier td = params.textDocument();
        ArrayNode symbols = Json.createArrayNode();
        if (td == null) return symbols;
        String uri = td.uri();
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
    private static ObjectNode symbolInfoJson(String name, int kind, String uri, Node nameNode,
                                             @Nullable String container) {
        ObjectNode sym = Json.createObjectNode();
        sym.put("name", name);
        sym.put("kind", kind);
        var p = nameNode.getStartPoint();
        int len = nameNode.getEndByte() - nameNode.getStartByte();
        sym.set("location", locationJson(uri, p.getRow(), p.getColumn(), len));
        if (container != null) sym.put("containerName", container);
        return sym;
    }

    /**
     * Flat counterpart to {@link #collectClassMembers}: same shape, but
     * filtered by {@code query} and emitting SymbolInformation with
     * {@code container} set to the class name.
     */
    private static void collectClassMembersFlat(Node list, String uri, String query,
                                                String container, ArrayNode out) {
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
    private static void collectWorkspaceSymbols(Node root, String uri, String query, ArrayNode out) {
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

    private ArrayNode handleWorkspaceSymbol(WorkspaceSymbolParams params) {
        String query = params.query();
        if (query == null) query = "";
        log("workspace/symbol: " + query + "\n");

        ArrayNode out = Json.createArrayNode();
        for (PhpFile file : app.files()) {
            collectWorkspaceSymbols(file.tree().getRootNode(), file.uri(), query, out);
        }
        return out;
    }

    // -----------------------------------------------------------------------
    // completion
    // -----------------------------------------------------------------------

    /**
     * {@code $obj->}: properties and methods of obj's resolved class, from
     * anywhere in the workspace, since a class can live in a different file
     * than the usage.
     */
    private void addMemberCompletions(List<CompletionItem> items, String cls) {
        for (PhpVar v : symbols.classProperties(cls)) {
            /* v.name() is "$prop" (real, from the declaration); strip the "$"
             * since nothing is typed after "->" */
            String label = v.name().startsWith("$") ? v.name().substring(1) : v.name();
            items.add(new CompletionItem(label, CIK_FIELD, v.type()));
        }
        for (PhpFunction f : symbols.classMethods(cls)) {
            items.add(new CompletionItem(f.name(), CIK_METHOD, f.returnType()));
        }
    }

    /** Each in-scope variable, with whatever type is known for it in that scope. */
    private void addVarCompletions(List<CompletionItem> items, int fileId, @Nullable String functionName) {
        for (PhpVar v : symbols.varsInScope(fileId, functionName)) {
            String type = v.type();
            if (type == null) {
                type = symbols.typeAnywhereInScope(fileId, functionName, v.name());
            }
            items.add(new CompletionItem(v.name(), CIK_VARIABLE, type));
        }
    }

    private List<CompletionItem> handleCompletion(TextDocumentPosition params) {
        TextDocumentIdentifier td = params.textDocument();
        Position pos = params.position();
        List<CompletionItem> items = new ArrayList<>();
        if (td == null || pos == null) return items;
        String uri = td.uri();
        int line = pos.line();
        int col = pos.character();
        log("completion: " + uri + " " + line + ":" + col + "\n");

        PhpFile file = app.findFile(uri);
        if (file == null) return items;

        byte[] content = file.content();
        int off = byteOffsetFor(content, line, col);

        /* "ClassName::" - static member completion is not supported (no
         * static-member tracking in the index yet); say so with an empty list
         * rather than offering irrelevant local variables */
        if (endsWith(content, off, "::")) return items;

        var pt = new Point(line, col);
        Node at = file.tree().getRootNode().getNamedDescendant(pt, pt);

        String obj = varBeforeArrow(content, off);
        if (obj != null) {
            String cls;
            if (obj.equals("$this")) {
                cls = SymbolFinder.enclosingClassName(at);
            } else {
                String fn = SymbolFinder.enclosingFunctionName(at);
                cls = SymbolFinder.stripNs(symbols.typeOfVarBefore(file.fileId(), fn, obj, pt));
            }
            if (cls != null) addMemberCompletions(items, cls);
            return items;
        }

        String fn = SymbolFinder.enclosingFunctionName(at);
        String cls = SymbolFinder.enclosingClassName(at);
        if (cls != null) {
            items.add(new CompletionItem("$this", CIK_VARIABLE, cls));
        }
        addVarCompletions(items, file.fileId(), fn);
        return items;
    }

    // -----------------------------------------------------------------------
    // Transport
    // -----------------------------------------------------------------------

    /** @return the message body, or {@code null} at end of input or on a bad header */
    private byte @Nullable [] readMessage() throws IOException {
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

    private @Nullable String readHeaderLine() throws IOException {
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

    private void write(JsonNode doc) throws IOException {
        byte[] body = Json.writeValueAsBytes(doc);
        out.write(("Content-Length: " + body.length + "\r\n\r\n")
                .getBytes(StandardCharsets.US_ASCII));
        out.write(body);
        out.flush();
    }

    /**
     * {@code result} is whatever the handler returned - a DTO, a raw node, or
     * {@code null}. It is written as an explicit JSON null rather than left to
     * the mapper's NON_NULL inclusion, because JSON-RPC requires a success
     * response to carry the key even when the answer is "nothing here".
     */
    private void respond(@Nullable JsonNode id, @Nullable Object result) throws IOException {
        ObjectNode root = Json.createObjectNode();
        root.put("jsonrpc", "2.0");
        root.set("id", id == null ? NullNode.getInstance() : id);
        root.set("result", result == null ? NullNode.getInstance() : Json.valueToTree(result));
        write(root);
    }

    private void respondMethodNotFound(JsonNode id) throws IOException {
        ObjectNode error = Json.createObjectNode();
        error.put("code", -32601);
        error.put("message", "method not found");
        ObjectNode root = Json.createObjectNode();
        root.put("jsonrpc", "2.0");
        root.set("id", id);
        root.set("error", error);
        write(root);
    }

    private void log(String message) {
        try {
            log.write(message);
            log.flush();
        } catch (IOException ignored) {
        }
    }
}
