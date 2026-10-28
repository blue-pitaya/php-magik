package dev.bluepitaya.phpmagik.lsp.handler;

import dev.bluepitaya.phpmagik.PhpFile;
import dev.bluepitaya.phpmagik.Workspace;
import dev.bluepitaya.phpmagik.lsp.Json;
import dev.bluepitaya.phpmagik.lsp.Logger;
import dev.bluepitaya.phpmagik.lsp.dto.TextDocumentPosition;
import dev.bluepitaya.phpmagik.phpsymbol.PhpClassDeclaration;
import dev.bluepitaya.phpmagik.phpsymbol.PhpFunctionLike;
import dev.bluepitaya.phpmagik.phpsymbol.PhpMethodDeclaration;
import dev.bluepitaya.phpmagik.phpsymbol.PhpMethodLocalVarDeclaration;
import dev.bluepitaya.phpmagik.phpsymbol.PhpParameterDeclaration;
import dev.bluepitaya.phpmagik.phpsymbol.PhpPropertyDeclaration;
import dev.bluepitaya.phpmagik.phpsymbol.PhpSymbolOwner;
import dev.bluepitaya.phpmagik.phpsymbol.PhpType;
import dev.bluepitaya.phpmagik.ts.Point;
import dev.bluepitaya.phpmagik.ts.Range;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;


@NullMarked
public final class CompletionHandler {

    private static final int KIND_METHOD = 2;
    private static final int KIND_FIELD = 5;
    private static final int KIND_VARIABLE = 6;

    private static final Pattern MEMBER_ACCESS =
            Pattern.compile("([$\\w]*)(->|::)\\w*$", Pattern.UNICODE_CHARACTER_CLASS);

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

        PhpFile file = workspace.findFileOrThrow(uri);

        String text = file.contentLine(line);
        if (text == null) {
            return items;
        }

        String prefix = text.substring(0, Math.clamp(col, 0, text.length()));
        Matcher access = MEMBER_ACCESS.matcher(prefix);
        boolean isAccess = access.find();

        if (isAccess && access.group(2).equals("::")) {
            return items;
        }

        PhpSymbolOwner owner = enclosingOwner(file, new Point(line, col));

        if (isAccess) {
            members(items, access.group(1), owner);
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
            if (property.owner().orElse(null) == owningClass) {
                items.add(item(name.substring(1), KIND_FIELD, detail(property.type().orElse(null))));
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
        String fqn = PhpType.classFqn(type);
        if (fqn == null) {
            return null;
        }
        for (PhpClassDeclaration declared : workspace.symbols().classDeclarations()) {
            if (fqn.equals(declared.fqn())) {
                return declared;
            }
        }
        return null;
    }

    private @Nullable PhpSymbolOwner enclosingOwner(PhpFile file, Point at) {
        PhpSymbolOwner found = null;
        Range best = null;

        for (PhpFunctionLike function : workspace.symbols().functionLikes()) {
            Range scope = function.scope();
            if (function.file() == file && scope != null && scope.contains(at)
                    && (best == null || scope.isWithin(best))) {
                found = function;
                best = scope;
            }
        }
        return found;
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
