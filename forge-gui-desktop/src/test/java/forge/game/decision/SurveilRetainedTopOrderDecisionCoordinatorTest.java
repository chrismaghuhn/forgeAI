package forge.game.decision;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.player.Player;
import forge.game.zone.ZoneType;
import forge.util.DeterminismAuditRandom;
import forge.util.MyRandom;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.testng.annotations.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

public class SurveilRetainedTopOrderDecisionCoordinatorTest extends AITest {
    @Test
    public void nativeNonEmptyCaptureInvokesCallbackOnceAndReturnsExactPair() {
        final Fixture fixture = fixture("Island", "Forest");
        final CardCollection topN = new CardCollection(fixture.cards());
        final Pair<CardCollection, CardCollection> nativePair = new ImmutablePair<>(
                new CardCollection(List.of(fixture.cards().get(1), fixture.cards().get(0))),
                new CardCollection());
        final AtomicInteger callbackCount = new AtomicInteger();

        final Pair<CardCollection, CardCollection> result = coordinator().captureNativeSurveil(
                fixture.chooser(), topN, argument -> {
                    callbackCount.incrementAndGet();
                    assertSame(argument, topN);
                    return nativePair;
                });

        assertEquals(callbackCount.get(), 1);
        assertSame(result, nativePair);
    }

    @Test
    public void emptyTopNSkipsOwnerAdmissionAndDecisionRowsButPreservesNativeBoundary() {
        final Fixture fixture = fixture();
        final SurveilPartitionDecisionProvider provider = new SurveilPartitionDecisionProvider();
        provider.setOwner(SurveilPartitionOwner.EXTERNAL);
        provider.setResolver(request -> {
            throw new AssertionError("empty native capture must not ask an external resolver");
        });
        final SurveilPartitionDecisionCoordinator coordinator = new SurveilPartitionDecisionCoordinator(provider);
        final CardCollection topN = new CardCollection();
        final Pair<CardCollection, CardCollection> nativePair = new ImmutablePair<>(
                new CardCollection(), new CardCollection());
        final AtomicInteger callbackCount = new AtomicInteger();

        final Pair<CardCollection, CardCollection> result = coordinator.captureNativeSurveil(
                fixture.chooser(), topN, argument -> {
                    callbackCount.incrementAndGet();
                    assertSame(argument, topN);
                    return nativePair;
                });

        assertEquals(callbackCount.get(), 1);
        assertSame(result, nativePair);
        assertEquals(provider.activeSessionCount(), 0);
    }

    @Test
    public void nullTopNStillCallsNativeExactlyOnce() {
        final SurveilPartitionDecisionCoordinator coordinator = coordinator();
        final AtomicInteger callbackCount = new AtomicInteger();
        final AtomicReference<CardCollection> callbackArgument = new AtomicReference<>();
        final Pair<CardCollection, CardCollection> nativePair = new ImmutablePair<>(null, null);

        final Pair<CardCollection, CardCollection> result = coordinator.captureNativeSurveil(
                null, null, argument -> {
                    callbackCount.incrementAndGet();
                    callbackArgument.set(argument);
                    return nativePair;
                });

        assertEquals(callbackCount.get(), 1);
        assertSame(callbackArgument.get(), null);
        assertSame(result, nativePair);
    }

    @Test
    public void nullTopNFallsBackBeforeAdmissionAndPreservesNativeException() {
        final SurveilPartitionDecisionCoordinator coordinator = coordinator();
        final RuntimeException failure = new RuntimeException("native-null-topN");
        final AtomicInteger callbackCount = new AtomicInteger();

        final RuntimeException actual = expectThrows(RuntimeException.class,
                () -> coordinator.captureNativeSurveil(null, null, ignored -> {
                    callbackCount.incrementAndGet();
                    throw failure;
                }));

        assertSame(actual, failure);
        assertEquals(callbackCount.get(), 1);
        assertEquals(coordinator.activeSessionCount(), 0);
    }

    @Test
    public void nativeAdmissionFailureFallsBackOnceWithoutCaptureRows() {
        final Fixture fixture = fixture("Island");
        final CardCollection topN = new CardCollection(fixture.cards());
        final Pair<CardCollection, CardCollection> nativePair = new ImmutablePair<>(
                new CardCollection(), new CardCollection());
        final AtomicInteger callbackCount = new AtomicInteger();
        final SurveilPartitionDecisionProvider provider = new SurveilPartitionDecisionProvider();
        final SurveilPartitionDecisionCoordinator coordinator = new SurveilPartitionDecisionCoordinator(provider);

        final List<String> rows = captureTraceRows(fixture.game(), () -> {
            final Pair<CardCollection, CardCollection> result = coordinator.captureNativeSurveil(
                    null, topN, argument -> {
                        callbackCount.incrementAndGet();
                        assertSame(argument, topN);
                        return nativePair;
                    });
            assertSame(result, nativePair);
        });

        assertEquals(callbackCount.get(), 1);
        assertNoDecisionRows(rows);
        assertEquals(provider.activeSessionCount(), 0);
    }

    @Test
    public void externalAdmissionFailureDoesNotInvokeNativeOrFallBack() {
        final Fixture fixture = fixture("Island");
        final CardCollection topN = new CardCollection(fixture.cards());
        final AtomicInteger callbackCount = new AtomicInteger();
        final SurveilPartitionDecisionProvider provider = new SurveilPartitionDecisionProvider();
        provider.setOwner(SurveilPartitionOwner.EXTERNAL);
        final SurveilPartitionDecisionCoordinator coordinator = new SurveilPartitionDecisionCoordinator(provider);

        final SurveilPartitionAdmissionFailure failure = expectThrows(
                SurveilPartitionAdmissionFailure.class,
                () -> coordinator.captureNativeSurveil(null, topN, ignored -> {
                    callbackCount.incrementAndGet();
                    return new ImmutablePair<>(new CardCollection(), new CardCollection());
                }));

        assertEquals(failure.reason(), SurveilPartitionAdmissionFailureReason.UNSUPPORTED_ADMISSION);
        assertEquals(callbackCount.get(), 0);
        assertEquals(provider.activeSessionCount(), 0);
    }

