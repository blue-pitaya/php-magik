package dev.bluepitaya.phpmagik;

import org.jspecify.annotations.NullMarked;

import java.nio.file.FileSystems;
import java.nio.file.PathMatcher;
import java.util.List;

@NullMarked
public record Config(List<String> excludePatterns) {

    public static Config create() {
        return new Config(List.of(
                "**/.git/**",
                "**/vendor/**/{Tests,tests}/**",
                "**/vendor/**/vendor/**"
        ));
    }

    public List<PathMatcher> excludeMatchers() {
        return excludePatterns.stream()
                .map(pattern -> FileSystems.getDefault().getPathMatcher("glob:" + pattern))
                .toList();
    }
}
