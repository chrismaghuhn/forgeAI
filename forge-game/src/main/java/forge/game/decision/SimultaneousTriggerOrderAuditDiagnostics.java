package forge.game.decision;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Disabled-by-default, value-only counters for the FRL-02L1 canonical gate. */
public final class SimultaneousTriggerOrderAuditDiagnostics {
    public static final String OUTPUT_FILE_PROPERTY = "forge.simultaneousTriggerOrder.auditFile";

    private static final Path OUTPUT = outputPath();
    private static final boolean ENABLED = OUTPUT != null;
    private static long total;
    private static long n0;
    private static long n1;
    private static long n2;
    private static long n3;
    private static long n4;
    private static long nOther;
    private static long strategicSessions;
    private static long admittedSessions;
    private static long orderRequests;
    private static long candidateSize2;
    private static long candidateSize3;
    private static long candidateSize4;
    private static long forcedRequests;
    private static long unsupportedFallbacks;
    private static long integrityFailures;
    private static long unsupportedFailures;
    private static long invalidExternalCandidates;
    private static long nativeCallbackFailures;
    private static long mappingFailures;
    private static final Map<String, Long> admissionRejections = new TreeMap<>();

    static {
        if (ENABLED) {
            Runtime.getRuntime().addShutdownHook(new Thread(
                    SimultaneousTriggerOrderAuditDiagnostics::write, "forge-order-l1-audit"));
        }
    }

    private SimultaneousTriggerOrderAuditDiagnostics() {
    }

    public static synchronized void recordRawInvocation(final int size) {
        if (!ENABLED) {
            return;
        }
        total++;
        switch (size) {
        case 0:
            n0++;
            break;
        case 1:
            n1++;
            break;
        case 2:
            n2++;
            break;
        case 3:
            n3++;
            break;
        case 4:
            n4++;
            break;
        default:
            nOther++;
            break;
        }
    }

    public static synchronized void recordStrategicAdmission(final boolean admitted) {
        if (ENABLED) {
            strategicSessions++;
            if (admitted) {
                admittedSessions++;
            }
        }
    }

    public static synchronized void recordUnsupportedFallback() {
        if (ENABLED) {
            unsupportedFallbacks++;
        }
    }

    public static synchronized void recordIntegrityFailure() {
        if (ENABLED) {
            integrityFailures++;
        }
    }

    public static synchronized void recordUnsupportedFailure() {
        if (ENABLED) {
            unsupportedFailures++;
        }
    }

    public static synchronized void recordInvalidExternalCandidate() {
        if (ENABLED) {
            invalidExternalCandidates++;
        }
    }

    public static synchronized void recordNativeCallbackFailure() {
        if (ENABLED) {
            nativeCallbackFailures++;
        }
    }

    public static synchronized void recordMappingFailure() {
        if (ENABLED) {
            mappingFailures++;
        }
    }

    public static synchronized void recordAdmissionRejection(final String reason) {
        if (ENABLED) {
            admissionRejections.merge(reason, 1L, Long::sum);
        }
    }

    public static synchronized void recordRequest(final int candidateCount, final boolean forced) {
        if (!ENABLED) {
            return;
        }
        orderRequests++;
        if (candidateCount == 2) {
            candidateSize2++;
        } else if (candidateCount == 3) {
            candidateSize3++;
        } else if (candidateCount == 4) {
            candidateSize4++;
        }
        if (forced) {
            forcedRequests++;
        }
    }

    private static Path outputPath() {
        final String value = System.getProperty(OUTPUT_FILE_PROPERTY, "");
        return value.isBlank() ? null : Path.of(value);
    }

    private static synchronized void write() {
        if (!ENABLED) {
            return;
        }
        final List<String> lines = new ArrayList<>();
        lines.add("version=FRL_02L1_ORDER_AUDIT_V1");
        lines.add("orderSimultaneousSa.total=" + total);
        lines.add("orderSimultaneousSa.n0=" + n0);
        lines.add("orderSimultaneousSa.n1=" + n1);
        lines.add("orderSimultaneousSa.n2=" + n2);
        lines.add("orderSimultaneousSa.n3=" + n3);
        lines.add("orderSimultaneousSa.n4=" + n4);
        lines.add("orderSimultaneousSa.nOther=" + nOther);
        lines.add("strategicSessions=" + strategicSessions);
        lines.add("admittedStrategicSessions=" + admittedSessions);
        lines.add("orderRequests=" + orderRequests);
        lines.add("candidateSize2=" + candidateSize2);
        lines.add("candidateSize3=" + candidateSize3);
        lines.add("candidateSize4=" + candidateSize4);
        lines.add("forcedRequests=" + forcedRequests);
        lines.add("unsupportedFallbacks=" + unsupportedFallbacks);
        lines.add("integrityFailures=" + integrityFailures);
        lines.add("unsupportedFailures=" + unsupportedFailures);
        lines.add("invalidExternalCandidates=" + invalidExternalCandidates);
        lines.add("nativeCallbackFailures=" + nativeCallbackFailures);
        lines.add("mappingFailures=" + mappingFailures);
        for (final Map.Entry<String, Long> entry : admissionRejections.entrySet()) {
            lines.add("admissionReject." + entry.getKey() + "=" + entry.getValue());
        }
        try {
            final Path parent = OUTPUT.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(OUTPUT, lines, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        } catch (final IOException ignored) {
            // Diagnostics must never change the engine callback or shutdown result.
        }
    }
}