    @Test
    public void admittedExternalRouteFailsClosedWithoutNativeFallback() {
        final Fixture fixture = fixture("Island");
        final SurveilPartitionDecisionProvider provider = new SurveilPartitionDecisionProvider();
        provider.setOwner(SurveilPartitionOwner.EXTERNAL);
        final SurveilPartitionDecisionCoordinator coordinator = new SurveilPartitionDecisionCoordinator(provider);
        final AtomicInteger callbackCount = new AtomicInteger();

        final RuntimeException failure = expectThrows(
                RuntimeException.class,
                () -> coordinator.captureNativeSurveil(fixture.chooser(), new CardCollection(fixture.cards()),
                        ignored -> {
                            callbackCount.incrementAndGet();
                            return new ImmutablePair<>(new CardCollection(), new CardCollection(fixture.cards()));
                        }));

        assertTrue(failure.getMessage().contains("external Surveil"));
        assertEquals(callbackCount.get(), 0);
        assertEquals(provider.activeSessionCount(), 0);
    }

    @Test
    public void externalOwnerResolvesBothStagesWithoutNativeCallbackOrNativeVector() {
        final Fixture fixture = fixture("Island", "Forest", "Mountain");
        final SurveilPartitionDecisionProvider provider = new SurveilPartitionDecisionProvider();
        provider.setOwner(SurveilPartitionOwner.EXTERNAL);
        provider.setResolver(request -> request.getCandidates().stream()
                .filter(candidate -> candidate.getSurveilPartitionCandidateKind()
                        == SurveilPartitionCandidateKind.CLASSIFY_RETAIN)
                .findFirst()
                .orElseGet(() -> request.getCandidates().get(0)));
        final SurveilPartitionDecisionCoordinator coordinator = new SurveilPartitionDecisionCoordinator(provider);
        final AtomicInteger callbackCount = new AtomicInteger();
        final CardCollection topN = new CardCollection(fixture.cards());

        final List<String> rows = captureTraceRows(fixture.game(), () -> {
            final Pair<CardCollection, CardCollection> result = coordinator.captureNativeSurveil(
                    fixture.chooser(), topN, ignored -> {
                        callbackCount.incrementAndGet();
                        throw new AssertionError("EXTERNAL must not invoke native arrangeForSurveil");
                    });
            assertEquals(result.getLeft().size(), 3);
            assertEquals(result.getRight().size(), 0);
            assertTrue(result.getLeft() != topN);
            assertTrue(result.getRight() != topN);
            result.getLeft().add(fixture.cards().get(0));
            result.getLeft().remove(result.getLeft().size() - 1);
        });

        assertEquals(callbackCount.get(), 0);
        assertEquals(requestRows(rows, "|CARD_SELECTION|SURVEIL_PARTITION|").size(), 3);
        assertEquals(requestRows(rows, "|ORDER|SURVEIL_RETAINED_TOP_ORDER|").size(), 2);
        assertEquals(resultRows(rows).size(), 5);
        assertTrue(resultRows(rows).stream().allMatch(row -> row.contains("|CHOSEN|")
                && row.contains("|false|false|")));
        assertEquals(provider.activeSessionCount(), 0);
    }

    @Test
    public void externalRetainedCardinalitySkipsOrLimitsL2BResolverCalls() {
        for (int retainedCount = 0; retainedCount <= 2; retainedCount++) {
            final int requestedRetainedCount = retainedCount;
            final Fixture fixture = fixture("Island", "Forest");
            final SurveilPartitionDecisionProvider provider = new SurveilPartitionDecisionProvider();
            provider.setOwner(SurveilPartitionOwner.EXTERNAL);
            final AtomicInteger l2aCalls = new AtomicInteger();
            final AtomicInteger l2bCalls = new AtomicInteger();
            provider.setResolver(request -> {
                if (request.getDecisionType() == DecisionType.CARD_SELECTION) {
                    final int call = l2aCalls.getAndIncrement();
                    final SurveilPartitionCandidateKind kind = requestedRetainedCount == 2
                            || requestedRetainedCount == 1 && call == 0
                            ? SurveilPartitionCandidateKind.CLASSIFY_RETAIN
                            : SurveilPartitionCandidateKind.CLASSIFY_GRAVEYARD;
                    return request.getCandidates().stream()
                            .filter(candidate -> candidate.getSurveilPartitionCandidateKind() == kind)
                            .findFirst().orElseThrow();
                }
                l2bCalls.incrementAndGet();
                return request.getCandidates().get(0);
            });
            final SurveilPartitionDecisionCoordinator coordinator = new SurveilPartitionDecisionCoordinator(provider);

            final Pair<CardCollection, CardCollection> result = coordinator.captureNativeSurveil(
                    fixture.chooser(), new CardCollection(fixture.cards()),
                    ignored -> { throw new AssertionError("EXTERNAL must not invoke native arrange"); });

            assertEquals(result.getLeft().size(), retainedCount);
            assertEquals(result.getRight().size(), fixture.cards().size() - retainedCount);
            assertEquals(l2aCalls.get(), fixture.cards().size());
            assertEquals(l2bCalls.get(), Math.max(0, retainedCount - 1));
        }
    }

    @Test
    public void externalPairRightUsesFreshPrivateSnapshotFilteredOrder() {
        final Fixture fixture = fixture("Island", "Forest", "Mountain");
        final SurveilPartitionDecisionProvider provider = new SurveilPartitionDecisionProvider();
        provider.setOwner(SurveilPartitionOwner.EXTERNAL);
        final AtomicInteger l2aCalls = new AtomicInteger();
        provider.setResolver(request -> {
            if (request.getDecisionType() == DecisionType.CARD_SELECTION) {
                final int call = l2aCalls.getAndIncrement();
                final SurveilPartitionCandidateKind kind = call < 2
                        ? SurveilPartitionCandidateKind.CLASSIFY_GRAVEYARD
                        : SurveilPartitionCandidateKind.CLASSIFY_RETAIN;
                return request.getCandidates().stream()
                        .filter(candidate -> candidate.getSurveilPartitionCandidateKind() == kind)
                        .findFirst().orElseThrow();
            }
            throw new AssertionError("R=1 must not open L2B");
        });
        final SurveilPartitionDecisionCoordinator coordinator = new SurveilPartitionDecisionCoordinator(provider);
        final CardCollection topN = new CardCollection(fixture.cards());

        final Pair<CardCollection, CardCollection> result = coordinator.captureNativeSurveil(
                fixture.chooser(), topN, ignored -> {
                    throw new AssertionError("EXTERNAL must not invoke native arrange");
                });

        assertEquals(l2aCalls.get(), 3);
        assertEquals(result.getLeft().size(), 1);
        assertEquals(result.getRight().size(), 2);
        assertSame(result.getRight().get(0), fixture.cards().get(0));
        assertSame(result.getRight().get(1), fixture.cards().get(1));
        assertTrue(result.getRight() != topN);
    }

