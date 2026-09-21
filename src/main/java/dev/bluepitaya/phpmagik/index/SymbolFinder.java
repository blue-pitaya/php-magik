package dev.bluepitaya.phpmagik.index;

import dev.bluepitaya.phpmagik.ts.Node;
import dev.bluepitaya.phpmagik.ts.Nodes;
import dev.bluepitaya.phpmagik.ts.Point;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

@NullMarked
public final class SymbolFinder {

    private final Workspace workspace;

    public SymbolFinder(Workspace ws) {
        this.workspace = ws;
    }

    /* the tree belongs to the PhpFile and outlives this call, so it is borrowed,
     * never closed here: closing would free it for every later request */
    public @Nullable PhpSymbol resolveAt(PhpFile file, int line, int character) {
        Point pt = new Point(line, character);

        Node root = file.tree().getRootNode();
        if (root == null) return null;
        Node node = root.getNamedDescendant(pt, pt);
        if (node == null) return null;

        String t = node.getType();
        Point p = node.getStartPoint();
        String text = node.getContent();
        if (t == null || p == null || text == null) return null;

        return switch (t) {
            case "variable_name" -> findVarAt(file.fileId(), p, text);
            case "name", "qualified_name" -> {
                var func = findFuncAt(file.fileId(), p, text);
                yield func != null ? func : findVarAt(file.fileId(), p, "$" + text);
            }
            default -> null;
        };
    }

    private @Nullable PhpVar findVarAt(int fileId, Point p, String name) {
        for (var v : workspace.vars()) {
            if (v.fileId() == fileId && v.line() == p.getRow() && v.col() == p.getColumn()
                    && v.name().equals(name)) {
                return v;
            }
        }
        return null;
    }

