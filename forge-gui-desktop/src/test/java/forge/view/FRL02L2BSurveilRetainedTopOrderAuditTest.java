package forge.view;

import forge.game.decision.DecisionTraceRequestRecord;
import forge.game.decision.DecisionTraceResultKind;
import forge.game.decision.DeterminismTrace;
import forge.game.decision.DeterminismTraceHasher;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/** Fresh-JVM canonical acceptance gate for FRL-02L2B retained-top ordering. */
public class FRL02L2BSurveilRetainedTopOrderAuditTest {
    private static final String L2B_AUDIT_OUTPUT_PROPERTY =
            "forge.surveil.retained.top.order.audit.output";
    private static final String L2B_AUDIT_SCHEMA =
            "FRL02L2B_SURVEIL_RETAINED_TOP_ORDER_AUDIT_V1";
    private static final String L2B_PROFILE = "SURVEIL_RETAINED_TOP_ORDER";
    private static final String FIRST_DECK = "Izzet Guild Kit";
    private static final String SECOND_DECK = "Dimir Guild Kit";
    private static final int GAME_COUNT = 10;
    private static final long SEED = 20260810L;
    private static final int CHILD_TIMEOUT_SECONDS = 300;

    public static void main(final String[] args) throws Exception {
        if (args.length > 0 && "run-workload".equals(args[0])) {
            final String configuredOutput = System.getProperty(L2B_AUDIT_OUTPUT_PROPERTY, "");
            final String configuredTrace = System.getProperty(DeterminismTrace.OUTPUT_DIRECTORY_PROPERTY,
                    "");
            if (!configuredOutput.isBlank()) {
                if (configuredTrace.isBlank()) {
                    throw new AssertionError("L2B audit output requires a determinism trace directory");
                }
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    try {
                        writeAudit(Path.of(configuredOutput), readTrace(Path.of(configuredTrace)));
                    } catch (final Throwable failure) {
                        failure.printStackTrace();
                    }
                }, "frl02l2b-audit-writer"));
            }
            Main.main(Arrays.copyOfRange(args, 1, args.length));
        }
    }

    @Test(timeOut = 900_000L)
    public void canonicalWorkloadMeasuresFreshJvmL2BTrace() throws Exception {
        final Path run = Files.createTempDirectory("frl02l2b-audit-");
        final Path auditRoot = run.resolve("audit");
        final Path controlRoot = run.resolve("control");
        Files.createDirectories(auditRoot);
        Files.createDirectories(controlRoot);
        final Path auditOutput = auditRoot.resolve("l2b-audit.properties");
        final Path auditTrace = auditRoot.resolve("trace");
        final Path auditConsole = auditRoot.resolve("console.log");
        final Path controlTrace = controlRoot.resolve("trace");
        final Path controlConsole = controlRoot.resolve("console.log");
        boolean passed = false;
        try {
            final List<String> auditCommand = command(auditOutput, auditTrace, true);
            final List<String> controlCommand = command(null, controlTrace, false);
            assertChildConfiguration(auditCommand, controlCommand, auditTrace, controlTrace);
            runChild(auditCommand, auditConsole, "audit");
            runChild(controlCommand, controlConsole, "control");

            assertTrue(Files.isRegularFile(auditOutput), "missing L2B audit artifact: " + auditOutput);
            assertFalse(Files.exists(controlRoot.resolve("l2b-audit.properties")),
                    "control child unexpectedly received the L2B audit output option");
            final TraceSnapshot audit = readTrace(auditTrace);
            final TraceSnapshot control = readTrace(controlTrace);
            assertTraceTreesEqual(auditTrace, controlTrace, audit, control);
            assertExpectedWorkload(audit);
            assertExpectedWorkload(control);
            assertTraceSemanticsEqual(audit, control);
            assertNoPrivatePayload(audit, Files.readString(auditOutput, StandardCharsets.UTF_8));
            assertAuditArtifact(auditOutput, audit);
            passed = true;
        } catch (final Exception | AssertionError failure) {
            throw new AssertionError(withRetainedArtifacts(failure, run, auditConsole, controlConsole), failure);
        } finally {
            if (passed) {
                deleteTree(run);
            } else {
                System.err.println("FRL-02L2B audit artifacts retained at " + run);
            }
        }
    }

    private static List<String> command(final Path auditOutput, final Path trace, final boolean audit) {
        final List<String> workload = List.of("sim", "-d", FIRST_DECK, SECOND_DECK, "-n",
                Integer.toString(GAME_COUNT), "-s", Long.toString(SEED), "-q");
        final List<String> command = new ArrayList<>();
        command.add(ChildJvmSupport.javaExecutable().toString());
        if (audit) {
            command.add("-D" + L2B_AUDIT_OUTPUT_PROPERTY + "=" + auditOutput);
        }
        command.add("-D" + DeterminismTrace.OUTPUT_DIRECTORY_PROPERTY + "=" + trace);
        command.add("-D" + DeterminismTrace.AUDIT_RANDOM_PROPERTY + "=true");
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(FRL02L2BSurveilRetainedTopOrderAuditTest.class.getName());
        command.add("run-workload");
        command.addAll(workload);
        return command;
    }

    private static void assertChildConfiguration(final List<String> auditCommand,
            final List<String> controlCommand, final Path auditTrace, final Path controlTrace) {
        final String auditOption = "-D" + L2B_AUDIT_OUTPUT_PROPERTY + "=";
        assertTrue(auditCommand.stream().anyMatch(argument -> argument.startsWith(auditOption)));
        assertFalse(controlCommand.stream().anyMatch(argument -> argument.startsWith(auditOption)));
        assertTrue(auditCommand.contains("-D" + DeterminismTrace.AUDIT_RANDOM_PROPERTY + "=true"));
        assertTrue(controlCommand.contains("-D" + DeterminismTrace.AUDIT_RANDOM_PROPERTY + "=true"));
        assertEquals(auditCommand.get(auditCommand.indexOf("-cp") + 1),
                controlCommand.get(controlCommand.indexOf("-cp") + 1));
        assertEquals(auditCommand.subList(auditCommand.indexOf("run-workload"), auditCommand.size()),
                controlCommand.subList(controlCommand.indexOf("run-workload"), controlCommand.size()));
        assertFalse(auditTrace.toAbsolutePath().normalize().equals(controlTrace.toAbsolutePath().normalize()));
    }

    private static void runChild(final List<String> command, final Path console, final String label)
            throws Exception {
        final Process child;
        try {
            child = new ProcessBuilder(command)
                    .directory(repositoryRoot().resolve("forge-gui").toFile())
                    .redirectErrorStream(true)
                    .redirectOutput(console.toFile())
                    .start();
        } catch (final IOException startFailure) {
            throw new AssertionError(label + " child could not start; console=" + console, startFailure);
        }
        if (!child.waitFor(CHILD_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            child.destroyForcibly();
            child.waitFor(5, TimeUnit.SECONDS);
            throw new AssertionError(label + " child timed out; console=" + console
                    + "\n" + readIfPresent(console));
        }
        final String output = readIfPresent(console);
        assertEquals(child.exitValue(), 0,
                label + " child failed; console=" + console + "\n" + output);
    }

    private static TraceSnapshot readTrace(final Path root) throws IOException {
        assertTrue(Files.isDirectory(root), "missing trace directory: " + root);
        final List<Path> files = regularFiles(root);
        final List<Path> decisionFiles = files.stream()
                .filter(path -> path.getFileName().toString().endsWith(".decision.trace"))
                .toList();
        final List<Path> summaryFiles = files.stream()
                .filter(path -> path.getFileName().toString().endsWith(".summary.properties"))
                .toList();
        assertEquals(summaryFiles.size(), GAME_COUNT, "summary trace count for " + root);
        assertEquals(decisionFiles.size(), GAME_COUNT, "decision trace count for " + root);

        final List<GameTrace> games = new ArrayList<>();
        for (final Path decisionFile : decisionFiles) {
            games.add(parseGameTrace(decisionFile));
        }
        final List<String> decisionRows = games.stream()
                .flatMap(game -> game.rows().stream()).toList();
        final List<String> l2aRows = games.stream()
                .flatMap(game -> game.l2aRequests().stream()).map(TraceRequest::serialized).toList();
        final List<String> l2bRows = games.stream()
                .flatMap(game -> game.l2bRequests().stream()).map(TraceRequest::serialized).toList();
        final List<String> l2bCandidateHashes = games.stream()
                .flatMap(game -> game.l2bRequests().stream())
                .map(request -> request.record().getCandidateSetHash()).toList();
        final List<String> reconstructions = games.stream()
                .flatMap(game -> game.reconstructions().stream()).toList();
        return new TraceSnapshot(root, games, decisionRows, l2aRows, l2bRows, l2bCandidateHashes,
                reconstructions, hashTraceTree(root));
    }

    private static GameTrace parseGameTrace(final Path decisionFile) throws IOException {
        final List<String> rows = Files.readAllLines(decisionFile, StandardCharsets.UTF_8);
        assertFalse(rows.isEmpty(), "empty decision trace: " + decisionFile);
        final Map<Long, TraceRequest> requests = new LinkedHashMap<>();
        final Map<Long, TraceResult> results = new LinkedHashMap<>();
        for (final String row : rows) {
            assertTrue(row.startsWith(DeterminismTrace.DECISION_TRACE_VERSION + "|")
                            || row.startsWith(DeterminismTrace.DECISION_TRACE_V3 + "|"),
                    "unknown decision trace version in " + decisionFile + ": " + row);
            final String[] fields = row.split("\\|", -1);
            assertTrue(fields.length > 1, "malformed decision row in " + decisionFile + ": " + row);
            if ("REQUEST".equals(fields[1])) {
                final DecisionTraceRequestRecord record = DecisionTraceRequestRecord.fromSerializedRequest(row);
                final TraceRequest request = new TraceRequest(record, row);
                assertTrue(requests.put(record.getTraceRequestIndex(), request) == null,
                        "duplicate decision request index in " + decisionFile);
                assertCandidateSetHash(request, decisionFile);
            } else if ("RESULT".equals(fields[1])) {
                final TraceResult result = parseResult(fields, row, decisionFile);
                assertTrue(results.put(result.traceRequestIndex(), result) == null,
                        "duplicate decision result index in " + decisionFile);
            } else {
                throw new AssertionError("unknown decision row kind in " + decisionFile + ": " + row);
            }
        }
        assertEquals(results.keySet(), requests.keySet(), "request/result indexes in " + decisionFile);
        final List<SurveilSession> sessions = new ArrayList<>();
        final List<TraceRequest> l2aRows = new ArrayList<>();
        final List<TraceRequest> l2bRows = new ArrayList<>();
        final List<String> reconstructions = new ArrayList<>();
        SessionBuilder current = null;
        for (final TraceRequest request : requests.values()) {
            final DecisionTraceRequestRecord record = request.record();
            if (record.isSurveilRetainedTopOrderBearing()) {
                assertTrue(record.isSurveilRetainedTopOrderRequest(),
                        "malformed L2B-bearing request in " + decisionFile + ": " + request.serialized());
            }
            if (isSurveilPartitionRequest(record)) {
                if (record.getDecisionStepIndex() == 0) {
                    if (current != null) {
                        final SurveilSession session = current.build(results);
                        reconstructions.add(validateSurveilSession(session, decisionFile, sessions.size()));
                        sessions.add(session);
                    }
                    current = new SessionBuilder();
                }
                assertTrue(current != null, "L2A request without a session in " + decisionFile);
                current.l2aRequests.add(request);
                l2aRows.add(request);
            } else if (record.isSurveilRetainedTopOrderRequest()) {
                assertTrue(current != null, "L2B request without an L2A session in " + decisionFile);
                current.l2bRequests.add(request);
                l2bRows.add(request);
            }
        }
        if (current != null) {
            final SurveilSession session = current.build(results);
            reconstructions.add(validateSurveilSession(session, decisionFile, sessions.size()));
            sessions.add(session);
        }
        return new GameTrace(rows, l2aRows, l2bRows, sessions, reconstructions);
    }

    private static TraceResult parseResult(final String[] fields, final String row, final Path decisionFile) {
        assertEquals(fields.length, 10, "malformed result row in " + decisionFile + ": " + row);
        final DecisionTraceResultKind kind;
        try {
            kind = DecisionTraceResultKind.valueOf(fields[3]);
        } catch (final RuntimeException failure) {
            throw new AssertionError("malformed result kind in " + decisionFile + ": " + row, failure);
        }
        return new TraceResult(parseLong(fields[2], "result index", row), kind, decode(fields[4]),
                parseBoolean(fields[5], "native callback", row),
                parseBoolean(fields[6], "mapping attempted", row),
                parseBoolean(fields[7], "rollback", row),
                parseBoolean(fields[8], "forced bypass", row),
                parseBoolean(fields[9], "trace finalization", row));
    }

    private static String validateSurveilSession(final SurveilSession session, final Path decisionFile,
            final int sessionIndex) {
        final String label = decisionFile + " session " + sessionIndex;
        assertTrue(!session.l2aRequests().isEmpty(), "empty L2A session: " + label);
        for (int step = 0; step < session.l2aRequests().size(); step++) {
            final TraceRequest request = session.l2aRequests().get(step);
            final DecisionTraceRequestRecord record = request.record();
            assertEquals(record.getDecisionStepIndex(), step, "L2A step: " + label);
            assertEquals(record.getProfile(), DecisionTraceRequestRecord.Profile.SURVEIL_PARTITION,
                    "L2A profile: " + label);
            assertEquals(record.getTeacherLabelEligibility(),
                    forge.game.decision.DecisionTraceTeacherLabelEligibility.NOT_APPLICABLE,
                    "L2A eligibility: " + label);
            assertFalse(record.isForced(), "forced L2A request: " + label);
            final TraceResult result = session.results().get(record.getTraceRequestIndex());
            assertNativeChosen(result, record, label + " L2A step " + step);
            assertTrue(result.selectedCandidateSemanticKey().startsWith("SURVEIL_PARTITION|CLASSIFY_"),
                    "unexpected L2A result: " + label);
        }
        final int retainedCount = session.retainedCount();
        assertEquals(session.l2bRequests().size(), Math.max(0, retainedCount - 1),
                "R-1 L2B row count: " + label);
        final List<String> reconstruction = new ArrayList<>();
        List<String> remaining = null;
        for (int step = 0; step < session.l2bRequests().size(); step++) {
            final TraceRequest request = session.l2bRequests().get(step);
            final DecisionTraceRequestRecord record = request.record();
            assertEquals(record.getDecisionStepIndex(), step, "L2B step: " + label);
            assertEquals(record.getDecisionType(), forge.game.decision.DecisionType.ORDER,
                    "L2B type: " + label);
            assertEquals(record.getAdapterOrStage(), L2B_PROFILE, "L2B stage: " + label);
            assertEquals(record.getProfile(), DecisionTraceRequestRecord.Profile.SURVEIL_RETAINED_TOP_ORDER,
                    "L2B profile: " + label);
            assertEquals(record.getTeacherLabelEligibility(),
                    forge.game.decision.DecisionTraceTeacherLabelEligibility.NOT_APPLICABLE,
                    "L2B eligibility: " + label);
            assertFalse(record.isForced(), "forced L2B request: " + label);
            assertTrue(!record.getLegalCandidates().isEmpty(), "empty L2B candidates: " + label);
            assertTrue(record.getLegalCandidates().stream().allMatch(
                    FRL02L2BSurveilRetainedTopOrderAuditTest::isL2BCandidateKey),
                    "non-L2B candidate payload: " + label);
            if (step == 0) {
                remaining = new ArrayList<>(record.getLegalCandidates());
                assertEquals(remaining.size(), retainedCount, "initial L2B cardinality: " + label);
            }
            assertEquals(record.getLegalCandidates(), remaining, "L2B remaining order: " + label);
            final TraceResult result = session.results().get(record.getTraceRequestIndex());
            assertNativeChosen(result, record, label + " L2B step " + step);
            assertTrue(remaining.remove(result.selectedCandidateSemanticKey()),
                    "L2B result was not in remaining candidates: " + label);
            reconstruction.add(result.selectedCandidateSemanticKey());
        }
        if (retainedCount >= 2) {
            assertTrue(remaining != null, "missing L2B reconstruction: " + label);
            assertEquals(remaining.size(), 1, "R-1 remaining tail: " + label);
            reconstruction.add(remaining.get(0));
        } else {
            assertTrue(session.l2bRequests().isEmpty(), "unexpected L2B request for R<2: " + label);
        }
        return "retained=" + retainedCount + "|" + String.join(",", reconstruction);
    }

    private static boolean isSurveilPartitionRequest(final DecisionTraceRequestRecord record) {
        return record.getDecisionType() == forge.game.decision.DecisionType.CARD_SELECTION
                && record.getProfile() == DecisionTraceRequestRecord.Profile.SURVEIL_PARTITION
                && "SURVEIL_PARTITION".equals(record.getAdapterOrStage());
    }

    private static void assertNativeChosen(final TraceResult result,
            final DecisionTraceRequestRecord request, final String label) {
        assertTrue(result != null, "missing result: " + label);
        assertEquals(result.kind(), DecisionTraceResultKind.CHOSEN, "result kind: " + label);
        assertTrue(request.getLegalCandidates().contains(result.selectedCandidateSemanticKey()),
                "illegal result: " + label);
        assertTrue(result.nativeCallbackCompleted(), "non-native result: " + label);
        assertTrue(result.mappingAttempted(), "unmapped native result: " + label);
        assertFalse(result.engineRollbackObserved(), "rollback result: " + label);
        assertFalse(result.engineForcedBypass(), "forced bypass result: " + label);
        assertFalse(result.traceFinalization(), "finalization result: " + label);
    }

    private static boolean isL2BCandidateKey(final String value) {
        final String prefix = L2B_PROFILE + "|SELECT_NEXT_TOP|";
        if (!value.startsWith(prefix)) {
            return false;
        }
        try {
            Long.parseLong(value.substring(prefix.length()));
            return true;
        } catch (final NumberFormatException failure) {
            return false;
        }
    }

    private static void assertCandidateSetHash(final TraceRequest request, final Path decisionFile) {
        final List<String> candidates = request.record().getLegalCandidates();
        final String candidateList = candidates.stream()
                .map(FRL02L2BSurveilRetainedTopOrderAuditTest::canonicalListText)
                .collect(Collectors.joining(",", "[", "]"));
        final String expected = DeterminismTraceHasher.sha256(
                List.of("DECISION_CANDIDATE_SET_V1|" + candidateList));
        assertEquals(request.record().getCandidateSetHash(), expected,
                "candidate-set hash in " + decisionFile + " request "
                        + request.record().getTraceRequestIndex());
    }

    private static void assertExpectedWorkload(final TraceSnapshot snapshot) {
        assertEquals(snapshot.sessions().size(), 16, "Surveil sessions");
        assertEquals(countSessions(snapshot, 0), 0, "N=0 sessions");
        assertEquals(countSessions(snapshot, 1), 6, "N=1 sessions");
        assertEquals(countSessions(snapshot, 2), 10, "N=2 sessions");
        assertEquals(countSessionsAtLeast(snapshot, 3), 0, "N>=3 sessions");
        assertEquals(countRetained(snapshot, 0), 3, "retained=0 sessions");
        assertEquals(countRetained(snapshot, 1), 2, "retained=1 sessions");
        assertEquals(countRetained(snapshot, 2), 5, "retained=2 sessions");
        assertEquals(countRetainedAtLeast(snapshot, 3), 0, "retained>=3 sessions");
        assertEquals(snapshot.l2bRows().size(), 5, "actual L2B request rows must be exactly 5");
        assertEquals(countMeaningfulL2BOpportunities(snapshot), 5,
                "meaningful L2B opportunities derived from L2A");
    }

    private static long countSessions(final TraceSnapshot snapshot, final int size) {
        return snapshot.sessions().stream().filter(session -> session.l2aRequests().size() == size).count();
    }

    private static long countSessionsAtLeast(final TraceSnapshot snapshot, final int size) {
        return snapshot.sessions().stream().filter(session -> session.l2aRequests().size() >= size).count();
    }

    private static long countRetained(final TraceSnapshot snapshot, final int size) {
        return snapshot.sessions().stream()
                .filter(session -> session.l2aRequests().size() == 2)
                .filter(session -> session.retainedCount() == size)
                .count();
    }

    private static long countRetainedAtLeast(final TraceSnapshot snapshot, final int size) {
        return snapshot.sessions().stream()
                .filter(session -> session.l2aRequests().size() == 2)
                .filter(session -> session.retainedCount() >= size)
                .count();
    }

    private static long countMeaningfulL2BOpportunities(final TraceSnapshot snapshot) {
        return snapshot.sessions().stream()
                .filter(session -> session.l2aRequests().size() == 2)
                .filter(session -> session.retainedCount() >= 2)
                .count();
    }

    private static void assertTraceTreesEqual(final Path auditTrace, final Path controlTrace,
            final TraceSnapshot audit, final TraceSnapshot control) throws IOException {
        assertEquals(audit.traceHash(), control.traceHash(), "audit/control trace tree hash");
        final List<String> auditFiles = relativeFiles(auditTrace);
        final List<String> controlFiles = relativeFiles(controlTrace);
        assertEquals(auditFiles, controlFiles, "audit/control trace tree paths");
        for (final String relative : auditFiles) {
            assertEquals(Files.readAllBytes(auditTrace.resolve(relative)),
                    Files.readAllBytes(controlTrace.resolve(relative)),
                    "audit/control trace file " + relative);
        }
    }

    private static void assertTraceSemanticsEqual(final TraceSnapshot audit, final TraceSnapshot control) {
        assertEquals(audit.l2aRows(), control.l2aRows(), "L2A public request order");
        assertEquals(audit.l2bRows(), control.l2bRows(), "L2B request sequence");
        assertEquals(audit.l2bCandidateHashes(), control.l2bCandidateHashes(),
                "L2B candidate-set hashes");
        assertEquals(audit.reconstructions(), control.reconstructions(),
                "R-1 retained-order reconstruction");
        assertEquals(DeterminismTraceHasher.sha256(audit.l2bRows()),
                DeterminismTraceHasher.sha256(control.l2bRows()), "L2B sequence hash");
        assertEquals(DeterminismTraceHasher.sha256(audit.l2bCandidateHashes()),
                DeterminismTraceHasher.sha256(control.l2bCandidateHashes()),
                "L2B candidate-set hash sequence");
    }

    private static void assertNoPrivatePayload(final TraceSnapshot snapshot, final String auditText) {
        assertForbiddenAbsent(auditText, List.of("card", "cardview", "cardlki", "cardid", "gametimestamp",
                "nativeordinal", "nativeobject", "spellability", "library", "zonetype", "ownerid",
                "controllerid", "rng", "shuffle", "random", "heuristic", "top_first", "pair",
                "ownership", "teacher"));
        for (final String row : snapshot.decisionRows()) {
            assertForbiddenAbsent(row, List.of("cardview", "cardlki", "cardid", "gametimestamp",
                    "nativeordinal", "nativeobject", "spellability", "library", "zone", "zonetype", "ownerid",
                    "controllerid", "rng", "rngstate", "shuffle", "random", "heuristic", "top_first", "pair",
                    "ownership", "teacher"));
            assertNoSerializedEngineFields(row);
        }
        assertNoSerializedEngineFields(auditText);
        assertFalse(auditText.contains("FRL02L2A_SURVEIL_AUDIT_V1"));
        assertFalse(auditText.toLowerCase().contains("top_first"));
        assertTrue(snapshot.l2bRows().stream().allMatch(row -> !row.contains("TOP_FIRST")));
        assertTrue(snapshot.l2bRows().stream().allMatch(row -> !row.toLowerCase().contains("pair")));
        assertTrue(snapshot.l2bRows().stream().allMatch(row -> !row.toLowerCase().contains("teacher")));
    }

    private static void assertNoSerializedEngineFields(final String value) {
        for (final String marker : List.of("Card", "cardId", "gameTimestamp", "nativeOrdinal", "Zone", "RNG")) {
            assertFalse(value.contains(marker), "forbidden serialized engine field: " + marker);
        }
        final boolean hasAiToken = Arrays.stream(value.split("[|\\r\\n=,\\[\\]]"))
                .anyMatch("AI"::equals);
        assertFalse(hasAiToken, "forbidden serialized AI token");
    }

    private static void assertAuditArtifact(final Path output, final TraceSnapshot snapshot) throws IOException {
        final Properties properties = new Properties();
        try (var reader = Files.newBufferedReader(output, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        final Set<String> expectedKeys = new TreeSet<>(List.of(
                "schema", "profile", "workload_first_deck", "workload_second_deck", "games", "seed",
                "measured_surveil_sessions", "measured_n_bucket_0", "measured_n_bucket_1",
                "measured_n_bucket_2", "measured_n_bucket_ge3", "measured_retained_0",
                "measured_retained_1", "measured_retained_2", "measured_retained_ge3",
                "measured_actual_l2b_request_rows", "derived_meaningful_l2b_opportunities",
                "l2a_public_request_order_sha256", "l2b_request_sequence_sha256",
                "l2b_candidate_set_hash_sequence_sha256", "l2b_r_minus_one_reconstruction_sha256",
                "external_routing", "external_synthesis", "private_tail_order"));
        assertEquals(new TreeSet<>(properties.stringPropertyNames()), expectedKeys,
                "L2B audit schema keys");
        final Map<String, String> expected = Map.ofEntries(
                Map.entry("schema", L2B_AUDIT_SCHEMA),
                Map.entry("profile", L2B_PROFILE),
                Map.entry("workload_first_deck", FIRST_DECK),
                Map.entry("workload_second_deck", SECOND_DECK),
                Map.entry("games", Integer.toString(GAME_COUNT)),
                Map.entry("seed", Long.toString(SEED)),
                Map.entry("measured_surveil_sessions", "16"),
                Map.entry("measured_n_bucket_0", "0"),
                Map.entry("measured_n_bucket_1", "6"),
                Map.entry("measured_n_bucket_2", "10"),
                Map.entry("measured_n_bucket_ge3", "0"),
                Map.entry("measured_retained_0", "3"),
                Map.entry("measured_retained_1", "2"),
                Map.entry("measured_retained_2", "5"),
                Map.entry("measured_retained_ge3", "0"),
                Map.entry("measured_actual_l2b_request_rows", "5"),
                Map.entry("derived_meaningful_l2b_opportunities", "5"),
                Map.entry("l2a_public_request_order_sha256", DeterminismTraceHasher.sha256(snapshot.l2aRows())),
                Map.entry("l2b_request_sequence_sha256", DeterminismTraceHasher.sha256(snapshot.l2bRows())),
                Map.entry("l2b_candidate_set_hash_sequence_sha256",
                        DeterminismTraceHasher.sha256(snapshot.l2bCandidateHashes())),
                Map.entry("l2b_r_minus_one_reconstruction_sha256",
                        DeterminismTraceHasher.sha256(snapshot.reconstructions())),
                Map.entry("external_routing", "UNPROVEN"),
                Map.entry("external_synthesis", "UNPROVEN"),
                Map.entry("private_tail_order", "DEFERRED"));
        for (final Map.Entry<String, String> entry : expected.entrySet()) {
            assertEquals(properties.getProperty(entry.getKey()), entry.getValue(), entry.getKey());
        }
        assertNoPrivatePayload(snapshot, Files.readString(output, StandardCharsets.UTF_8));
    }

    private static void writeAudit(final Path output, final TraceSnapshot snapshot) throws IOException {
        assertExpectedWorkload(snapshot);
        final List<String> lines = List.of(
                "schema=" + L2B_AUDIT_SCHEMA,
                "profile=" + L2B_PROFILE,
                "workload_first_deck=" + FIRST_DECK,
                "workload_second_deck=" + SECOND_DECK,
                "games=" + GAME_COUNT,
                "seed=" + SEED,
                "measured_surveil_sessions=" + snapshot.sessions().size(),
                "measured_n_bucket_0=" + countSessions(snapshot, 0),
                "measured_n_bucket_1=" + countSessions(snapshot, 1),
                "measured_n_bucket_2=" + countSessions(snapshot, 2),
                "measured_n_bucket_ge3=" + countSessionsAtLeast(snapshot, 3),
                "measured_retained_0=" + countRetained(snapshot, 0),
                "measured_retained_1=" + countRetained(snapshot, 1),
                "measured_retained_2=" + countRetained(snapshot, 2),
                "measured_retained_ge3=" + countRetainedAtLeast(snapshot, 3),
                "measured_actual_l2b_request_rows=" + snapshot.l2bRows().size(),
                "derived_meaningful_l2b_opportunities=" + countMeaningfulL2BOpportunities(snapshot),
                "l2a_public_request_order_sha256=" + DeterminismTraceHasher.sha256(snapshot.l2aRows()),
                "l2b_request_sequence_sha256=" + DeterminismTraceHasher.sha256(snapshot.l2bRows()),
                "l2b_candidate_set_hash_sequence_sha256="
                        + DeterminismTraceHasher.sha256(snapshot.l2bCandidateHashes()),
                "l2b_r_minus_one_reconstruction_sha256="
                        + DeterminismTraceHasher.sha256(snapshot.reconstructions()),
                "external_routing=UNPROVEN",
                "external_synthesis=UNPROVEN",
                "private_tail_order=DEFERRED");
        if (output.getParent() != null) {
            Files.createDirectories(output.getParent());
        }
        Files.write(output, lines, StandardCharsets.UTF_8);
    }

    private static void assertForbiddenAbsent(final String value, final List<String> markers) {
        final String normalized = value.toLowerCase();
        for (final String marker : markers) {
            assertFalse(normalized.contains(marker), "forbidden private marker: " + marker + " in " + value);
        }
    }

    private static List<Path> regularFiles(final Path root) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(path -> root.relativize(path).toString()))
                    .toList();
        }
    }

    private static List<String> relativeFiles(final Path root) throws IOException {
        return regularFiles(root).stream()
                .map(path -> root.relativize(path).toString().replace('\\', '/')).toList();
    }

    private static String hashTraceTree(final Path root) throws IOException {
        final MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (final NoSuchAlgorithmException failure) {
            throw new IOException("SHA-256 is unavailable", failure);
        }
        for (final Path file : regularFiles(root)) {
            digest.update(root.relativize(file).toString().replace('\\', '/')
                    .getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(Files.readAllBytes(file));
            digest.update((byte) 0);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static long parseLong(final String value, final String label, final String row) {
        try {
            return Long.parseLong(value);
        } catch (final NumberFormatException failure) {
            throw new AssertionError("malformed " + label + " in " + row, failure);
        }
    }

    private static boolean parseBoolean(final String value, final String label, final String row) {
        if (!"true".equals(value) && !"false".equals(value)) {
            throw new AssertionError("malformed " + label + " in " + row);
        }
        return Boolean.parseBoolean(value);
    }

    private static String canonicalListText(final String value) {
        return canonicalText(value).replace(",", "%2C").replace("[", "%5B").replace("]", "%5D");
    }

    private static String canonicalText(final String value) {
        return value.replace("%", "%25").replace("|", "%7C")
                .replace("\r", "%0D").replace("\n", "%0A");
    }

    private static String decode(final String value) {
        return value.replace("%0D", "\r").replace("%0A", "\n").replace("%7C", "|")
                .replace("%2C", ",").replace("%5B", "[").replace("%5D", "]").replace("%25", "%");
    }

    private static String readIfPresent(final Path file) {
        try {
            return Files.exists(file) ? Files.readString(file, StandardCharsets.UTF_8) : "<missing>";
        } catch (final IOException failure) {
            return "<unreadable: " + failure + ">";
        }
    }

    private static String withRetainedArtifacts(final Throwable failure, final Path run,
            final Path auditConsole, final Path controlConsole) {
        return String.valueOf(failure.getMessage()) + "\nFRL-02L2B artifacts retained at " + run
                + "\n--- audit child output (" + auditConsole + ") ---\n" + readIfPresent(auditConsole)
                + "\n--- control child output (" + controlConsole + ") ---\n" + readIfPresent(controlConsole);
    }

    private static Path repositoryRoot() {
        final Path workingDirectory = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        return workingDirectory.getFileName().toString().equals("forge-gui-desktop")
                ? workingDirectory.getParent() : workingDirectory;
    }

    private static void deleteTree(final Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            for (final Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private record TraceRequest(DecisionTraceRequestRecord record, String serialized) {
    }

    private record TraceResult(long traceRequestIndex, DecisionTraceResultKind kind,
            String selectedCandidateSemanticKey, boolean nativeCallbackCompleted, boolean mappingAttempted,
            boolean engineRollbackObserved, boolean engineForcedBypass, boolean traceFinalization) {
    }

    private record SurveilSession(List<TraceRequest> l2aRequests, List<TraceRequest> l2bRequests,
            Map<Long, TraceResult> results) {
        private int retainedCount() {
            return (int) l2aRequests.stream()
                    .map(request -> results.get(request.record().getTraceRequestIndex()))
                    .filter(result -> result.selectedCandidateSemanticKey()
                            .startsWith("SURVEIL_PARTITION|CLASSIFY_RETAIN|"))
                    .count();
        }
    }

    private record GameTrace(List<String> rows, List<TraceRequest> l2aRequests, List<TraceRequest> l2bRequests,
            List<SurveilSession> sessions, List<String> reconstructions) {
    }

    private record TraceSnapshot(Path root, List<GameTrace> games, List<String> decisionRows,
            List<String> l2aRows, List<String> l2bRows, List<String> l2bCandidateHashes,
            List<String> reconstructions, String traceHash) {
        private List<SurveilSession> sessions() {
            return games.stream().flatMap(game -> game.sessions().stream()).toList();
        }
    }

    private static final class SessionBuilder {
        private final List<TraceRequest> l2aRequests = new ArrayList<>();
        private final List<TraceRequest> l2bRequests = new ArrayList<>();

        private SurveilSession build(final Map<Long, TraceResult> results) {
            return new SurveilSession(List.copyOf(l2aRequests), List.copyOf(l2bRequests), Map.copyOf(results));
        }
    }
}
