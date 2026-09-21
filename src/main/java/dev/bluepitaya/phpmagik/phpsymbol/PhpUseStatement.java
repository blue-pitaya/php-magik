package dev.bluepitaya.phpmagik.phpsymbol;

import dev.bluepitaya.phpmagik.ts.Range;

/**
 * One name imported into a file: {@code use App\NSA\A;} or a single clause of
 * {@code use App\{NSA\A, NSB\B};}.
 *
 * @param alias the name the file refers to it by - the {@code as} alias when
 * there is one, else the last segment of {@code fqn}
 * @param fqn fully qualified, with no leading {@code \}
 * @param range spans the clause, so the path and its alias together
 */
public record PhpUseStatement(String alias, String fqn, UseKind kind, Range range, int fileId)
        implements PhpSymbol {
}