    private @Nullable PhpFunction findFuncAt(int fileId, Point p, String name) {
        for (var f : workspace.funcs()) {
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
    public @Nullable PhpFunction findFuncDef(PhpFunction call) {
        if (call.kind() == FuncKind.DEF) return call;
        for (var f : workspace.funcs()) {
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
     * The PHPDoc block above {@code def}'s declaration, its {@code /**} and
     * leading {@code *} markers stripped, or {@code null} if the declaration has
     * no doc comment. Tags are left exactly as written - this locates the block,
     * it does not parse PHPDoc.
     *
     * <p>Only a declaration has one, so callers holding a call site should pass
     * it through {@link #findFuncDef} first.
     */
    public @Nullable String docComment(PhpFunction def) {
        if (def.kind() != FuncKind.DEF) return null;

        PhpFile file = workspace.file(def.fileId());
        Node root = file.tree().getRootNode();
        if (root == null) return null;
        Node decl = enclosingDeclaration(root.getNamedDescendant(
                new Point(def.line(), def.col()), new Point(def.line(), def.col())));
        if (decl == null) return null;

        Node prev = decl.getPrevSibling();
        /* attributes sit between the doc block and the declaration */
        while (prev != null && "attribute_list".equals(prev.getType())) {
            prev = prev.getPrevSibling();
        }
        if (prev == null || !"comment".equals(prev.getType())) return null;

        String text = prev.getContent();
        /* "/*" alone is an ordinary block comment, not a doc block */
        if (text == null || !text.startsWith("/**")) return null;
        return stripDocMarkers(text);
    }

    private static @Nullable Node enclosingDeclaration(@Nullable Node node) {
        while (node != null) {
            String t = node.getType();
            if ("function_definition".equals(t) || "method_declaration".equals(t)) return node;
            node = node.getParent();
        }
        return null;
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
            if (out.isEmpty() && stripped.isEmpty()) continue;
            out.append(stripped).append('\n');
        }

        String doc = out.toString().strip();
        return doc.isEmpty() ? null : doc;
    }

    /**
     * Definition target for a variable usage: the class property for
     * {@code $this}/{@code $obj} access, else the earliest occurrence in the
     * same scope.
     */
    public @Nullable PhpVar findVarDef(PhpVar use) {
        if (use.kind() == VarKind.PROPERTY) return use;
        if (use.kind() == VarKind.OBJ || use.kind() == VarKind.THIS) {
            for (var v : workspace.vars()) {
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
        for (var v : workspace.vars()) {
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
     * Every func-index entry that {@link #findFuncDef} would resolve to
     * {@code def}, in index order. {@code def} itself is included only if
     * {@code includeDecl}, matched by identity since names repeat across
     * unrelated classes.
     */
    public List<PhpFunction> funcRefs(PhpFunction def, boolean includeDecl) {
        var refs = new ArrayList<PhpFunction>();
        for (var f : workspace.funcs()) {
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
            if (match) refs.add(f);
        }
        return refs;
    }

    /** Every var-index entry that {@link #findVarDef} would resolve to {@code def}. */
    public List<PhpVar> varRefs(PhpVar def, boolean includeDecl) {
        var refs = new ArrayList<PhpVar>();
        for (var v : workspace.vars()) {
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
            if (match) refs.add(v);
        }
        return refs;
    }

    /**
     * Most recent known type of variable {@code name}, at or before
     * {@code before}, in the same file and function scope. Mirrors the
     * indexer's scope lookup, but at query time over the whole index rather
     * than during a single parse pass.
     */
    public @Nullable String typeOfVarBefore(int fileId, @Nullable String functionName, String name,
                                            Point before) {
        PhpVar best = null;
        for (var v : workspace.vars()) {
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

    public @Nullable String latestVarType(PhpVar at) {
        return typeOfVarBefore(at.fileId(), at.functionName(), at.name(),
                new Point(at.line(), at.col()));
    }

    /** Whatever type is known for {@code name} anywhere in that scope. */
    public @Nullable String typeAnywhereInScope(int fileId, @Nullable String functionName, String name) {
        return typeOfVarBefore(fileId, functionName, name,
                new Point(Integer.MAX_VALUE, Integer.MAX_VALUE));
    }

    /** Declared properties of {@code cls}, from anywhere in the workspace. */
    public List<PhpVar> classProperties(String cls) {
        var found = new ArrayList<PhpVar>();
        for (var v : workspace.vars()) {
            if (v.kind() == VarKind.PROPERTY && v.className() != null
                    && v.className().equals(cls)) {
                found.add(v);
            }
        }
        return found;
    }

    /** Declared methods of {@code cls}, from anywhere in the workspace. */
    public List<PhpFunction> classMethods(String cls) {
        var found = new ArrayList<PhpFunction>();
        for (var f : workspace.funcs()) {
            if (f.kind() == FuncKind.DEF && f.className() != null
                    && f.className().equals(cls)) {
                found.add(f);
            }
        }
        return found;
    }

    /**
     * Every variable in scope - same file plus enclosing function - deduped by
     * name, keeping the first occurrence.
     */
    public List<PhpVar> varsInScope(int fileId, @Nullable String functionName) {
        var seen = new LinkedHashSet<String>();
        var found = new ArrayList<PhpVar>();
        for (var v : workspace.vars()) {
            if (v.fileId() != fileId) continue;
            if (!Objects.equals(v.functionName(), functionName)) continue;
            if (seen.add(v.name())) found.add(v);
        }
        return found;
    }

    /** {@code ?Foo}, {@code \Foo} become {@code Foo}. */
    public static @Nullable String stripNs(@Nullable String t) {
        if (t == null) return null;
        var i = 0;
        while (i < t.length() && (t.charAt(i) == '?' || t.charAt(i) == '\\')) {
            i++;
        }
        return t.substring(i);
    }

    public static @Nullable String enclosingFunctionName(@Nullable Node node) {
        while (node != null) {
            var t = node.getType();
            if (t.equals("function_definition") || t.equals("method_declaration")) {
                return Nodes.text(node.getChildByFieldName("name"));
            }
            node = node.getParent();
        }
        return null;
    }

    public static @Nullable String enclosingClassName(@Nullable Node node) {
        while (node != null) {
            var t = node.getType();
            if (t.equals("class_declaration") || t.equals("interface_declaration")
                    || t.equals("trait_declaration") || t.equals("enum_declaration")) {
                return Nodes.text(node.getChildByFieldName("name"));
            }
            node = node.getParent();
        }
        return null;
    }
}
