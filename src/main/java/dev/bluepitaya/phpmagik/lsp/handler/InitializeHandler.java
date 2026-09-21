package dev.bluepitaya.phpmagik.lsp.handler;

import dev.bluepitaya.phpmagik.lsp.Json;
import org.jspecify.annotations.NullMarked;
import tools.jackson.databind.node.ObjectNode;

@NullMarked
public final class InitializeHandler {

    public ObjectNode handle() {
        ObjectNode result = Json.object();
        ObjectNode caps = Json.object();
        caps.put("textDocumentSync", 1);
        caps.put("hoverProvider", true);
        caps.put("definitionProvider", true);
        caps.put("referencesProvider", true);
        caps.put("documentSymbolProvider", true);
        caps.put("workspaceSymbolProvider", true);

        result.set("capabilities", caps);
        return result;
    }
}
