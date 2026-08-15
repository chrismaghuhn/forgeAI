package forge.game.decision;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.player.Player;
import forge.game.zone.ZoneType;
import forge.util.DeterminismAuditRandom;
import org.testng.annotations.Test;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertNull;

public class SurveilRetainedTopOrderTraceTest extends AITest {
    private static final long HIGH_OPAQUE_ITEM_ID = 9_000_000_001L;
    private static final long LOW_OPAQUE_ITEM_ID = 7L;

    @Test
    public void isolatedL2BRequestUsesV3WithoutSerializingTopFirst() throws Exception {
        final Fixture fixture = fixture();
        final DeterminismTrace trace = DeterminismTrace.attach(fixture.game, 0,
                new DeterminismAuditRandom(20260814L), fixture.directory);
        try {
            final DecisionRequest request = l2bRequest(fixture.player);
            final DeterminismTrace.RequestHandle handle = DeterminismTrace.recordRequest(fixture.game,
                    fixture.player.getId(), request, "SURVEIL_RETAINED_TOP_ORDER", 0,
                    DecisionTraceRequestRecord.Profile.valueOf("SURVEIL_RETAINED_TOP_ORDER"),
                    DecisionTraceTeacherLabelEligibility.NOT_APPLICABLE);
            handle.recordNativeMappedResult(request.getCandidates().get(0));
            trace.finish();

            final List<String> records = Files.readAllLines(
                    fixture.directory.resolve("game-001.decision.trace"), StandardCharsets.UTF_8);
            assertEquals(records.size(), 2);
            assertTrue(records.stream().allMatch(record -> record.startsWith("DECISION_TRACE_V3|")));
            assertTrue(records.stream().noneMatch(record -> record.contains("TOP_FIRST")));
            assertNoPrivateL2BInformation(records);
            final DecisionTraceRequestRecord parsed =
                    DecisionTraceRequestRecord.fromSerializedRequest(records.get(0));
            assertTrue(parsed.isSurveilRetainedTopOrderRequest());
            assertTrue(parsed.isSurveilRetainedTopOrderBearing());
            assertEquals(parsed.getProfile(), DecisionTraceRequestRecord.Profile.SURVEIL_RETAINED_TOP_ORDER);
            assertEquals(parsed.getTeacherLabelEligibility(),
                    DecisionTraceTeacherLabelEligibility.NOT_APPLICABLE);
            assertEquals(parsed.getLegalCandidates(), List.of(
                    "SURVEIL_RETAINED_TOP_ORDER|SELECT_NEXT_TOP|" + HIGH_OPAQUE_ITEM_ID,
                    "SURVEIL_RETAINED_TOP_ORDER|SELECT_NEXT_TOP|" + LOW_OPAQUE_ITEM_ID));
            assertTrue(records.get(1).contains("SURVEIL_RETAINED_TOP_ORDER%7CSELECT_NEXT_TOP%7C"
                    + HIGH_OPAQUE_ITEM_ID));
        } finally {
            trace.finish();
            delete(fixture.directory);
        }
    }

    @Test
    public void retainedTopOrderDiagnosticsDoNotAddPrivateOrderOrEngineState() throws Exception {
        final Method approvedCounterKeys = SurveilPartitionDiagnostics.class
                .getDeclaredMethod("approvedCounterKeys");
        approvedCounterKeys.setAccessible(true);
        @SuppressWarnings("unchecked")
        final List<String> keys = (List<String>) approvedCounterKeys.invoke(null);

        assertFalse(keys.stream().anyMatch(key -> {
            final String normalized = key.toLowerCase(Locale.ROOT);
            return normalized.contains("pair_left") || normalized.contains("pair_right")
                    || normalized.contains("insertion_order")
                    || normalized.contains("surveil_graveyard_insertion_order")
                    || normalized.contains("full_surveil_ownership")
                    || normalized.contains("card_id") || normalized.contains("game_timestamp")
                    || normalized.contains("native_ordinal") || normalized.contains("library")
                    || normalized.contains("zone") || normalized.contains("heuristic")
                    || normalized.contains("rng") || normalized.contains("shuffle")
                    || normalized.contains("random");
        }));
        assertFalse(keys.contains("retained_order_request_count"));
        assertFalse(keys.contains("retained_order_result_count"));
    }

