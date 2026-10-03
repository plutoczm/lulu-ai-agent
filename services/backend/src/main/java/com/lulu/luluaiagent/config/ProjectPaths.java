package com.lulu.luluaiagent.config;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Resolves repository-level runtime paths independently of the backend working directory.
 */
public final class ProjectPaths {

    private static final Path ROOT = resolveRoot();

    private ProjectPaths() {
    }

    public static Path root() {
        return ROOT;
    }

    public static Path data(String first, String... more) {
        return ROOT.resolve("data").resolve(Path.of(first, more)).normalize();
    }

    public static Path temp() {
        return ROOT.resolve("tmp").normalize();
    }

    private static Path resolveRoot() {
        String configured = System.getenv("LULU_PROJECT_ROOT");
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured).toAbsolutePath().normalize();
        }

        Path cwd = Path.of(System.getProperty("user.dir"))
                .toAbsolutePath()
                .normalize();
        if (Files.exists(cwd.resolve(".git"))) {
            return cwd;
        }

        Path monorepoCandidate = cwd.resolve("..").resolve("..").normalize();
        if (Files.exists(monorepoCandidate.resolve(".git"))) {
            return monorepoCandidate;
        }

        return cwd;
    }
}
