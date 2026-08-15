package forge.game.decision;

import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.player.Player;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/** Capture-only boundary around the existing native surveil callback. */
public final class SurveilPartitionDecisionCoordinator {
    private static final String L2A_STAGE = "SURVEIL_PARTITION";
    private static final String L2B_STAGE = "SURVEIL_RETAINED_TOP_ORDER";

    private final SurveilPartitionDecisionProvider provider;

    public SurveilPartitionDecisionCoordinator(final SurveilPartitionDecisionProvider provider) {
        this.provider = Objects.requireNonNull(provider, "provider");
    }

    SurveilPartitionDecisionProvider provider() {
        return provider;
    }

    public Pair<CardCollection, CardCollection> captureNativeSurveil(final Player chooser,
            final CardCollection topN,
            final Function<CardCollection, Pair<CardCollection, CardCollection>> nativeArrange) {
        Objects.requireNonNull(nativeArrange, "nativeArrange");
        final CardCollection originalTopN = topN;
        if (originalTopN == null) {
            SurveilPartitionDiagnostics.recordCaptureAdmissionFailure("NULL_TOP_N");
            return invokeNative(originalTopN, nativeArrange);
        }
        if (originalTopN.isEmpty()) {
            SurveilPartitionDiagnostics.recordEmptyTopN();
            return invokeNative(originalTopN, nativeArrange);
        }

        final List<Card> privateSnapshot = Collections.unmodifiableList(new ArrayList<>(originalTopN));
        final SurveilPartitionOwner selectedOwner = provider.getOwner();
        SurveilPartitionDiagnostics.recordOwnerSelected(selectedOwner);
        final SurveilPartitionSession session;
        try {
            session = provider.admit(chooser, privateSnapshot, selectedOwner);
        } catch (final SurveilPartitionAdmissionFailure admissionFailure) {
            SurveilPartitionDiagnostics.recordCaptureAdmissionFailure(admissionFailure.reason().name());
            if (selectedOwner == SurveilPartitionOwner.NATIVE) {
                return invokeNative(originalTopN, nativeArrange);
            }
            throw admissionFailure;
        } catch (final RuntimeException admissionFailure) {
            SurveilPartitionDiagnostics.recordCaptureAdmissionFailure("UNKNOWN");
            if (selectedOwner == SurveilPartitionOwner.NATIVE) {
                return invokeNative(originalTopN, nativeArrange);
            }
            throw admissionFailure;
        }

        if (session.getOwner() != selectedOwner) {
            provider.closeSession(session);
            throw new SurveilPartitionAdmissionFailure(
                    SurveilPartitionAdmissionFailureReason.SESSION_INTEGRITY_FAILURE,
                    "Surveil owner changed during admission");
        }
        SurveilPartitionDiagnostics.recordSessionSize(privateSnapshot.size());
        if (selectedOwner == SurveilPartitionOwner.EXTERNAL) {
            return captureExternalSurveil(chooser, session);
        }

        try {
            final Pair<CardCollection, CardCollection> nativePair = invokeNative(originalTopN, nativeArrange);
            final NativeCapturePlan plan;
            try {
                plan = buildNativeCapturePlan(session, privateSnapshot, nativePair);
            } catch (final RuntimeException preHandleFailure) {
                SurveilPartitionDiagnostics.recordPreHandleCaptureFailure("IDENTITY");
                SurveilPartitionDiagnostics.recordMapping(false, "IDENTITY");
                return nativePair;
            }

            final MaterializationState state = new MaterializationState();
            try {
                materializeNativeCapture(chooser, session, plan, state);
                provider.markPairReady(session);
                SurveilPartitionDiagnostics.recordPairReady();
                SurveilPartitionDiagnostics.recordMapping(true, "VALID");
                return nativePair;
            } catch (final RuntimeException postHandleFailure) {
                terminalizeCurrentHandle(state);
                final String terminal = state.mappingAttempted ? "MAPPING_FAILED" : "UNOBSERVED";
                SurveilPartitionDiagnostics.recordPostHandleCaptureFailure(terminal);
                SurveilPartitionDiagnostics.recordMapping(false, terminal);
                return nativePair;
            }
        } finally {
            provider.closeSession(session);
        }
    }

