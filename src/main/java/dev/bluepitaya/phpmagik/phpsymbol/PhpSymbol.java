package dev.bluepitaya.phpmagik.phpsymbol;

import dev.bluepitaya.phpmagik.ts.Range;

/**
 * Anything the index records, with the source it was written at.
 *
 * <p>Every kind that can be both declared and used is recorded as two types,
 * paired by one of the resolvers: {@code VariableResolver},
 * {@code FunctionResolver}, {@code MethodResolver}, {@code PropertyResolver},
 * {@code ClassResolver}.
 */
public sealed interface PhpSymbol permits
        PhpVarDefinition, PhpVarUsage,
        PhpFunctionDefinition, PhpFunctionUsage,
        PhpMethodDefinition, PhpMethodUsage,
        PhpPropertyDefinition, PhpPropertyUsage,
        PhpClassDefinition, PhpClassUsage,
        PhpUseStatement {

    Range range();

    int fileId();
}