    @Test
    public void pairRightRemainsPrivateCompatibilityFallbackAcrossSnapshotPermutations() {
        final Fixture fixture = fixture("Mountain", "Island", "Forest", "Swamp");
        final List<Card> firstOrder = fixture.cards();
        final List<Card> secondOrder = List.of(
                fixture.cards().get(2), fixture.cards().get(1),
                fixture.cards().get(0), fixture.cards().get(3));
        final Set<String> graveyardNames = Set.of("Mountain", "Forest");
        final SurveilPartitionDecisionProvider firstProvider = externalProvider(graveyardNames);
        final SurveilPartitionDecisionProvider secondProvider = externalProvider(graveyardNames);
        final AtomicReference<Pair<CardCollection, CardCollection>> firstPair = new AtomicReference<>();
        final AtomicReference<Pair<CardCollection, CardCollection>> secondPair = new AtomicReference<>();

        final List<String> firstRows = captureTraceRows(fixture.game(), () -> firstPair.set(
                new SurveilPartitionDecisionCoordinator(firstProvider).captureNativeSurveil(
                        fixture.chooser(), new CardCollection(firstOrder),
                        ignored -> { throw new AssertionError("EXTERNAL must not invoke native arrange"); })));
        final List<String> secondRows = captureTraceRows(fixture.game(), () -> secondPair.set(
                new SurveilPartitionDecisionCoordinator(secondProvider).captureNativeSurveil(
                        fixture.chooser(), new CardCollection(secondOrder),
                        ignored -> { throw new AssertionError("EXTERNAL must not invoke native arrange"); })));

        assertSame(firstPair.get().getRight().get(0), fixture.cards().get(0));
        assertSame(firstPair.get().getRight().get(1), fixture.cards().get(2));
        assertSame(secondPair.get().getRight().get(0), fixture.cards().get(2));
        assertSame(secondPair.get().getRight().get(1), fixture.cards().get(0));
        assertTrue(firstPair.get().getRight().get(0) != secondPair.get().getRight().get(0));
        assertSame(firstPair.get().getLeft().get(0), fixture.cards().get(1));
        assertSame(firstPair.get().getLeft().get(1), fixture.cards().get(3));
        assertSame(secondPair.get().getLeft().get(0), fixture.cards().get(1));
        assertSame(secondPair.get().getLeft().get(1), fixture.cards().get(3));

        assertEquals(publicSemanticKeys(firstRows), publicSemanticKeys(secondRows));
        assertEquals(requestRows(firstRows, "|CARD_SELECTION|SURVEIL_PARTITION|").size(), 4);
        assertEquals(requestRows(firstRows, "|ORDER|SURVEIL_RETAINED_TOP_ORDER|").size(), 1);
        assertEquals(resultRows(firstRows).size(), 5);
        assertTrue(requestRows(firstRows, "").stream()
                .allMatch(row -> row.endsWith("|NOT_APPLICABLE")));
        assertTrue(firstRows.stream().noneMatch(row -> row.contains("SURVEIL_GRAVEYARD_INSERTION_ORDER")));
        assertTrue(firstRows.stream().noneMatch(row -> row.contains("FULL_SURVEIL_OWNERSHIP")));
        assertTrue(firstProvider.activeSessionCount() == 0);
        assertTrue(secondProvider.activeSessionCount() == 0);
    }

    @Test
    public void externalNullResolverAfterOpenHandleIsTypedTerminalWithoutFallback() {
        final Fixture fixture = fixture("Island", "Forest");
        final SurveilPartitionDecisionProvider provider = new SurveilPartitionDecisionProvider();
        provider.setOwner(SurveilPartitionOwner.EXTERNAL);
        provider.setResolver(request -> null);
        final SurveilPartitionDecisionCoordinator coordinator = new SurveilPartitionDecisionCoordinator(provider);
        final AtomicInteger callbackCount = new AtomicInteger();

        final List<String> rows = captureTraceRows(fixture.game(), () -> expectThrows(
                RuntimeException.class,
                () -> coordinator.captureNativeSurveil(fixture.chooser(), new CardCollection(fixture.cards()),
                        ignored -> {
                            callbackCount.incrementAndGet();
                            throw new AssertionError("invalid EXTERNAL resolution must not fall back");
                        })));

        assertEquals(callbackCount.get(), 0);
        assertEquals(requestRows(rows, "|CARD_SELECTION|SURVEIL_PARTITION|").size(), 1);
        assertExternalInvalidCandidate(rows, 1);
        assertEquals(provider.activeSessionCount(), 0);
    }

    @Test
    public void externalResolverExceptionAfterOpenHandleIsTerminalAndDoesNotFallback() {
        final Fixture fixture = fixture("Island", "Forest");
        final RuntimeException resolverFailure = new RuntimeException("resolver failure");
        final SurveilPartitionDecisionProvider provider = new SurveilPartitionDecisionProvider();
        provider.setOwner(SurveilPartitionOwner.EXTERNAL);
        provider.setResolver(request -> {
            throw resolverFailure;
        });
        final SurveilPartitionDecisionCoordinator coordinator = new SurveilPartitionDecisionCoordinator(provider);
        final AtomicInteger callbackCount = new AtomicInteger();

        final AtomicReference<RuntimeException> actualReference = new AtomicReference<>();
        final List<String> rows = captureTraceRows(fixture.game(), () -> actualReference.set(expectThrows(
                RuntimeException.class,
                () -> coordinator.captureNativeSurveil(fixture.chooser(), new CardCollection(fixture.cards()),
                        ignored -> {
                            callbackCount.incrementAndGet();
                            throw new AssertionError("resolver failure must not invoke native arrange");
                        }))));

        assertSame(actualReference.get(), resolverFailure);
        assertExternalInvalidCandidate(rows, 1);
        assertEquals(callbackCount.get(), 0);
        assertEquals(provider.activeSessionCount(), 0);
    }

