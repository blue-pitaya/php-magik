package dev.bluepitaya.phpmagik.phpsymbol;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;

@NullMarked
public record PhpNameScope(@Nullable PhpNamespaceDefinition namespace, List<PhpImport> imports) {

    public static final PhpNameScope GLOBAL = new PhpNameScope(null, List.of());

    public String fqn(String name) {
        if (name.startsWith("\\")) {
            return name.substring(1);
        }
        if (name.regionMatches(true, 0, "namespace\\", 0, 10)) {
            return qualify(name.substring(10));
        }
        if ("self".equalsIgnoreCase(name) || "static".equalsIgnoreCase(name) || "parent".equalsIgnoreCase(name)) {
            return name;
        }

        int slash = name.indexOf('\\');
        String alias = slash < 0 ? name : name.substring(0, slash);
        for (PhpImport imported : imports) {
            if (imported.alias().equals(alias)) {
                return slash < 0 ? imported.name() : imported.name() + name.substring(slash);
            }
        }
        return qualify(name);
    }

    private String qualify(String name) {
        String namespaceName = namespace == null ? null : namespace.name();
        return namespaceName == null ? name : namespaceName + "\\" + name;
    }
}
