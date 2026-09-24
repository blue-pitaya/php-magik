package dev.bluepitaya.phpmagik.lsp.handler;

import dev.bluepitaya.phpmagik.AppException;
import dev.bluepitaya.phpmagik.PhpFile;
import dev.bluepitaya.phpmagik.Workspace;
import dev.bluepitaya.phpmagik.lsp.Json;
import dev.bluepitaya.phpmagik.lsp.Logger;
import dev.bluepitaya.phpmagik.lsp.dto.TextDocumentPosition;
import dev.bluepitaya.phpmagik.phpsymbol.PhpClassDeclaration;
import dev.bluepitaya.phpmagik.phpsymbol.PhpFunctionDefinition;
import dev.bluepitaya.phpmagik.phpsymbol.PhpMethodDeclaration;
import dev.bluepitaya.phpmagik.phpsymbol.PhpMethodLocalVarDeclaration;
import dev.bluepitaya.phpmagik.phpsymbol.PhpParameterDeclaration;
import dev.bluepitaya.phpmagik.phpsymbol.PhpPropertyDeclaration;
import dev.bluepitaya.phpmagik.phpsymbol.PhpSymbolOwner;
import dev.bluepitaya.phpmagik.phpsymbol.PhpType;
import dev.bluepitaya.phpmagik.ts.Point;
import dev.bluepitaya.phpmagik.ts.Range;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;

@NullMarked
public final class CompletionHandler {

    private static final int KIND_METHOD = 2;
    private static final int KIND_FIELD = 5;
    private static final int KIND_VARIABLE = 6;

    private final Workspace workspace;
    private final Logger log;

    public CompletionHandler(Workspace workspace, Logger log) {
        this.workspace = workspace;
        this.log = log;
    }

    public ArrayNode handle(TextDocumentPosition params) {
        ArrayNode items = Json.array();

        String uri = params.textDocument().uri();
        int line = params.position().line();
        int col = params.position().character();

        log.log("completion: " + uri + " " + line + ":" + col);

        PhpFile file = workspace.findFile(uri);
        if (file == null) {
            throw new AppException("file not found: " + uri);
        }

        String[] lines = new String(file.content(), StandardCharsets.UTF_8).split("\n", -1);
        String text = line >= 0 && line < lines.length ? lines[line] : "";
        String prefix = text.substring(0, Math.min(Math.max(col, 0), text.length()));
        int end = prefix.length();
        while (end > 0 && isNameChar(prefix.charAt(end - 1))) {
            end--;
        }
        String trigger = prefix.substring(0, end);

        if (trigger.endsWith("::")) {
            return items;
        }

        PhpSymbolOwner owner = enclosingOwner(file, new Point(line, col));

        if (trigger.endsWith("->")) {
            members(items, receiver(trigger), owner);
        } else {
            locals(items, owner);
        }

        return items;
    }

    private void members(ArrayNode items, String receiver, @Nullable PhpSymbolOwner owner) {
        PhpClassDeclaration owningClass = receiverClass(receiver, owner);
        if (owningClass == null) {
            return;
        }

        for (PhpPropertyDeclaration property : workspace.symbols().propertyDeclarations()) {
            String name = property.name();
            if (property.owner() == owningClass && name != null) {
                items.add(item(name.substring(1), KIND_FIELD, detail(property.type())));
            }
        }
        for (PhpMethodDeclaration method : workspace.symbols().methodDeclarations()) {
            if (method.owner() == owningClass && method.name() != null) {
                items.add(item(method.name(), KIND_METHOD, detail(method.returnType())));
            }
        }
    }

    private void locals(ArrayNode items, @Nullable PhpSymbolOwner owner) {
        if (owner instanceof PhpMethodDeclaration method && method.owner() != null) {
            items.add(item("$this", KIND_VARIABLE, method.owner().name()));
        }
        for (PhpParameterDeclaration param : workspace.symbols().parameterDeclarations()) {
            if (param.owner() == owner && param.name() != null) {
                items.add(item(param.name(), KIND_VARIABLE, detail(param.type())));
            }
        }
        for (PhpMethodLocalVarDeclaration local : workspace.symbols().localVarDeclarations()) {
            if (local.owner() == owner && local.name() != null) {
                items.add(item(local.name(), KIND_VARIABLE, detail(local.type())));
            }
        }
    }

    private @Nullable PhpClassDeclaration receiverClass(String receiver, @Nullable PhpSymbolOwner owner) {
        if ("$this".equals(receiver)) {
            return owner instanceof PhpMethodDeclaration method ? method.owner() : null;
        }
        for (PhpParameterDeclaration param : workspace.symbols().parameterDeclarations()) {
            if (param.owner() == owner && receiver.equals(param.name())) {
                return classNamed(param.type());
            }
        }
        for (PhpMethodLocalVarDeclaration local : workspace.symbols().localVarDeclarations()) {
            if (local.owner() == owner && receiver.equals(local.name())) {
                return classNamed(local.type());
            }
        }
        return null;
    }

    private @Nullable PhpClassDeclaration classNamed(@Nullable PhpType type) {
        if (!(type instanceof PhpType.ClassType classType)) {
            return null;
        }
        for (PhpClassDeclaration declared : workspace.symbols().classDeclarations()) {
            if (classType.name().equals(declared.name())) {
                return declared;
            }
        }
        return null;
    }

    private @Nullable PhpSymbolOwner enclosingOwner(PhpFile file, Point at) {
        PhpSymbolOwner found = null;
        Range best = null;

        for (PhpMethodDeclaration method : workspace.symbols().methodDeclarations()) {
            Range scope = method.scope();
            if (method.file() == file && scope != null && scope.contains(at)
                    && (best == null || scope.isWithin(best))) {
                found = method;
                best = scope;
            }
        }
        for (PhpFunctionDefinition function : workspace.symbols().functionDefinitions()) {
            Range scope = function.scope();
            if (function.file() == file && scope != null && scope.contains(at)
                    && (best == null || scope.isWithin(best))) {
                found = function;
                best = scope;
            }
        }
        return found;
    }

    private static String receiver(String trigger) {
        String beforeArrow = trigger.substring(0, trigger.length() - 2);
        int start = beforeArrow.length();
        while (start > 0 && (isNameChar(beforeArrow.charAt(start - 1)) || beforeArrow.charAt(start - 1) == '$')) {
            start--;
        }
        return beforeArrow.substring(start);
    }

    private static boolean isNameChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    private static @Nullable String detail(@Nullable PhpType type) {
        return type == null ? null : type.php();
    }

    private static ObjectNode item(String label, int kind, @Nullable String detail) {
        ObjectNode item = Json.object();
        item.put("label", label);
        item.put("kind", kind);
        if (detail != null) {
            item.put("detail", detail);
        }
        return item;
    }
}