    @Test
    public void externalResolverAbsenceAfterOpenHandleIsTerminalAndDoesNotFallback() {
        final Fixture fixture = fixture("Island", "Forest");
        final SurveilPartitionDecisionProvider provider = new SurveilPartitionDecisionProvider();
        provider.setOwner(SurveilPartitionOwner.EXTERNAL);
        final SurveilPartitionDecisionCoordinator coordinator = new SurveilPartitionDecisionCoordinator(provider);
        final AtomicInteger callbackCount = new AtomicInteger();

        final List<String> rows = captureTraceRows(fixture.game(), () -> expectThrows(
                RuntimeException.class,
                () -> coordinator.captureNativeSurveil(fixture.chooser(), new CardCollection(fixture.cards()),
                        ignored -> {
                            callbackCount.incrementAndGet();
                            throw new AssertionError("missing resolver must not invoke native arrange");
                        })));

        assertExternalInvalidCandidate(rows, 1);
        assertEquals(callbackCount.get(), 0);
        assertEquals(provider.activeSessionCount(), 0);
    }

    @Test
    public void externalForeignCandidateAfterOpenHandleIsTerminalAndDoesNotFallback() {
        final Fixture foreignFixture = fixture("Plains", "Swamp");
        final SurveilPartitionDecisionProvider foreignProvider = new SurveilPartitionDecisionProvider();
        final SurveilPartitionSession foreignSession = foreignProvider.admit(foreignFixture.chooser(),
                foreignFixture.cards(), SurveilPartitionOwner.EXTERNAL);
        final LegalCandidate foreignCandidate = foreignProvider.createMembershipRequest(foreignSession)
                .getCandidates().get(0);
        foreignProvider.closeSession(foreignSession);

        final Fixture fixture = fixture("Island", "Forest");
        final SurveilPartitionDecisionProvider provider = new SurveilPartitionDecisionProvider();
        provider.setOwner(SurveilPartitionOwner.EXTERNAL);
        provider.setResolver(request -> foreignCandidate);
        final SurveilPartitionDecisionCoordinator coordinator = new SurveilPartitionDecisionCoordinator(provider);
        final AtomicInteger callbackCount = new AtomicInteger();

        final List<String> rows = captureTraceRows(fixture.game(), () -> expectThrows(
                RuntimeException.class,
                () -> coordinator.captureNativeSurveil(fixture.chooser(), new CardCollection(fixture.cards()),
                        ignored -> {
                            callbackCount.incrementAndGet();
                            throw new AssertionError("foreign candidate must not invoke native arrange");
                        })));

        assertExternalInvalidCandidate(rows, 1);
        assertEquals(callbackCount.get(), 0);
        assertEquals(provider.activeSessionCount(), 0);
    }

    @Test
    public void externalStaleCandidateAfterOpenHandleIsTerminalAndDoesNotFallback() {
        final Fixture fixture = fixture("Island", "Forest");
        final SurveilPartitionDecisionProvider provider = new SurveilPartitionDecisionProvider();
        provider.setOwner(SurveilPartitionOwner.EXTERNAL);
        final AtomicReference<LegalCandidate> firstCandidate = new AtomicReference<>();
        final AtomicInteger resolverCalls = new AtomicInteger();
        provider.setResolver(request -> resolverCalls.getAndIncrement() == 0
                ? firstCandidate.updateAndGet(ignored -> request.getCandidates().get(0))
                : firstCandidate.get());
        final SurveilPartitionDecisionCoordinator coordinator = new SurveilPartitionDecisionCoordinator(provider);
        final AtomicInteger callbackCount = new AtomicInteger();

        final List<String> rows = captureTraceRows(fixture.game(), () -> expectThrows(
                RuntimeException.class,
                () -> coordinator.captureNativeSurveil(fixture.chooser(), new CardCollection(fixture.cards()),
                        ignored -> {
                            callbackCount.incrementAndGet();
                            throw new AssertionError("stale candidate must not invoke native arrange");
                        })));

        assertExternalInvalidCandidate(rows, 1);
        assertEquals(callbackCount.get(), 0);
        assertEquals(provider.activeSessionCount(), 0);
    }

    @Test
    public void externalDuplicateCandidateAfterOpenHandleIsTerminalAndDoesNotFallback() {
        final Fixture fixture = fixture("Island", "Forest", "Mountain");
        final SurveilPartitionDecisionProvider provider = new SurveilPartitionDecisionProvider();
        provider.setOwner(SurveilPartitionOwner.EXTERNAL);
        final AtomicReference<LegalCandidate> firstOrderCandidate = new AtomicReference<>();
        provider.setResolver(request -> {
            if (request.getDecisionType() == DecisionType.CARD_SELECTION) {
                return request.getCandidates().get(1);
            }
            firstOrderCandidate.compareAndSet(null, request.getCandidates().get(0));
            return firstOrderCandidate.get();
        });
        final SurveilPartitionDecisionCoordinator coordinator = new SurveilPartitionDecisionCoordinator(provider);
        final AtomicInteger callbackCount = new AtomicInteger();

        final List<String> rows = captureTraceRows(fixture.game(), () -> expectThrows(
                RuntimeException.class,
                () -> coordinator.captureNativeSurveil(fixture.chooser(), new CardCollection(fixture.cards()),
                        ignored -> {
                            callbackCount.incrementAndGet();
                            throw new AssertionError("duplicate candidate must not invoke native arrange");
                        })));

        assertExternalInvalidCandidate(rows, 1);
        assertEquals(callbackCount.get(), 0);
        assertEquals(provider.activeSessionCount(), 0);
    }

    @Test
    public void externalIllegalCandidateAfterOpenHandleIsTerminalAndDoesNotFallback() {
        final Fixture fixture = fixture("Island", "Forest");
        final SurveilPartitionDecisionProvider provider = new SurveilPartitionDecisionProvider();
        provider.setOwner(SurveilPartitionOwner.EXTERNAL);
        provider.setResolver(request -> LegalCandidate.surveilRetainedTopOrder(0,
                request.getCandidates().get(0).getSurveilPartitionCard()));
        final SurveilPartitionDecisionCoordinator coordinator = new SurveilPartitionDecisionCoordinator(provider);
        final AtomicInteger callbackCount = new AtomicInteger();

        final List<String> rows = captureTraceRows(fixture.game(), () -> expectThrows(
                RuntimeException.class,
                () -> coordinator.captureNativeSurveil(fixture.chooser(), new CardCollection(fixture.cards()),
                        ignored -> {
                            callbackCount.incrementAndGet();
                            throw new AssertionError("illegal candidate must not invoke native arrange");
                        })));

        assertExternalInvalidCandidate(rows, 1);
        assertEquals(callbackCount.get(), 0);
        assertEquals(provider.activeSessionCount(), 0);
    }

