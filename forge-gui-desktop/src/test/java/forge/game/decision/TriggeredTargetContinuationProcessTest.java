package forge.game.decision;

import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

public class TriggeredTargetContinuationProcessTest {
    private static final List<String> EXPECTED_OUTPUT = List.of(
            "reason=UNSUPPORTED_ACTION_CONTINUATION",
            "provider_requests=0",
            "resolver_calls=0",
            "native_calls=0");

    @Test
    public void freshJvmRejectsTriggeredTargetContinuationBeforeDownstreamCallbacks() throws Exception {
        final Path temporaryDirectory = Files.createTempDirectory("frl02k-c2a-process-");
        final Path output = temporaryDirectory.resolve("child-output.txt");
        Process process = null;
        try {
            final List<String> command = List.of(
                    javaExecutable().toString(),
                    "-Djava.io.tmpdir=" + temporaryDirectory,
                    "-cp",
                    System.getProperty("java.class.path"),
                    TriggeredTargetContinuationChildMain.class.getName());
            process = new ProcessBuilder(command)
                    .directory(repositoryRoot().resolve("forge-gui").toFile())
                    .redirectErrorStream(true)
                    .redirectOutput(output.toFile())
                    .start();
            if (!process.waitFor(120, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
                fail("continuation child JVM timed out");
            }

            final List<String> childOutput = Files.readAllLines(output, StandardCharsets.UTF_8);
            assertEquals(process.exitValue(), 0, String.join(System.lineSeparator(), childOutput));
            assertEquals(childOutput, EXPECTED_OUTPUT);
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            }
            deleteTree(temporaryDirectory);
        }
    }

    private static Path javaExecutable() {
        final String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        final String executableName = osName.startsWith("windows") ? "java.exe" : "java";
        final Path executable = Path.of(System.getProperty("java.home"), "bin", executableName);
        if (!Files.isRegularFile(executable)) {
            throw new IllegalStateException("Java executable does not exist: " + executable);
        }
        return executable;
    }

    private static Path repositoryRoot() {
        final Path workingDirectory = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        return workingDirectory.getFileName().toString().equals("forge-gui-desktop")
                ? workingDirectory.getParent() : workingDirectory;
    }

    private static void deleteTree(final Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            for (final Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
