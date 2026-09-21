package dev.bluepitaya.phpmagik.lsp;

import dev.bluepitaya.phpmagik.SymbolFinder;
import dev.bluepitaya.phpmagik.Workspace;
import dev.bluepitaya.phpmagik.lsp.dto.DidChangeParams;
import dev.bluepitaya.phpmagik.lsp.dto.DidOpenParams;
import dev.bluepitaya.phpmagik.lsp.dto.ReferenceParams;
import dev.bluepitaya.phpmagik.lsp.dto.TextDocumentParams;
import dev.bluepitaya.phpmagik.lsp.dto.TextDocumentPosition;
import dev.bluepitaya.phpmagik.lsp.dto.WorkspaceSymbolParams;
import dev.bluepitaya.phpmagik.lsp.handler.DefinitionHandler;
import dev.bluepitaya.phpmagik.lsp.handler.DidChangeHandler;
import dev.bluepitaya.phpmagik.lsp.handler.DidCloseHandler;
import dev.bluepitaya.phpmagik.lsp.handler.DidOpenHandler;
import dev.bluepitaya.phpmagik.lsp.handler.DocumentSymbolHandler;
import dev.bluepitaya.phpmagik.lsp.handler.HoverHandler;
import dev.bluepitaya.phpmagik.lsp.handler.InitializeHandler;
import dev.bluepitaya.phpmagik.lsp.handler.ReferencesHandler;
import dev.bluepitaya.phpmagik.lsp.handler.WorkspaceSymbolHandler;
import dev.bluepitaya.phpmagik.resolver.ClassResolver;
import dev.bluepitaya.phpmagik.resolver.FunctionResolver;
import dev.bluepitaya.phpmagik.resolver.MethodResolver;
import dev.bluepitaya.phpmagik.resolver.PropertyResolver;
import dev.bluepitaya.phpmagik.resolver.TypeInference;
import dev.bluepitaya.phpmagik.resolver.VariableResolver;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.NullNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

@NullMarked
public final class LspServer {

    private static final String CONTENT_LENGTH = "Content-Length:";

    private final InputStream in;
    private final OutputStream out;
    private final Logger log;

    private final InitializeHandler initialize;
    private final DidOpenHandler didOpen;
    private final DidChangeHandler didChange;
    private final DidCloseHandler didClose;
    private final HoverHandler hover;
    private final DefinitionHandler definition;
    private final ReferencesHandler references;
    private final DocumentSymbolHandler documentSymbol;
    private final WorkspaceSymbolHandler workspaceSymbol;

    public LspServer(Workspace app, Logger log) {
        this.in = new BufferedInputStream(System.in);
        this.out = System.out;
        this.log = log;

        var symbols = new SymbolFinder(app);
        var variables = new VariableResolver(app);
        var functions = new FunctionResolver(app);
        /* the member resolvers ask it which class an access is on, so it comes
         * first and knows nothing of them */
        var types = new TypeInference(app, functions);
        var methods = new MethodResolver(app, types);
        var properties = new PropertyResolver(app, types);
        var classes = new ClassResolver(app, symbols);
        this.initialize = new InitializeHandler();
        this.didOpen = new DidOpenHandler(app, log);
        this.didChange = new DidChangeHandler(app, log);
        this.didClose = new DidCloseHandler(log);
        this.hover = new HoverHandler(app, symbols, types, functions, methods, properties, log);
        this.definition = new DefinitionHandler(app, symbols, variables, functions, methods,
                properties, classes, log);
        this.references = new ReferencesHandler(app, symbols, variables, functions, methods,
                properties, classes, log);
        this.documentSymbol = new DocumentSymbolHandler(app, log);
        this.workspaceSymbol = new WorkspaceSymbolHandler(app, log);
    }

    public void run() throws IOException {
        var running = true;

        while (running) {
            var message = readMessage();
            if (message == null) {
                break;
            }

            JsonNode request;
            try {
                request = Json.RejectsNullFieldsMapper.readTree(message);
            } catch (JacksonException malformed) {
                continue;
            }

            try {
                running = dispatch(request);
            } catch (IllegalArgumentException badRequest) {
                log.log("bad request: " + badRequest.getMessage());
            }
        }
    }

