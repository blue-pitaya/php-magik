package dev.bluepitaya.phpmagik;

import dev.bluepitaya.phpmagik.phpsymbol.ClassKind;
import dev.bluepitaya.phpmagik.phpsymbol.PhpClassDefinition;
import dev.bluepitaya.phpmagik.phpsymbol.PhpClassUsage;
import dev.bluepitaya.phpmagik.phpsymbol.PhpFunctionDefinition;
import dev.bluepitaya.phpmagik.phpsymbol.PhpFunctionUsage;
import dev.bluepitaya.phpmagik.phpsymbol.PhpMethodDefinition;
import dev.bluepitaya.phpmagik.phpsymbol.PhpMethodUsage;
import dev.bluepitaya.phpmagik.phpsymbol.PhpPropertyDefinition;
import dev.bluepitaya.phpmagik.phpsymbol.PhpPropertyUsage;
import dev.bluepitaya.phpmagik.phpsymbol.PhpSymbolCollection;
import dev.bluepitaya.phpmagik.phpsymbol.PhpUseStatement;
import dev.bluepitaya.phpmagik.phpsymbol.PhpVarDefinition;
import dev.bluepitaya.phpmagik.phpsymbol.PhpVarUsage;
import dev.bluepitaya.phpmagik.phpsymbol.UseKind;
import dev.bluepitaya.phpmagik.ts.Node;
import dev.bluepitaya.phpmagik.ts.Nodes;
import dev.bluepitaya.phpmagik.ts.Range;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;

@NullMarked
final class CtxPhpSymbolAdder {

    private final PhpFile file;
    private final PhpSymbolCollection symbols = new PhpSymbolCollection();

    private @Nullable String ns;

    CtxPhpSymbolAdder(PhpFile file) {
        this.file = file;
    }

    PhpSymbolCollection symbols() {
        return symbols;
    }

    @Nullable String ns() {
        return ns;
    }

    void ns(@Nullable String ns) {
        this.ns = ns;
    }

    void addVarUsage(Node name, Scope scope) {
        symbols.add(new PhpVarUsage(name.getContent(), ns, scope.function(), Range.of(name), file));
    }

    void addVarDefinition(Node name, @Nullable String type, @Nullable Range valueSource,
                          Scope scope) {
        symbols.add(new PhpVarDefinition(name.getContent(), ns, scope.function(), type,
                valueSource, Range.of(name), file));
    }

    void addProperty(@Nullable Node name, @Nullable PhpClassDefinition owner,
                     @Nullable String type) {
        if (name == null || owner == null) return;
        symbols.add(new PhpPropertyDefinition(name.getContent(), owner, type, Range.of(name),
                file));
    }

    void addPropertyUsage(Node access, Scope scope) {
        Node name = PhpNodes.memberName(access);
        if (name == null) return;

        symbols.add(new PhpPropertyUsage("$" + name.getContent(), thisClass(access, scope),
                objectSource(access), Range.of(name), file));
    }

    void addMethodUsage(Node access, Scope scope) {
        Node name = PhpNodes.memberName(access);
        if (name == null) return;

        symbols.add(new PhpMethodUsage(name.getContent(), thisClass(access, scope),
                objectSource(access), Range.of(name), file));
    }

    void addClassUsage(@Nullable Node name) {
        String type = Nodes.type(name);
        if (type == null || !PhpNodes.NAME_TYPES.contains(type)) return;

        symbols.add(new PhpClassUsage(name.getContent(), ns, Range.of(name), file));
    }

    void addFunctionUsage(Node call) {
        Node called = call.getChildByFieldName("function");
        String type = Nodes.type(called);
        if (type != null && PhpNodes.NAME_TYPES.contains(type)) {
            symbols.add(new PhpFunctionUsage(called.getContent(), ns, Range.of(called), file));
        }
    }

    @Nullable PhpClassDefinition addClass(Node declaration) {
        Node name = declaration.getChildByFieldName("name");
        if (name == null) return null;

        String simple = name.getContent();
        PhpClassDefinition declared = new PhpClassDefinition(simple, ns,
                ns == null ? simple : ns + "\\" + simple, classKind(declaration.getType()),
                Range.of(name), Range.of(declaration), file);
        symbols.add(declared);
        return declared;
    }