    @Test
    public void externalPairReadyBoundaryReturnsPairAndDetachesRegistry() {
        final Fixture fixture = fixture("Island", "Forest", "Mountain");
        final SurveilPartitionDecisionProvider provider = new SurveilPartitionDecisionProvider();
        provider.setOwner(SurveilPartitionOwner.EXTERNAL);
        final AtomicReference<SurveilPartitionSession> sessionReference = new AtomicReference<>();
        provider.setResolver(request -> {
            sessionReference.compareAndSet(null, soleRegisteredSession(provider));
            return request.getCandidates().get(1);
        });
        final SurveilPartitionDecisionCoordinator coordinator = new SurveilPartitionDecisionCoordinator(provider);

        final Pair<CardCollection, CardCollection> result = coordinator.captureNativeSurveil(
                fixture.chooser(), new CardCollection(fixture.cards()), ignored -> {
                    throw new AssertionError("EXTERNAL must not invoke native arrange");
                });

        assertEquals(result.getLeft().size(), 3);
        assertEquals(result.getRight().size(), 0);
        assertTrue(sessionReference.get().isPairReady());
        assertTrue(sessionReference.get().isClosed());
        assertEquals(provider.activeSessionCount(), 0);
    }

    @Test
    public void nativeCallbackCardMutationDoesNotRemapCapturedItemIds() {
        final Fixture fixture = fixture("Island", "Island", "Forest");
        final Card mutated = fixture.cards().get(0);
        final Pair<CardCollection, CardCollection> nativePair = new ImmutablePair<>(
                new CardCollection(fixture.cards()), new CardCollection());
        final AtomicReference<List<String>> traceRows = new AtomicReference<>();

        traceRows.set(captureTraceRows(fixture.game(), () -> coordinator().captureNativeSurveil(
                fixture.chooser(), new CardCollection(fixture.cards()), ignored -> {
                    mutated.setName("Mutated during callback");
                    mutated.setGameTimestamp(Long.MAX_VALUE);
                    return nativePair;
                })));

        assertEquals(requestRows(traceRows.get(), "|CARD_SELECTION|SURVEIL_PARTITION|").size(), 3);
        assertEquals(requestRows(traceRows.get(), "|ORDER|SURVEIL_RETAINED_TOP_ORDER|").size(), 2);
        assertEquals(resultRows(traceRows.get()).size(), 5);
    }

    @Test
    public void nativeCallbackExceptionIdentityIsRethrownWithoutRetry() {
        final Fixture fixture = fixture("Island");
        final RuntimeException failure = new RuntimeException("native callback");
        final AtomicInteger callbackCount = new AtomicInteger();
        final SurveilPartitionDecisionCoordinator coordinator = coordinator();

        final RuntimeException actual = expectThrows(RuntimeException.class,
                () -> coordinator.captureNativeSurveil(fixture.chooser(), new CardCollection(fixture.cards()),
                        ignored -> {
                            callbackCount.incrementAndGet();
                            throw failure;
                        }));

        assertSame(actual, failure);
        assertEquals(callbackCount.get(), 1);
        assertEquals(coordinator.activeSessionCount(), 0);
    }

    @Test
    public void malformedNativePairProducesZeroRowsBeforeFirstHandle() {
        final Fixture fixture = fixture("Island", "Forest");
        final SurveilPartitionDecisionProvider provider = new SurveilPartitionDecisionProvider();
        final SurveilPartitionDecisionCoordinator coordinator = new SurveilPartitionDecisionCoordinator(provider);
        final Pair<CardCollection, CardCollection> nativePair = new ImmutablePair<>(
                new CardCollection(), new CardCollection(fixture.cards().get(0)));
        final AtomicReference<SurveilPartitionSession> sessionReference = new AtomicReference<>();

        final List<String> rows = captureTraceRows(fixture.game(), () -> {
            final Pair<CardCollection, CardCollection> result = coordinator.captureNativeSurveil(
                    fixture.chooser(), new CardCollection(fixture.cards()), ignored -> {
                        sessionReference.set(soleRegisteredSession(provider));
                        return nativePair;
                    });
            assertSame(result, nativePair);
        });

        assertNoDecisionRows(rows);
        assertFalse(sessionReference.get().isPairReady());
        assertTrue(sessionReference.get().isClosed());
        assertEquals(provider.activeSessionCount(), 0);
    }

    @Test
    public void nullLeftNativePairProducesZeroRowsBeforeFirstHandle() {
        assertNullNativePairSideProducesZeroRowsBeforeFirstHandle(true);
    }

    @Test
    public void nullRightNativePairProducesZeroRowsBeforeFirstHandle() {
        assertNullNativePairSideProducesZeroRowsBeforeFirstHandle(false);
    }

    @Test
    public void nativePairAndCollectionsAreReturnedWithoutRepairOrReordering() {
        final Fixture fixture = fixture("Island", "Forest", "Mountain");
        final CardCollection topN = new CardCollection(fixture.cards());
        final CardCollection retained = new CardCollection(List.of(
                fixture.cards().get(2), fixture.cards().get(0)));
        final CardCollection graveyard = new CardCollection(fixture.cards().get(1));
        final List<Card> retainedBefore = new ArrayList<>(retained);
        final List<Card> graveyardBefore = new ArrayList<>(graveyard);
        final Pair<CardCollection, CardCollection> nativePair = new ImmutablePair<>(retained, graveyard);

        final Pair<CardCollection, CardCollection> result = coordinator().captureNativeSurveil(
                fixture.chooser(), topN, ignored -> nativePair);

        assertSame(result, nativePair);
        assertSame(result.getLeft(), retained);
        assertSame(result.getRight(), graveyard);
        assertEquals(new ArrayList<>(retained), retainedBefore);
        assertEquals(new ArrayList<>(graveyard), graveyardBefore);
    }