    private Pair<CardCollection, CardCollection> captureExternalSurveil(final Player chooser,
            final SurveilPartitionSession session) {
        final MaterializationState state = new MaterializationState();
        boolean pairReady = false;
        try {
            resolveExternalMembership(chooser, session, state);
            resolveExternalRetainedOrder(chooser, session, state);
            final Pair<CardCollection, CardCollection> pair = synthesizeExternalPair(session);
            provider.markPairReady(session);
            // PAIR_READY is the authoritative boundary: all following work is secondary bookkeeping.
            pairReady = true;
            try {
                try {
                    SurveilPartitionDiagnostics.recordPairReady();
                } catch (final RuntimeException bookkeepingFailure) {
                    // Diagnostics must not suppress an already authoritative Pair.
                }
                try {
                    SurveilPartitionDiagnostics.recordMapping(true, "VALID");
                } catch (final RuntimeException bookkeepingFailure) {
                    // Diagnostics must not suppress an already authoritative Pair.
                }
            } finally {
                try {
                    provider.closeSession(session);
                } catch (final RuntimeException cleanupFailure) {
                    // Registry cleanup is secondary after PAIR_READY; the Pair remains authoritative.
                }
            }
            return pair;
        } catch (final RuntimeException failure) {
            if (!pairReady) {
                try {
                    provider.closeSession(session);
                } catch (final RuntimeException cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
            }
            throw failure;
        }
    }

    private void resolveExternalMembership(final Player chooser, final SurveilPartitionSession session,
            final MaterializationState state) {
        for (int step = 0; step < session.privateSnapshot().size(); step++) {
            final DecisionRequest request = provider.createMembershipRequest(session);
            if (request == null) {
                throw new IllegalStateException("external Surveil L2A ended before completion");
            }
            state.handle = recordL2ARequest(chooser, request, step);
            final LegalCandidate chosen;
            try {
                final SurveilPartitionDecisionProvider.Resolver resolver = provider.getResolver();
                chosen = resolver == null ? null : resolver.choose(request);
            } catch (final RuntimeException resolverFailure) {
                state.handle.recordInvalidExternalCandidate();
                throw resolverFailure;
            }
            if (chosen == null) {
                state.handle.recordInvalidExternalCandidate();
                throw new IllegalStateException("external Surveil resolver returned no candidate");
            }
            try {
                provider.applyMembershipCandidate(session, chosen);
                state.handle.recordExternalChosenResult(chosen);
                SurveilPartitionDiagnostics.recordMembershipResult();
                state.clear();
            } catch (final RuntimeException candidateFailure) {
                state.handle.recordInvalidExternalCandidate();
                throw candidateFailure;
            }
        }
    }

    private void resolveExternalRetainedOrder(final Player chooser, final SurveilPartitionSession session,
            final MaterializationState state) {
        int step = 0;
        while (!provider.isRetainedTopOrderComplete(session)) {
            final DecisionRequest request = provider.createRetainedTopOrderRequest(session);
            if (request == null) {
                throw new IllegalStateException("external Surveil L2B ended before completion");
            }
            state.handle = recordL2BRequest(chooser, request, step++);
            final LegalCandidate chosen;
            try {
                final SurveilPartitionDecisionProvider.Resolver resolver = provider.getResolver();
                chosen = resolver == null ? null : resolver.choose(request);
            } catch (final RuntimeException resolverFailure) {
                state.handle.recordInvalidExternalCandidate();
                throw resolverFailure;
            }
            if (chosen == null) {
                state.handle.recordInvalidExternalCandidate();
                throw new IllegalStateException("external Surveil resolver returned no candidate");
            }
            try {
                provider.applyRetainedTopOrderCandidate(session, chosen);
                state.handle.recordExternalChosenResult(chosen);
                SurveilPartitionDiagnostics.recordRetainedOrderResult();
                state.clear();
            } catch (final RuntimeException candidateFailure) {
                state.handle.recordInvalidExternalCandidate();
                throw candidateFailure;
            }
        }
    }

    private Pair<CardCollection, CardCollection> synthesizeExternalPair(
            final SurveilPartitionSession session) {
        final List<Card> snapshot = session.privateSnapshot();
        final List<Card> retained = provider.finalRetainedNativeOrder(session);
        final List<Card> graveyard = session.externalGraveyardSnapshotOrder();
        validateExternalPairInputs(session, snapshot, retained, graveyard);

        final CardCollection pairLeft = new CardCollection();
        pairLeft.addAll(retained);
        final CardCollection pairRight = new CardCollection();
        pairRight.addAll(graveyard);
        return new ImmutablePair<>(pairLeft, pairRight);
    }

    private static void validateExternalPairInputs(final SurveilPartitionSession session,
            final List<Card> snapshot,
            final List<Card> retained, final List<Card> graveyard) {
        if (snapshot == null || retained == null || graveyard == null
                || retained.size() + graveyard.size() != snapshot.size()) {
            throw new IllegalStateException("external Surveil Pair cardinality is inconsistent");
        }
        if (!session.isExternalTopFirstOrder(retained)) {
            throw new IllegalStateException("external Surveil retained order is not TOP_FIRST");
        }
        final IdentityHashMap<Card, Boolean> expected = new IdentityHashMap<>();
        for (final Card card : snapshot) {
            if (card == null || expected.put(card, Boolean.TRUE) != null) {
                throw new IllegalStateException("external Surveil snapshot identity is inconsistent");
            }
        }
        final IdentityHashMap<Card, Boolean> seen = new IdentityHashMap<>();
        validateExternalPairSide(retained, expected, seen, "retained");
        validateExternalPairSide(graveyard, expected, seen, "graveyard");
        if (seen.size() != expected.size()) {
            throw new IllegalStateException("external Surveil Pair does not cover the snapshot");
        }
    }

    private static void validateExternalPairSide(final List<Card> side,
            final IdentityHashMap<Card, Boolean> expected, final IdentityHashMap<Card, Boolean> seen,
            final String label) {
        for (final Card card : side) {
            if (card == null || expected.get(card) == null || seen.put(card, Boolean.TRUE) != null) {
                throw new IllegalStateException("external Surveil Pair has an invalid " + label + " side");
            }
        }
    }

    int activeSessionCount() {
        return provider.activeSessionCount();
    }

    private void materializeNativeCapture(final Player chooser, final SurveilPartitionSession session,
            final NativeCapturePlan plan, final MaterializationState state) {
        for (int step = 0; step < plan.membershipVector.size(); step++) {
            final DecisionRequest request = provider.createMembershipRequest(session);
            if (request == null) {
                throw new IllegalStateException("native Surveil L2A request ended before the plan");
            }
            final SurveilPartitionCandidateKind expectedKind = plan.membershipVector.get(step);
            final LegalCandidate chosen = request.getCandidates().stream()
                    .filter(candidate -> candidate.getSurveilPartitionCandidateKind() == expectedKind)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("native membership candidate is not legal"));
            state.handle = recordL2ARequest(chooser, request, step);
            state.mappingAttempted = true;
            provider.applyMembershipCandidate(session, chosen);
            state.handle.recordNativeMappedResult(chosen);
            SurveilPartitionDiagnostics.recordMembershipResult();
            state.clear();
        }

        for (int step = 0; step < plan.retainedItemIds.size() - 1; step++) {
            final DecisionRequest request = provider.createRetainedTopOrderRequest(session);
            if (request == null) {
                throw new IllegalStateException("native Surveil L2B request ended before the plan");
            }
            final long expectedItemId = plan.retainedItemIds.get(step);
            final LegalCandidate chosen = request.getCandidates().stream()
                    .filter(candidate -> candidate.getSurveilRetainedTopOrderCard() != null
                            && candidate.getSurveilRetainedTopOrderCard().getItemId() == expectedItemId)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("native retained order candidate is not legal"));
            state.handle = recordL2BRequest(chooser, request, step);
            final Card authoritativeCard = plan.nativePair.getLeft().get(step);
            if (authoritativeCard != plan.retainedCards.get(step)) {
                throw new IllegalStateException("native retained order changed after prevalidation");
            }
            state.mappingAttempted = true;
            provider.applyRetainedTopOrderCandidate(session, chosen);
            state.handle.recordNativeMappedResult(chosen);
            SurveilPartitionDiagnostics.recordRetainedOrderResult();
            state.clear();
        }