    @Test
    public void v2StageTextDoesNotBecomeAnL2BRequest() {
        final DecisionTraceRequestRecord request = DecisionTraceRequestRecord.fromSerializedRequest(
                "DECISION_TRACE_V2|REQUEST|7|0|MAIN|1|ORDER|SURVEIL_RETAINED_TOP_ORDER|0|false|"
                        + "[SURVEIL_RETAINED_TOP_ORDER%7CSELECT_NEXT_TOP%7C1]|hash");

        assertEquals(request.getProfile(), DecisionTraceRequestRecord.Profile.OTHER);
        assertFalse(request.isSurveilRetainedTopOrderRequest());
        assertFalse(DecisionTraceTrainingValidator.isHistoryValid(request,
                result(request, DecisionTraceResultKind.CHOSEN,
                        "SURVEIL_RETAINED_TOP_ORDER|SELECT_NEXT_TOP|1", true, true)));
    }

    @Test
    public void unknownV3ProfileValueRemainsFailClosedForL2B() {
        final DecisionTraceRequestRecord request = DecisionTraceRequestRecord.fromSerializedRequest(
                "DECISION_TRACE_V3|REQUEST|14|0|MAIN|1|ORDER|SURVEIL_RETAINED_TOP_ORDER|0|false|"
                        + "[SURVEIL_RETAINED_TOP_ORDER%7CSELECT_NEXT_TOP%7C1]|hash|UNKNOWN_PROFILE|NOT_APPLICABLE");

        assertNull(request.getProfile());
        assertTrue(request.isSurveilRetainedTopOrderBearing());
        assertFalse(request.isSurveilRetainedTopOrderRequest());
        assertFalse(DecisionTraceTrainingValidator.isHistoryValid(request,
                result(request, DecisionTraceResultKind.CHOSEN,
                        "SURVEIL_RETAINED_TOP_ORDER|SELECT_NEXT_TOP|1", true, true)));
    }

    @Test
    public void l2bHistoryUsesOnlyTheTypedV3TruthTable() {
        final DecisionTraceRequestRecord request = l2bRequestRecord(
                "SURVEIL_RETAINED_TOP_ORDER", "SURVEIL_RETAINED_TOP_ORDER");

        assertTrue(DecisionTraceTrainingValidator.isHistoryValid(request,
                result(request, DecisionTraceResultKind.CHOSEN,
                        "SURVEIL_RETAINED_TOP_ORDER|SELECT_NEXT_TOP|1", true, true)));
        assertTrue(DecisionTraceTrainingValidator.isHistoryValid(request,
                result(request, DecisionTraceResultKind.CHOSEN,
                        "SURVEIL_RETAINED_TOP_ORDER|SELECT_NEXT_TOP|1", false, false)));
        assertTrue(DecisionTraceTrainingValidator.isHistoryValid(request,
                result(request, DecisionTraceResultKind.MAPPING_FAILED, "", true, true)));
        assertTrue(DecisionTraceTrainingValidator.isHistoryValid(request,
                result(request, DecisionTraceResultKind.UNOBSERVED, "", true, false)));
        assertTrue(DecisionTraceTrainingValidator.isHistoryValid(request,
                result(request, DecisionTraceResultKind.INVALID_EXTERNAL_CANDIDATE, "", false, false)));
        assertFalse(DecisionTraceTrainingValidator.isHistoryValid(request,
                result(request, DecisionTraceResultKind.NATIVE_CALLBACK_FAILURE, "", false, false)));
        assertFalse(DecisionTraceTrainingValidator.isBCPolicySample(request,
                result(request, DecisionTraceResultKind.CHOSEN,
                        "SURVEIL_RETAINED_TOP_ORDER|SELECT_NEXT_TOP|1", true, true)));

        final DecisionTraceRequestRecord forced = DecisionTraceRequestRecord.fromSerializedRequest(
                serializedL2bRequest("SURVEIL_RETAINED_TOP_ORDER", "SURVEIL_RETAINED_TOP_ORDER", true)
                        .replace("|0|false|", "|0|true|"));
        assertFalse(DecisionTraceTrainingValidator.isHistoryValid(forced,
                result(forced, DecisionTraceResultKind.MAPPING_FAILED, "", true, true)));
    }