    void addFunction(Node declaration, Node name, @Nullable String returnType,
                     @Nullable Range returnSource) {
        symbols.add(new PhpFunctionDefinition(name.getContent(), ns, returnType, returnSource,
                signature(declaration), docComment(declaration), Range.of(name),
                Range.of(declaration), file));
    }

    void addMethod(Node declaration, Node name, PhpClassDefinition owner,
                   @Nullable String returnType, @Nullable Range returnSource) {
        symbols.add(new PhpMethodDefinition(name.getContent(), owner, returnType, returnSource,
                signature(declaration), docComment(declaration), Range.of(name),
                Range.of(declaration), file));
    }

    void addUse(Node clause, @Nullable String prefix, UseKind declared) {
        /* "path" or "path as alias": the path comes first, and the alias is the
         * only part with a field */
        Node path = Nodes.namedChild(clause, 0);
        if (path == null) return;

        Node alias = clause.getChildByFieldName("alias");
        /* a clause may narrow the declaration's kind inside a group */
        UseKind kind = clause.getChildByFieldName("type") != null ? useKind(clause) : declared;
        String fqn = stripLeadingSeparator(
                prefix == null ? path.getContent() : prefix + "\\" + path.getContent());
        String name = alias != null ? alias.getContent() : lastSegment(fqn);

        symbols.add(new PhpUseStatement(name, fqn, kind, Range.of(clause), file));
    }

    static UseKind useKind(Node node) {
        String type = Nodes.text(node.getChildByFieldName("type"));
        if ("function".equals(type)) return UseKind.FUNCTION;
        if ("const".equals(type)) return UseKind.CONST;
        return UseKind.CLASS;
    }

    private static ClassKind classKind(String nodeType) {
        return switch (nodeType) {
            case "interface_declaration" -> ClassKind.INTERFACE;
            case "trait_declaration" -> ClassKind.TRAIT;
            case "enum_declaration" -> ClassKind.ENUM;
            default -> ClassKind.CLASS;
        };
    }

    private static String stripLeadingSeparator(String fqn) {
        return fqn.startsWith("\\") ? fqn.substring(1) : fqn;
    }

    private static String lastSegment(String fqn) {
        int last = fqn.lastIndexOf('\\');
        return last < 0 ? fqn : fqn.substring(last + 1);
    }

    private static @Nullable String thisClass(Node access, Scope scope) {
        Node object = access.getChildByFieldName("object");
        boolean isThis = "variable_name".equals(Nodes.type(object))
                && "$this".equals(object.getContent());
        if (!isThis || scope.cls() == null) return null;
        return scope.cls().name();
    }

    private static @Nullable Range objectSource(Node access) {
        return PhpNodes.valueSource(access.getChildByFieldName("object"));
    }

    private @Nullable String signature(Node declaration) {
        byte[] source = file.content();
        Node body = declaration.getChildByFieldName("body");
        int start = declaration.getStartByte();
        int end = body != null ? body.getStartByte() : declaration.getEndByte();
        if (start < 0 || end > source.length || end <= start) {
            return null;
        }

        String text = new String(source, start, end - start, StandardCharsets.UTF_8).strip();

        /* an abstract or interface method ends in ";" where a body would be */
        if (text.endsWith(";")) {
            text = text.substring(0, text.length() - 1).strip();
        }

        return text;
    }

    //TODO: docComment should be a class for type inference logic
    private static @Nullable String docComment(Node declaration) {
        Node prev = declaration.getPrevSibling();
        if (prev == null || !"comment".equals(prev.getType())) {
            return null;
        }

        String text = prev.getContent();
        /* "/*" alone is an ordinary block comment, not a doc block */
        if (text == null || !text.startsWith("/**")) {
            return null;
        }

        return stripDocMarkers(text);
    }

    private static @Nullable String stripDocMarkers(String comment) {
        String body = comment.substring("/**".length());
        if (body.endsWith("*/")) {
            body = body.substring(0, body.length() - "*/".length());
        }

        var out = new StringBuilder();
        for (String line : body.split("\n", -1)) {
            String stripped = line.strip();
            if (stripped.startsWith("*")) {
                stripped = stripped.substring(1).strip();
            }
            if (out.isEmpty() && stripped.isEmpty()) {
                continue;
            }
            out.append(stripped).append('\n');
        }

        String doc = out.toString().strip();

        return doc.isEmpty() ? null : doc;
    }
}