    @Test
    public void nativeOwnerSelectionIsPermanentAndResolverFree() {
        final Fixture fixture = fixture("Island", "Forest");
        final SurveilPartitionDecisionProvider provider = new SurveilPartitionDecisionProvider();
        provider.setOwner(SurveilPartitionOwner.NATIVE);
        provider.setResolver(request -> {
            throw new AssertionError("native capture must not invoke an external resolver");
        });
        final SurveilPartitionDecisionCoordinator coordinator = new SurveilPartitionDecisionCoordinator(provider);
        final Pair<CardCollection, CardCollection> nativePair = new ImmutablePair<>(
                new CardCollection(fixture.cards().get(1)), new CardCollection(fixture.cards().get(0)));
        final AtomicReference<SurveilPartitionSession> sessionReference = new AtomicReference<>();

        final List<String> rows = captureTraceRows(fixture.game(), () -> {
            final Pair<CardCollection, CardCollection> result = coordinator.captureNativeSurveil(
                    fixture.chooser(), new CardCollection(fixture.cards()), ignored -> {
                        sessionReference.set(soleRegisteredSession(provider));
                        provider.setOwner(SurveilPartitionOwner.EXTERNAL);
                        return nativePair;
                    });
            assertSame(result, nativePair);
        });

        assertEquals(sessionReference.get().getOwner(), SurveilPartitionOwner.NATIVE);
        assertTrue(rows.stream().anyMatch(row -> row.contains("|CARD_SELECTION|SURVEIL_PARTITION|")));
        assertEquals(provider.activeSessionCount(), 0);
    }

    @Test
    public void nativeMaterializationHasExactRMinusOneRetainedOrderRows() {
        for (int retainedCount = 0; retainedCount <= 3; retainedCount++) {
            final int retained = retainedCount;
            final int itemCount = Math.max(1, retained);
            final Fixture fixture = fixture(itemCount);
            final Pair<CardCollection, CardCollection> nativePair = nativePair(fixture, retained);
            final SurveilPartitionDecisionProvider provider = new SurveilPartitionDecisionProvider();
            final SurveilPartitionDecisionCoordinator coordinator =
                    new SurveilPartitionDecisionCoordinator(provider);
            final AtomicInteger callbackCount = new AtomicInteger();
            final AtomicReference<SurveilPartitionSession> sessionReference = new AtomicReference<>();

            final List<String> rows = captureTraceRows(fixture.game(), () -> {
                final Pair<CardCollection, CardCollection> result = coordinator.captureNativeSurveil(
                        fixture.chooser(), new CardCollection(fixture.cards()), ignored -> {
                            callbackCount.incrementAndGet();
                            sessionReference.set(soleRegisteredSession(provider));
                            assertFalse(sessionReference.get().isPairReady());
                            if (retained >= 2) {
                                assertFalse(sessionReference.get().isRetainedTopOrderComplete());
                            }
                            return nativePair;
                        });
                assertSame(result, nativePair);
            });

            assertEquals(callbackCount.get(), 1, "callback count for R=" + retained);
            assertEquals(requestRows(rows, "|CARD_SELECTION|SURVEIL_PARTITION|").size(), itemCount,
                    "L2A rows for R=" + retained);
            assertEquals(requestRows(rows, "|ORDER|SURVEIL_RETAINED_TOP_ORDER|").size(),
                    Math.max(0, retained - 1), "L2B rows for R=" + retained);
            assertEquals(resultRows(rows).size(), itemCount + Math.max(0, retained - 1));
            assertTrue(sessionReference.get().isRetainedTopOrderComplete());
            assertTrue(sessionReference.get().isPairReady());
            assertTrue(sessionReference.get().isClosed());
            assertFalse(sessionReference.get().hasOpenRequest());
            assertEquals(provider.activeSessionCount(), 0);
            if (retained >= 2) {
                assertEquals(readField(sessionReference.get(), "finalRetainedNativeOrder"),
                        new ArrayList<>(nativePair.getLeft()));
            }
        }
    }

    @Test
    public void l2bNativeRowsUseNotApplicableTeacherEligibility() {
        final Fixture fixture = fixture("Island", "Forest", "Mountain");
        final Pair<CardCollection, CardCollection> nativePair = new ImmutablePair<>(
                new CardCollection(fixture.cards()), new CardCollection());

        final List<String> rows = captureTraceRows(fixture.game(), () ->
                coordinator().captureNativeSurveil(fixture.chooser(), new CardCollection(fixture.cards()),
                        ignored -> nativePair));

        assertTrue(requestRows(rows, "|CARD_SELECTION|SURVEIL_PARTITION|").stream()
                .allMatch(row -> row.endsWith("|SURVEIL_PARTITION|NOT_APPLICABLE")));
        assertTrue(requestRows(rows, "|ORDER|SURVEIL_RETAINED_TOP_ORDER|").stream()
                .allMatch(row -> row.endsWith("|SURVEIL_RETAINED_TOP_ORDER|NOT_APPLICABLE")));
    }

    @Test
    public void pairReadyIsSetOnlyAfterFinalMaterializationAndNoHandleRemainsOpen() {
        final Fixture fixture = fixture("Island", "Forest", "Mountain");
        final SurveilPartitionDecisionProvider provider = new SurveilPartitionDecisionProvider();
        final SurveilPartitionDecisionCoordinator coordinator = new SurveilPartitionDecisionCoordinator(provider);
        final Pair<CardCollection, CardCollection> nativePair = new ImmutablePair<>(
                new CardCollection(fixture.cards()), new CardCollection());
        final AtomicReference<SurveilPartitionSession> sessionReference = new AtomicReference<>();

        coordinator.captureNativeSurveil(fixture.chooser(), new CardCollection(fixture.cards()), ignored -> {
            final SurveilPartitionSession session = soleRegisteredSession(provider);
            sessionReference.set(session);
            assertFalse(session.isPairReady());
            assertFalse(session.isRetainedTopOrderComplete());
            return nativePair;
        });

        assertTrue(sessionReference.get().isPairReady());
        assertTrue(sessionReference.get().isRetainedTopOrderComplete());
        assertFalse(sessionReference.get().hasOpenRequest());
        assertTrue(sessionReference.get().isClosed());
    }

