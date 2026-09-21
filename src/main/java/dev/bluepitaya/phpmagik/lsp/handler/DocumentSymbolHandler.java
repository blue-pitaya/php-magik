package dev.bluepitaya.phpmagik.lsp.handler;

import dev.bluepitaya.phpmagik.index.*;
import dev.bluepitaya.phpmagik.lsp.Json;
import dev.bluepitaya.phpmagik.lsp.Logger;
import dev.bluepitaya.phpmagik.lsp.dto.*;
import dev.bluepitaya.phpmagik.ts.Node;
import org.jspecify.annotations.NullMarked;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@NullMarked
public final class DocumentSymbolHandler {

    private final Workspace app;
    private final Logger log;

    public DocumentSymbolHandler(Workspace app, Logger log) {
        this.app = app;
        this.log = log;
    }

    public ArrayNode handle(TextDocumentParams params) {
        TextDocumentIdentifier td = params.textDocument();
        ArrayNode out = Json.array();
        if (td == null) return out;
        String uri = td.uri();
        log.log("documentSymbol: " + uri);

        PhpFile file = app.findFile(uri);
        if (file == null) return out;

        collectDocumentSymbols(file.tree().getRootNode(), out);
        return out;
    }

    /**
     * A bare {@code {start,end}} range spanning {@code node}. Unlike
     * {@link Json#location} this reads a live tree node, so it gets the whole
     * span - a function's entire body, say - rather than just a name's length.
     */
    private static ObjectNode rangeJson(Node node) {
        var s = node.getStartPoint();
        var e = node.getEndPoint();
        ObjectNode range = Json.object();
        range.set("start", Json.object().put("line", s.getRow()).put("character", s.getColumn()));
        range.set("end", Json.object().put("line", e.getRow()).put("character", e.getColumn()));
        return range;
    }

    /**
     * Builds a DocumentSymbol for {@code nameNode} (its own name becomes the
     * selectionRange) spanning {@code whole} (its full declaration becomes the
     * range), appends it to {@code parentChildren}, and returns it so the
     * caller can add a "children" array of its own.
     */
    private static ObjectNode symbolNew(ArrayNode parentChildren, String name, int kind, Node whole,
                                        Node nameNode) {
        ObjectNode sym = Json.object();
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
                    int kind = text.equals("__construct") ? SymbolKind.CONSTRUCTOR : SymbolKind.METHOD;
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
                        symbolNew(children, varName.getContent(), SymbolKind.PROPERTY, node, varName);
                    }
                }
                case "enum_case" -> {
                    Node name = node.getChildByFieldName("name");
                    if (name == null) continue;
                    symbolNew(children, name.getContent(), SymbolKind.ENUM_MEMBER, node, name);
                }
            }
        }
    }

    private static void collectClassSymbol(Node node, ArrayNode out, int kind) {
        Node name = node.getChildByFieldName("name");
        if (name == null) return;
        ObjectNode sym = symbolNew(out, name.getContent(), kind, node, name);

        ArrayNode children = Json.array();
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
                    symbolNew(out, name.getContent(), SymbolKind.FUNCTION, node, name);
                }
                case "class_declaration", "trait_declaration" -> collectClassSymbol(node, out, SymbolKind.CLASS);
                case "interface_declaration" -> collectClassSymbol(node, out, SymbolKind.INTERFACE);
                case "enum_declaration" -> collectClassSymbol(node, out, SymbolKind.ENUM);
                case "namespace_definition" -> {
                    Node body = node.getChildByFieldName("body");
                    Node nsName = node.getChildByFieldName("name");
                    if (body != null && nsName != null) {
                        ObjectNode sym = symbolNew(out, nsName.getContent(), SymbolKind.NAMESPACE, node, nsName);
                        ArrayNode children = Json.array();
                        sym.set("children", children);
                        collectDocumentSymbols(body, children);
                    }
                }
            }
        }
    }
}
