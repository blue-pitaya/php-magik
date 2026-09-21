package dev.bluepitaya.phpmagik.lsp.handler;

import dev.bluepitaya.phpmagik.index.*;
import dev.bluepitaya.phpmagik.lsp.Json;
import dev.bluepitaya.phpmagik.lsp.Logger;
import dev.bluepitaya.phpmagik.lsp.dto.*;
import dev.bluepitaya.phpmagik.ts.Node;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@NullMarked
public final class WorkspaceSymbolHandler {

    private final Workspace app;
    private final Logger log;

    public WorkspaceSymbolHandler(Workspace app, Logger log) {
        this.app = app;
        this.log = log;
    }

    public ArrayNode handle(WorkspaceSymbolParams params) {
        String query = params.query();
        if (query == null) query = "";
        log.log("workspace/symbol: " + query);

        ArrayNode out = Json.array();
        for (PhpFile file : app.files()) {
            collectWorkspaceSymbols(file.tree().getRootNode(), file.uri(), query, out);
        }
        return out;
    }

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
        ObjectNode sym = Json.object();
        sym.put("name", name);
        sym.put("kind", kind);
        var p = nameNode.getStartPoint();
        int len = nameNode.getEndByte() - nameNode.getStartByte();
        sym.set("location", Json.location(uri, p.getRow(), p.getColumn(), len));
        if (container != null) sym.put("containerName", container);
        return sym;
    }

    /**
     * Flat counterpart to {@code DocumentSymbolHandler}'s class-member walk:
     * same shape, but filtered by {@code query} and emitting SymbolInformation
     * with {@code container} set to the class name.
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
                    int kind = text.equals("__construct") ? SymbolKind.CONSTRUCTOR : SymbolKind.METHOD;
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
                        out.add(symbolInfoJson(text, SymbolKind.PROPERTY, uri, varName, container));
                    }
                }
            } else if (type.equals("enum_case")) {
                Node name = node.getChildByFieldName("name");
                if (name == null) continue;
                String text = name.getContent();
                if (matchesQuery(text, query)) {
                    out.add(symbolInfoJson(text, SymbolKind.ENUM_MEMBER, uri, name, container));
                }
            }
        }
    }

    /**
     * Flat counterpart to {@code DocumentSymbolHandler}'s top-level walk:
     * called once per indexed file, emitting matching symbols into one array
     * spanning the whole workspace.
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
                    out.add(symbolInfoJson(text, SymbolKind.FUNCTION, uri, name, null));
                }
            } else if (t.equals("class_declaration") || t.equals("trait_declaration")
                    || t.equals("interface_declaration") || t.equals("enum_declaration")) {
                int kind = t.equals("interface_declaration") ? SymbolKind.INTERFACE
                        : t.equals("enum_declaration") ? SymbolKind.ENUM : SymbolKind.CLASS;
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
}