    private boolean dispatch(JsonNode request) throws IOException {
        String method = getRequestMethodOrThrow(request);
        JsonNode params = request.get("params");
        if (params == null) {
            params = Json.object();
        }

        try {
            switch (method) {
                case "initialize" -> respond(getRequestIdOrThrow(request), initialize.handle());
                case "initialized" -> log.log("initialized");
                case "shutdown" -> respond(getRequestIdOrThrow(request), null);
                case "exit" -> {
                    return false;
                }
                case "textDocument/didOpen" -> didOpen.handle(bind(params, DidOpenParams.class));
                case "textDocument/didChange" -> didChange.handle(bind(params, DidChangeParams.class));
                case "textDocument/didClose" -> didClose.handle(bind(params, TextDocumentParams.class));
                case "textDocument/hover" -> respond(getRequestIdOrThrow(request),
                        hover.handle(bind(params, TextDocumentPosition.class)));
                case "textDocument/definition" -> respond(getRequestIdOrThrow(request),
                        definition.handle(bind(params, TextDocumentPosition.class)));
                case "textDocument/references" -> respond(getRequestIdOrThrow(request),
                        references.handle(bind(params, ReferenceParams.class)));
                case "textDocument/documentSymbol" -> respond(getRequestIdOrThrow(request),
                        documentSymbol.handle(bind(params, TextDocumentParams.class)));
                case "workspace/symbol" -> respond(getRequestIdOrThrow(request),
                        workspaceSymbol.handle(bind(params, WorkspaceSymbolParams.class)));
                default -> {
                    JsonNode id = request.get("id");
                    /* an unknown notification is still a notification: no reply */
                    if (id == null) {
                        log.log("ignoring notification " + method);
                    } else {
                        respondMethodNotFound(id);
                    }
                }
            }
        } catch (JacksonException badParams) {
            log.log("unbindable params for " + method + ": " + badParams.getMessage());
        }

        return true;
    }

    private <T> T bind(JsonNode params, Class<T> type) throws JacksonException {
        return Json.RejectsNullFieldsMapper.treeToValue(params, type);
    }

    private String getRequestMethodOrThrow(JsonNode node) {
        @Nullable JsonNode value = node.get("method");
        if (value == null) {
            throw new IllegalArgumentException("method is required");
        }
        if (!value.isString()) {
            throw new IllegalArgumentException("method must be a string");
        }

        return value.stringValue();
    }

    private JsonNode getRequestIdOrThrow(JsonNode node) {
        @Nullable JsonNode value = node.get("id");
        if (value == null) {
            throw new IllegalArgumentException("id is required");
        }
        if (!value.isIntegralNumber() && !value.isString()) {
            throw new IllegalArgumentException("id must be an integer or a string");
        }

        return value;
    }

    private byte @Nullable [] readMessage() throws IOException {
        int contentLength = -1;
        String line;
        while ((line = readHeaderLine()) != null && !line.isEmpty()) {
            if (line.startsWith(CONTENT_LENGTH)) {
                contentLength = parseContentLength(line.substring(CONTENT_LENGTH.length()));
            }
        }
        if (line == null || contentLength <= 0) return null;

        byte[] body = in.readNBytes(contentLength);
        return body.length == contentLength ? body : null;
    }

    //TODO: limit line length
    private @Nullable String readHeaderLine() throws IOException {
        var line = new StringBuilder();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') return line.toString();
            if (c != '\r') line.append((char) c);
        }
        return line.isEmpty() ? null : line.toString();
    }

    private int parseContentLength(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException malformed) {
            return -1;
        }
    }

    private void respond(JsonNode id, @Nullable Object result) throws IOException {
        ObjectNode root = responseNode(id);
        root.set("result", result == null ? NullNode.getInstance() : Json.RejectsNullFieldsMapper.valueToTree(result));

        write(root);
    }

    private void respondMethodNotFound(JsonNode id) throws IOException {
        ObjectNode error = Json.object();
        error.put("code", -32601);
        error.put("message", "method not found");

        ObjectNode root = responseNode(id);
        root.set("error", error);

        write(root);
    }

    private ObjectNode responseNode(JsonNode id) {
        ObjectNode root = Json.object();
        root.put("jsonrpc", "2.0");
        root.set("id", id);

        return root;
    }

    private void write(JsonNode doc) throws IOException {
        byte[] body = Json.RejectsNullFieldsMapper.writeValueAsBytes(doc);
        out.write(("Content-Length: " + body.length + "\r\n\r\n")
                .getBytes(StandardCharsets.US_ASCII));
        out.write(body);
        out.flush();
    }
}