    @Test
    public void nativePathDoesNotInvokeResolverOrDrawRandomness() {
        final Fixture fixture = fixture("Island", "Forest", "Mountain");
        final SurveilPartitionDecisionProvider provider = new SurveilPartitionDecisionProvider();
        final AtomicInteger resolverCalls = new AtomicInteger();
        provider.setResolver(request -> {
            resolverCalls.incrementAndGet();
            throw new AssertionError("native path must not invoke resolver");
        });
        final SurveilPartitionDecisionCoordinator coordinator = new SurveilPartitionDecisionCoordinator(provider);
        final Pair<CardCollection, CardCollection> nativePair = new ImmutablePair<>(
                new CardCollection(fixture.cards()), new CardCollection());
        final Random previousRandom = MyRandom.getRandom();
        final DeterminismAuditRandom auditRandom = new DeterminismAuditRandom(20260814L);
        MyRandom.setRandom(auditRandom);
        try {
            final long before = auditRandom.getDrawCount();
            coordinator.captureNativeSurveil(fixture.chooser(), new CardCollection(fixture.cards()),
                    ignored -> nativePair);
            assertEquals(auditRandom.getDrawCount(), before);
        } finally {
            MyRandom.setRandom(previousRandom);
        }
        assertEquals(resolverCalls.get(), 0);
    }

    @Test
    public void postHandleFailureWithNoMappingIsUnobservedAndKeepsPairAuthoritative() {
        final Fixture fixture = fixture("Island", "Forest");
        final SurveilPartitionDecisionProvider provider = new SurveilPartitionDecisionProvider();
        final SurveilPartitionDecisionCoordinator coordinator = new SurveilPartitionDecisionCoordinator(provider);
        final RuntimeException failure = new RuntimeException("post-handle read failure");
        final FaultingCardCollection retained = FaultingCardCollection.throwing(
                List.of(fixture.cards().get(0), fixture.cards().get(1)), failure);
        final Pair<CardCollection, CardCollection> nativePair = new ImmutablePair<>(retained,
                new CardCollection());
        final AtomicReference<SurveilPartitionSession> sessionReference = new AtomicReference<>();

        final List<String> rows = captureTraceRows(fixture.game(), () -> {
            final Pair<CardCollection, CardCollection> result = coordinator.captureNativeSurveil(
                    fixture.chooser(), new CardCollection(fixture.cards()), ignored -> {
                        sessionReference.set(soleRegisteredSession(provider));
                        return nativePair;
                    });
            assertSame(result, nativePair);
        });

        assertEquals(requestRows(rows, "|ORDER|SURVEIL_RETAINED_TOP_ORDER|").size(), 1);
        assertTrue(rows.stream().anyMatch(row -> row.contains("|RESULT|")
                && row.contains("|UNOBSERVED||true|false|")));
        assertFalse(sessionReference.get().isPairReady());
        assertTrue(sessionReference.get().isClosed());
        assertFalse(sessionReference.get().hasOpenRequest());
        assertEquals(provider.activeSessionCount(), 0);
    }

    @Test
    public void postHandleFailureAfterMappingIsMappingFailedAndKeepsPairAuthoritative() {
        final Fixture fixture = fixture("Island", "Forest");
        final SurveilPartitionDecisionProvider provider = new SurveilPartitionDecisionProvider();
        final SurveilPartitionDecisionCoordinator coordinator = new SurveilPartitionDecisionCoordinator(provider);
        final AtomicReference<SurveilPartitionSession> sessionReference = new AtomicReference<>();
        final FaultingCardCollection retained = FaultingCardCollection.afterRead(() ->
                sessionReference.get().markClosed("test-post-handle-failure"),
                List.of(fixture.cards().get(0), fixture.cards().get(1)));
        final Pair<CardCollection, CardCollection> nativePair = new ImmutablePair<>(retained,
                new CardCollection());

        final List<String> rows = captureTraceRows(fixture.game(), () -> {
            final Pair<CardCollection, CardCollection> result = coordinator.captureNativeSurveil(
                    fixture.chooser(), new CardCollection(fixture.cards()), ignored -> {
                        sessionReference.set(soleRegisteredSession(provider));
                        return nativePair;
                    });
            assertSame(result, nativePair);
        });

        assertEquals(requestRows(rows, "|ORDER|SURVEIL_RETAINED_TOP_ORDER|").size(), 1);
        assertTrue(rows.stream().anyMatch(row -> row.contains("|RESULT|")
                && row.contains("|MAPPING_FAILED||true|true|")));
        assertFalse(sessionReference.get().isPairReady());
        assertTrue(sessionReference.get().isClosed());
        assertFalse(sessionReference.get().hasOpenRequest());
        assertEquals(provider.activeSessionCount(), 0);
    }

    @Test
    public void legacyV1CounterKeysExcludeRetainedTopOrderCounters() throws Exception {
        final Method approvedCounterKeys = SurveilPartitionDiagnostics.class
                .getDeclaredMethod("approvedCounterKeys");
        approvedCounterKeys.setAccessible(true);
        @SuppressWarnings("unchecked")
        final List<String> keys = (List<String>) approvedCounterKeys.invoke(null);

        assertFalse(keys.contains("owner_selected_NATIVE"));
        assertFalse(keys.contains("retained_order_request_count"));
        assertFalse(keys.contains("retained_order_result_count"));
        assertFalse(keys.contains("pair_ready_count"));
        assertFalse(keys.contains("post_handle_capture_failure_UNOBSERVED"));
        assertFalse(keys.contains("post_handle_capture_failure_MAPPING_FAILED"));
    }

    private SurveilPartitionDecisionCoordinator coordinator() {
        return new SurveilPartitionDecisionCoordinator(new SurveilPartitionDecisionProvider());
    }

    private static SurveilPartitionDecisionProvider externalProvider(final Set<String> graveyardNames) {
        final SurveilPartitionDecisionProvider provider = new SurveilPartitionDecisionProvider();
        provider.setOwner(SurveilPartitionOwner.EXTERNAL);
        provider.setResolver(request -> {
            if (request.getDecisionType() == DecisionType.CARD_SELECTION) {
                final SurveilPartitionContext context = request.getSurveilPartitionContext();
                final String visibleName = context.getVisibleItems().stream()
                        .filter(item -> item.getItemId() == context.getCurrentItemId())
                        .findFirst().orElseThrow().getVisibleName();
                final SurveilPartitionCandidateKind kind = graveyardNames.contains(visibleName)
                        ? SurveilPartitionCandidateKind.CLASSIFY_GRAVEYARD
                        : SurveilPartitionCandidateKind.CLASSIFY_RETAIN;
                return request.getCandidates().stream()
                        .filter(candidate -> candidate.getSurveilPartitionCandidateKind() == kind)
                        .findFirst().orElseThrow();
            }
            return request.getCandidates().get(0);
        });
        return provider;
    }