    @Test
    public void malformedL2BMetadataFailsBeforeGenericOrderValidation() {
        final List<DecisionTraceRequestRecord> malformed = List.of(
                l2bRequestRecord("SURVEIL_RETAINED_TOP_ORDER", "OTHER"),
                l2bRequestRecord("OTHER", "SURVEIL_RETAINED_TOP_ORDER"),
                DecisionTraceRequestRecord.fromSerializedRequest(
                        serializedL2bRequest("", "SURVEIL_RETAINED_TOP_ORDER", false)));
        for (final DecisionTraceRequestRecord request : malformed) {
            assertTrue(request.isSurveilRetainedTopOrderBearing());
            assertFalse(request.isSurveilRetainedTopOrderRequest());
            assertFalse(DecisionTraceTrainingValidator.isHistoryValid(request,
                    result(request, DecisionTraceResultKind.CHOSEN,
                            "SURVEIL_RETAINED_TOP_ORDER|SELECT_NEXT_TOP|1", true, true)));
            assertFalse(DecisionTraceTrainingValidator.isHistoryValid(request,
                    result(request, DecisionTraceResultKind.CHOSEN,
                            "SURVEIL_RETAINED_TOP_ORDER|SELECT_NEXT_TOP|1", false, false)));
            assertFalse(DecisionTraceTrainingValidator.isBCPolicySample(request,
                    result(request, DecisionTraceResultKind.CHOSEN,
                            "SURVEIL_RETAINED_TOP_ORDER|SELECT_NEXT_TOP|1", true, true)));
        }
    }

    @Test
    public void genericCardSelectionAndOrderRemainOutsideSurveilHistory() {
        final DecisionTraceRequestRecord cardSelection = DecisionTraceRequestRecord.fromSerializedRequest(
                "DECISION_TRACE_V3|REQUEST|10|0|MAIN|1|CARD_SELECTION|OTHER|0|false|"
                        + "[CARD_SELECTION%7C1]|hash|OTHER|NOT_APPLICABLE");
        final DecisionTraceRequestRecord order = DecisionTraceRequestRecord.fromSerializedRequest(
                "DECISION_TRACE_V3|REQUEST|11|0|MAIN|1|ORDER|OTHER|0|false|"
                        + "[ORDER%7C1]|hash|OTHER|NOT_APPLICABLE");

        assertFalse(cardSelection.isSurveilRetainedTopOrderBearing());
        assertFalse(order.isSurveilRetainedTopOrderBearing());
        assertFalse(DecisionTraceTrainingValidator.isHistoryValid(cardSelection,
                result(cardSelection, DecisionTraceResultKind.CHOSEN, "CARD_SELECTION|1", false, false)));
        assertTrue(DecisionTraceTrainingValidator.isHistoryValid(order,
                result(order, DecisionTraceResultKind.CHOSEN, "ORDER|1", false, false)));
    }

    @Test
    public void externalL2AHistoryRequiresExactTypedFlagsAndTerminalShape() {
        final DecisionTraceRequestRecord request = DecisionTraceRequestRecord.fromSerializedRequest(
                "DECISION_TRACE_V3|REQUEST|12|0|MAIN|1|CARD_SELECTION|SURVEIL_PARTITION|0|false|"
                        + "[SURVEIL_PARTITION%7CCLASSIFY_RETAIN%7C1]|hash|SURVEIL_PARTITION|NOT_APPLICABLE");
        final String legal = "SURVEIL_PARTITION|CLASSIFY_RETAIN|1";

        assertTrue(DecisionTraceTrainingValidator.isHistoryValid(request,
                result(request, DecisionTraceResultKind.CHOSEN, legal, false, false)));
        assertFalse(DecisionTraceTrainingValidator.isHistoryValid(request,
                result(request, DecisionTraceResultKind.CHOSEN, legal, true, false)));
        assertFalse(DecisionTraceTrainingValidator.isHistoryValid(request,
                result(request, DecisionTraceResultKind.CHOSEN, legal, false, true)));
        assertTrue(DecisionTraceTrainingValidator.isHistoryValid(request,
                result(request, DecisionTraceResultKind.INVALID_EXTERNAL_CANDIDATE, "", false, false)));
        assertFalse(DecisionTraceTrainingValidator.isHistoryValid(request,
                result(request, DecisionTraceResultKind.INVALID_EXTERNAL_CANDIDATE, legal, false, false)));
        final DecisionTraceRequestRecord forced = DecisionTraceRequestRecord.fromSerializedRequest(
                "DECISION_TRACE_V3|REQUEST|13|0|MAIN|1|CARD_SELECTION|SURVEIL_PARTITION|0|true|"
                        + "[SURVEIL_PARTITION%7CCLASSIFY_RETAIN%7C1]|hash|SURVEIL_PARTITION|NOT_APPLICABLE");
        assertFalse(DecisionTraceTrainingValidator.isHistoryValid(forced,
                result(forced, DecisionTraceResultKind.INVALID_EXTERNAL_CANDIDATE, "", false, false)));
    }