        if (!provider.isRetainedTopOrderComplete(session)) {
            throw new IllegalStateException("native Surveil L2B materialization is incomplete");
        }
        final List<Card> derivedOrder = provider.finalRetainedNativeOrder(session);
        if (!sameIdentityOrder(derivedOrder, plan.retainedCards)) {
            throw new IllegalStateException("native retained order was not preserved");
        }
    }

    private DeterminismTrace.RequestHandle recordL2ARequest(final Player chooser,
            final DecisionRequest request, final int step) {
        SurveilPartitionDiagnostics.recordMembershipRequest();
        return DeterminismTrace.recordRequest(chooser.getGame(), chooser.getId(), request,
                L2A_STAGE, step, DecisionTraceRequestRecord.Profile.SURVEIL_PARTITION,
                DecisionTraceTeacherLabelEligibility.NOT_APPLICABLE);
    }

    private DeterminismTrace.RequestHandle recordL2BRequest(final Player chooser,
            final DecisionRequest request, final int step) {
        SurveilPartitionDiagnostics.recordRetainedOrderRequest(request.getCandidates().size());
        return DeterminismTrace.recordRequest(chooser.getGame(), chooser.getId(), request,
                L2B_STAGE, step, DecisionTraceRequestRecord.Profile.SURVEIL_RETAINED_TOP_ORDER,
                DecisionTraceTeacherLabelEligibility.NOT_APPLICABLE);
    }

    private static NativeCapturePlan buildNativeCapturePlan(final SurveilPartitionSession session,
            final List<Card> privateSnapshot, final Pair<CardCollection, CardCollection> nativePair) {
        if (nativePair == null || nativePair.getLeft() == null || nativePair.getRight() == null) {
            throw new IllegalArgumentException("native Surveil Pair must contain both collections");
        }
        final List<Card> retained = List.copyOf(new ArrayList<>(nativePair.getLeft()));
        final List<Card> graveyard = List.copyOf(new ArrayList<>(nativePair.getRight()));
        final IdentityHashMap<Card, Boolean> expected = new IdentityHashMap<>();
        for (final Card card : privateSnapshot) {
            if (card == null || expected.put(card, Boolean.TRUE) != null) {
                throw new IllegalArgumentException("native snapshot is not an identity set");
            }
        }
        final IdentityHashMap<Card, Boolean> seen = new IdentityHashMap<>();
        validatePartitionSide(retained, expected, seen);
        validatePartitionSide(graveyard, expected, seen);
        if (seen.size() != expected.size()) {
            throw new IllegalArgumentException("native Pair does not cover the snapshot");
        }

        final List<Long> retainedItemIds = new ArrayList<>(retained.size());
        final Set<Long> retainedIdSet = new HashSet<>();
        for (final Card card : retained) {
            final Long itemId = session.itemIdForExactCard(card);
            if (itemId == null || !retainedIdSet.add(itemId)) {
                throw new IllegalArgumentException("native retained order is not a permutation");
            }
            retainedItemIds.add(itemId);
        }
        if (retainedItemIds.size() != retained.size()) {
            throw new IllegalArgumentException("native retained order has the wrong cardinality");
        }

        final List<SurveilPartitionCandidateKind> membershipVector =
                session.canonicalMembershipVector(graveyard);
        session.recordNativeMembershipVector(membershipVector, retained);
        session.recordSymmetryConflicts(membershipVector);
        SurveilPartitionDiagnostics.recordN2Cardinality(graveyard.size(), retained.size());
        return new NativeCapturePlan(nativePair, retained, retainedItemIds, membershipVector);
    }

    private static void validatePartitionSide(final List<Card> side,
            final IdentityHashMap<Card, Boolean> expected, final IdentityHashMap<Card, Boolean> seen) {
        for (final Card card : side) {
            if (card == null || expected.get(card) == null || seen.put(card, Boolean.TRUE) != null) {
                throw new IllegalArgumentException("native Pair is not an exact identity partition");
            }
        }
    }

    private static boolean sameIdentityOrder(final List<Card> actual, final List<Card> expected) {
        if (actual == null || actual.size() != expected.size()) {
            return false;
        }
        for (int index = 0; index < expected.size(); index++) {
            if (actual.get(index) != expected.get(index)) {
                return false;
            }
        }
        return true;
    }

    private static Pair<CardCollection, CardCollection> invokeNative(final CardCollection originalTopN,
            final Function<CardCollection, Pair<CardCollection, CardCollection>> nativeArrange) {
        try {
            SurveilPartitionDiagnostics.recordArrangeCall();
            final Pair<CardCollection, CardCollection> nativePair = nativeArrange.apply(originalTopN);
            SurveilPartitionDiagnostics.recordCallback(false);
            return nativePair;
        } catch (final RuntimeException failure) {
            SurveilPartitionDiagnostics.recordCallback(true);
            throw failure;
        }
    }

    private static void terminalizeCurrentHandle(final MaterializationState state) {
        if (state.handle == null || !state.handle.isActive()
                || state.handle.getResultRecord().isPresent()) {
            return;
        }
        if (state.mappingAttempted) {
            state.handle.recordMappingFailed();
        } else {
            state.handle.recordUnobserved();
        }
    }

    private static final class MaterializationState {
        private DeterminismTrace.RequestHandle handle;
        private boolean mappingAttempted;

        private void clear() {
            handle = null;
            mappingAttempted = false;
        }
    }

    private record NativeCapturePlan(Pair<CardCollection, CardCollection> nativePair,
            List<Card> retainedCards, List<Long> retainedItemIds,
            List<SurveilPartitionCandidateKind> membershipVector) {
        private NativeCapturePlan {
            retainedCards = List.copyOf(retainedCards);
            retainedItemIds = List.copyOf(retainedItemIds);
            membershipVector = List.copyOf(membershipVector);
        }
    }
}
