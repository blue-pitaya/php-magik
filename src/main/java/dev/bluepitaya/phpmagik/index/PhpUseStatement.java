package dev.bluepitaya.phpmagik.index;

/**
 * One name imported into a file: {@code use App\NSA\A;} or a single clause of
 * {@code use App\{NSA\A, NSB\B};}.
 *
 * @param alias the name the file refers to it by - the {@code as} alias when
 * there is one, else the last segment of {@code fqn}
 * @param fqn fully qualified, with no leading {@code \}
 */
public record PhpUseStatement(String alias, String fqn, UseKind kind) {
}