    @Test
    public void externalResolverNullExceptionAndAbsenceAfterHandleUseInvalidCandidate() throws Exception {
        for (final ResolverFailure failure : ResolverFailure.values()) {
            final Fixture fixture = fixtureWithCards();
            final SurveilPartitionDecisionProvider provider = new SurveilPartitionDecisionProvider();
            provider.setOwner(SurveilPartitionOwner.EXTERNAL);
            final AtomicInteger membershipCalls = new AtomicInteger();
            if (failure != ResolverFailure.ABSENCE) {
                provider.setResolver(request -> {
                    if (request.getDecisionType() == DecisionType.CARD_SELECTION) {
                        membershipCalls.incrementAndGet();
                        return request.getCandidates().stream()
                                .filter(candidate -> candidate.getSurveilPartitionCandidateKind()
                                        == SurveilPartitionCandidateKind.CLASSIFY_RETAIN)
                        .findFirst().orElseThrow();
                    }
                    if (failure == ResolverFailure.EXCEPTION) {
                        throw new IllegalStateException("resolver failure");
                    }
                    return null;
                });
            } else {
                provider.setResolver(request -> {
                    if (request.getDecisionType() != DecisionType.CARD_SELECTION) {
                        return null;
                    }
                    final LegalCandidate selected = request.getCandidates().stream()
                            .filter(candidate -> candidate.getSurveilPartitionCandidateKind()
                                    == SurveilPartitionCandidateKind.CLASSIFY_RETAIN)
                            .findFirst().orElseThrow();
                    if (membershipCalls.incrementAndGet() == 3) {
                        provider.setResolver(null);
                    }
                    return selected;
                });
            }

            final DeterminismTrace trace = DeterminismTrace.attach(fixture.game, 0,
                    new DeterminismAuditRandom(20260814L), fixture.directory);
            try {
                assertThrows(RuntimeException.class, () -> new SurveilPartitionDecisionCoordinator(provider)
                        .captureNativeSurveil(fixture.player, new CardCollection(fixture.cards), ignored -> {
                            throw new AssertionError("EXTERNAL must not invoke native arrangeForSurveil");
                        }));
                trace.finish();

                final List<String> records = Files.readAllLines(
                        fixture.directory.resolve("game-001.decision.trace"), StandardCharsets.UTF_8);
                assertTrue(records.stream().allMatch(record -> record.startsWith("DECISION_TRACE_V3|")));
                assertTrue(records.stream().anyMatch(record -> record.contains("|ORDER|SURVEIL_RETAINED_TOP_ORDER|")));
                assertTrue(records.stream().anyMatch(record -> record.contains(
                        "|INVALID_EXTERNAL_CANDIDATE||false|false|")));
                assertFalse(records.stream().anyMatch(record -> record.contains("|NATIVE_CALLBACK_FAILURE|")));
            } finally {
                trace.finish();
                delete(fixture.directory);
            }
        }
    }

    @Test
    public void externalL2AHistoryRemainsNarrowlySupported() {
        final DecisionTraceRequestRecord request = DecisionTraceRequestRecord.fromSerializedRequest(
                "DECISION_TRACE_V3|REQUEST|8|0|MAIN|1|CARD_SELECTION|SURVEIL_PARTITION|0|false|"
                        + "[SURVEIL_PARTITION%7CCLASSIFY_RETAIN%7C1]|hash|SURVEIL_PARTITION|NOT_APPLICABLE");
        final DecisionTraceRequestRecord genericRequest = DecisionTraceRequestRecord.fromSerializedRequest(
                "DECISION_TRACE_V3|REQUEST|9|0|MAIN|1|CARD_SELECTION|OTHER|0|false|"
                        + "[CARD_SELECTION%7C1]|hash|OTHER|NOT_APPLICABLE");

        assertTrue(DecisionTraceTrainingValidator.isHistoryValid(request,
                result(request, DecisionTraceResultKind.CHOSEN,
                        "SURVEIL_PARTITION|CLASSIFY_RETAIN|1", false, false)));
        assertTrue(DecisionTraceTrainingValidator.isHistoryValid(request,
                result(request, DecisionTraceResultKind.INVALID_EXTERNAL_CANDIDATE, "", false, false)));
        assertFalse(DecisionTraceTrainingValidator.isHistoryValid(genericRequest,
                result(genericRequest, DecisionTraceResultKind.CHOSEN,
                        "SURVEIL_PARTITION|CLASSIFY_RETAIN|1", false, false)));
    }

