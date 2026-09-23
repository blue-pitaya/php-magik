package dev.bluepitaya.phpmagik.resolver;

import dev.bluepitaya.phpmagik.Workspace;
import dev.bluepitaya.phpmagik.phpsymbol.PhpClassDeclaration;
import dev.bluepitaya.phpmagik.phpsymbol.PhpMethodDeclaration;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@NullMarked
public final class PhpMethodDeclarationResolver {

    private final Workspace workspace;

    public PhpMethodDeclarationResolver(Workspace workspace) {
        this.workspace = workspace;
    }

    public @Nullable PhpMethodDeclaration declarationOf(PhpClassDeclaration owner, String name) {
        for (PhpMethodDeclaration declared : workspace.symbols().methodDeclarations()) {
            if (declared.owner() == owner && name.equals(declared.name())) return declared;
        }
        return null;
    }

    public List<PhpMethodDeclaration> declarationsIn(PhpClassDeclaration owner) {
        var found = new ArrayList<PhpMethodDeclaration>();
        for (PhpMethodDeclaration declared : workspace.symbols().methodDeclarations()) {
            if (declared.owner() == owner) found.add(declared);
        }
        return found;
    }
}