    private Fixture fixture(final String... names) {
        final Game game = initAndCreateGame();
        final Player chooser = game.getPlayers().get(1);
        final List<Card> cards = new ArrayList<>();
        for (final String name : names) {
            cards.add(addCardToZone(name, chooser, ZoneType.Hand));
        }
        return new Fixture(game, chooser, cards);
    }

    private Fixture fixture(final int itemCount) {
        final String[] names = {"Island", "Forest", "Mountain"};
        return fixture(java.util.Arrays.copyOf(names, itemCount));
    }

    private static Pair<CardCollection, CardCollection> nativePair(final Fixture fixture,
            final int retainedCount) {
        return new ImmutablePair<>(
                new CardCollection(fixture.cards().subList(0, retainedCount)),
                new CardCollection(fixture.cards().subList(retainedCount, fixture.cards().size())));
    }

    private void assertNullNativePairSideProducesZeroRowsBeforeFirstHandle(final boolean nullLeft) {
        final Fixture fixture = fixture("Island", "Forest");
        final SurveilPartitionDecisionProvider provider = new SurveilPartitionDecisionProvider();
        final SurveilPartitionDecisionCoordinator coordinator =
                new SurveilPartitionDecisionCoordinator(provider);
        final Pair<CardCollection, CardCollection> nativePair = new ImmutablePair<>(
                nullLeft ? null : new CardCollection(fixture.cards()),
                nullLeft ? new CardCollection(fixture.cards()) : null);
        final AtomicReference<SurveilPartitionSession> sessionReference = new AtomicReference<>();

        final List<String> rows = captureTraceRows(fixture.game(), () -> {
            final Pair<CardCollection, CardCollection> result = coordinator.captureNativeSurveil(
                    fixture.chooser(), new CardCollection(fixture.cards()), ignored -> {
                        sessionReference.set(soleRegisteredSession(provider));
                        return nativePair;
                    });
            assertSame(result, nativePair);
        });

        assertNoDecisionRows(rows);
        assertFalse(sessionReference.get().isPairReady());
        assertTrue(sessionReference.get().isClosed());
        assertFalse(sessionReference.get().hasOpenRequest());
        assertEquals(provider.activeSessionCount(), 0);
    }

    private static SurveilPartitionSession soleRegisteredSession(
            final SurveilPartitionDecisionProvider provider) {
        try {
            final Field field = SurveilPartitionDecisionProvider.class.getDeclaredField("activeSessions");
            field.setAccessible(true);
            final java.util.Map<?, ?> sessions = (java.util.Map<?, ?>) field.get(provider);
            assertEquals(sessions.size(), 1);
            return (SurveilPartitionSession) sessions.values().iterator().next();
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static Object readField(final Object target, final String fieldName) {
        try {
            final Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static List<String> requestRows(final List<String> rows, final String marker) {
        return rows.stream().filter(row -> row.contains("|REQUEST|") && row.contains(marker)).toList();
    }

    private static List<String> resultRows(final List<String> rows) {
        return rows.stream().filter(row -> row.contains("|RESULT|")).toList();
    }

    private static List<String> publicSemanticKeys(final List<String> rows) {
        return requestRows(rows, "").stream()
                .map(DecisionTraceRequestRecord::fromSerializedRequest)
                .flatMap(request -> request.getLegalCandidates().stream())
                .toList();
    }

    private static void assertExternalInvalidCandidate(final List<String> rows,
            final int expectedInvalidRows) {
        final List<String> results = resultRows(rows);
        assertEquals(results.stream().filter(row -> row.contains("|INVALID_EXTERNAL_CANDIDATE|"))
                .count(), expectedInvalidRows);
        assertTrue(results.stream().filter(row -> row.contains("|INVALID_EXTERNAL_CANDIDATE|"))
                .allMatch(row -> row.contains("|INVALID_EXTERNAL_CANDIDATE||")
                        && row.contains("|false|false|")), results.toString());
        assertTrue(rows.stream().noneMatch(row -> row.contains("|TRACE_INCOMPLETE|")), rows.toString());
    }

    private static void assertNoDecisionRows(final List<String> rows) {
        assertTrue(rows.stream().noneMatch(row -> row.contains("|REQUEST|") || row.contains("|RESULT|")),
                rows.toString());
    }

    private static List<String> captureTraceRows(final Game game, final Runnable action) {
        final Random previousRandom = MyRandom.getRandom();
        final DeterminismAuditRandom auditRandom = new DeterminismAuditRandom(20260814L);
        MyRandom.setRandom(auditRandom);
        Path directory = null;
        DeterminismTrace trace = null;
        try {
            directory = Files.createTempDirectory("frl02l2b-native-trace-");
            trace = DeterminismTrace.attach(game, 0, auditRandom, directory);
            action.run();
            trace.finish();
            final Path decisionTrace = directory.resolve("game-001.decision.trace");
            return Files.exists(decisionTrace)
                    ? Files.readAllLines(decisionTrace, StandardCharsets.UTF_8) : List.of();
        } catch (IOException exception) {
            throw new AssertionError(exception);
        } finally {
            try {
                if (trace != null) {
                    trace.finish();
                }
                if (directory != null) {
                    try (var paths = Files.walk(directory)) {
                        for (final Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                            Files.deleteIfExists(path);
                        }
                    }
                }
            } catch (Exception exception) {
                throw new AssertionError(exception);
            } finally {
                MyRandom.setRandom(previousRandom);
            }
        }
    }

    private record Fixture(Game game, Player chooser, List<Card> cards) {
    }

    private static final class FaultingCardCollection extends CardCollection {
        private final RuntimeException failure;
        private final Runnable afterRead;

        private FaultingCardCollection(final Iterable<Card> cards, final RuntimeException failure,
                final Runnable afterRead) {
            super(cards);
            this.failure = failure;
            this.afterRead = afterRead;
        }

        private static FaultingCardCollection throwing(final Iterable<Card> cards,
                final RuntimeException failure) {
            return new FaultingCardCollection(cards, failure, null);
        }

        private static FaultingCardCollection afterRead(final Runnable action, final Iterable<Card> cards) {
            return new FaultingCardCollection(cards, null, action);
        }

        @Override
        public Card get(final int index) {
            if (failure != null) {
                throw failure;
            }
            if (afterRead != null) {
                afterRead.run();
            }
            return super.get(index);
        }
    }
}