    private static DecisionTraceRequestRecord l2bRequestRecord(final String profile,
            final String stage) {
        return DecisionTraceRequestRecord.fromSerializedRequest(
                serializedL2bRequest(profile, stage, true));
    }

    private static String serializedL2bRequest(final String profile, final String stage,
            final boolean includeMetadata) {
        final String request = "DECISION_TRACE_V3|REQUEST|7|0|MAIN|1|ORDER|" + stage + "|0|false|"
                + "[SURVEIL_RETAINED_TOP_ORDER%7CSELECT_NEXT_TOP%7C1,"
                + "SURVEIL_RETAINED_TOP_ORDER%7CSELECT_NEXT_TOP%7C2]|hash";
        return includeMetadata ? request + "|" + profile + "|NOT_APPLICABLE" : request;
    }

    private static DecisionTraceResultRecord result(final DecisionTraceRequestRecord request,
            final DecisionTraceResultKind kind, final String selected,
            final boolean nativeCallbackCompleted, final boolean mappingAttempted) {
        return new DecisionTraceResultRecord(request.getTraceRequestIndex(), kind, selected,
                nativeCallbackCompleted, mappingAttempted, false, false, false);
    }

    private enum ResolverFailure {
        NULL,
        EXCEPTION,
        ABSENCE
    }

    private DecisionRequest l2bRequest(final Player player) {
        final List<SurveilPartitionCard> items = List.of(
                new SurveilPartitionCard(HIGH_OPAQUE_ITEM_ID, "Island"),
                new SurveilPartitionCard(LOW_OPAQUE_ITEM_ID, "Forest"));
        final SurveilRetainedTopOrderContext context = new SurveilRetainedTopOrderContext(
                SurveilRetainedTopOrderProfile.SURVEIL_RETAINED_TOP_ORDER,
                SurveilRetainedTopOrderDirection.TOP_FIRST, 17L, 0, player.getId(), 2, items);
        return new DecisionRequest(17L, DecisionType.ORDER,
                List.of(LegalCandidate.surveilRetainedTopOrder(0, items.get(0)),
                        LegalCandidate.surveilRetainedTopOrder(1, items.get(1))), context);
    }

    private static void assertNoPrivateL2BInformation(final List<String> records) {
        final List<String> forbiddenMarkers = List.of(
                "card", "cardview", "cardlki", "cardid", "gametimestamp", "nativeordinal",
                "library", "zonetype", "zone", "pair", "heuristic", "rng", "shuffle",
                "random", "surveil_graveyard_insertion_order", "full_surveil_ownership");
        for (final String record : records) {
            final String normalized = record.toLowerCase(Locale.ROOT);
            for (final String forbiddenMarker : forbiddenMarkers) {
                assertFalse(normalized.contains(forbiddenMarker),
                        forbiddenMarker + " leaked through L2B trace: " + record);
            }
        }
    }

    private Fixture fixture() throws Exception {
        final Game game = initAndCreateGame();
        return new Fixture(game, game.getPlayers().get(0), Files.createTempDirectory("frl02l2b-trace-"));
    }

    private Fixture fixtureWithCards() throws Exception {
        final Game game = initAndCreateGame();
        final Player player = game.getPlayers().get(0);
        final List<Card> cards = List.of(
                addCardToZone("Island", player, ZoneType.Hand),
                addCardToZone("Forest", player, ZoneType.Hand),
                addCardToZone("Mountain", player, ZoneType.Hand));
        return new Fixture(game, player, Files.createTempDirectory("frl02l2b-trace-"), cards);
    }

    private static void delete(final Path directory) throws Exception {
        if (Files.exists(directory)) {
            try (var paths = Files.walk(directory)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (Exception exception) {
                        throw new IllegalStateException(exception);
                    }
                });
            }
        }
    }

    private record Fixture(Game game, Player player, Path directory, List<Card> cards) {
        private Fixture(final Game game, final Player player, final Path directory) {
            this(game, player, directory, List.of());
        }
    }
}
