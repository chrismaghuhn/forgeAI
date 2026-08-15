package forge.game.decision;

import forge.game.Game;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

final class SurveilPartitionSession {
    private final long surveilSessionId;
    private final Game game;
    private final int gameId;
    private final Player chooser;
    private final int choosingPlayerId;
    private final SurveilPartitionOwner selectedOwner;
    private final List<Card> nativeSnapshot;
    private final IdentityHashMap<Card, SurveilItem> nativeItems;
    private final List<SurveilItem> canonicalPolicyItems;
    private final List<SurveilPartitionCard> visibleItems;
    private final SurveilPartitionCandidateKind[] labels;
    private SurveilPartitionCandidateKind[] nativeMembershipVector;
    private final Map<String, EnumSet<SurveilPartitionCandidateKind>> symmetryLabels;
    private final Map<String, Boolean> symmetryConflicts;
    private List<Card> retainedNativeList;
    private List<SurveilPartitionCard> retainedItems;
    private List<Long> remainingRetainedItemIds;
    private List<Long> topFirstPrefix;
    private int retainedOrderStep;
    private DecisionRequest openRetainedOrderRequest;
    private List<Card> finalRetainedNativeOrder;
    private SurveilPartitionCandidateKind[] completedMembershipVector;
    private boolean l2aComplete;
    private boolean l2bComplete;
    private boolean pairReady;
    private int currentStep;
    private DecisionRequest openRequest;
    private boolean mappingFailed;
    private boolean closed;
    private String closeReason;

    SurveilPartitionSession(final long surveilSessionId, final Player chooser,
            final List<Card> privateSnapshot, final SurveilPartitionOwner selectedOwner) {
        this.surveilSessionId = surveilSessionId;
        this.selectedOwner = Objects.requireNonNull(selectedOwner, "selectedOwner");
        if (chooser == null) {
            throw new SurveilPartitionAdmissionFailure(
                    SurveilPartitionAdmissionFailureReason.UNSUPPORTED_ADMISSION,
                    "chooser authority is unavailable");
        }
        this.chooser = chooser;
        final Game capturedGame;
        final int capturedGameId;
        final int capturedChooserId;
        try {
            capturedGame = chooser.getGame();
            if (capturedGame == null) {
                throw new SurveilPartitionAdmissionFailure(
                        SurveilPartitionAdmissionFailureReason.UNSUPPORTED_ADMISSION,
                        "chooser game authority is unavailable");
            }
            capturedGameId = capturedGame.getId();
            capturedChooserId = chooser.getId();
        } catch (final SurveilPartitionAdmissionFailure failure) {
            throw failure;
        } catch (final RuntimeException failure) {
            throw new SurveilPartitionAdmissionFailure(
                    SurveilPartitionAdmissionFailureReason.UNSUPPORTED_ADMISSION,
                    "chooser authority is unavailable", failure);
        }
        this.game = capturedGame;
        this.gameId = capturedGameId;
        this.choosingPlayerId = capturedChooserId;
        if (privateSnapshot == null) {
            throw new SurveilPartitionAdmissionFailure(
                    SurveilPartitionAdmissionFailureReason.UNSUPPORTED_ADMISSION,
                    "private snapshot authority is unavailable");
        }
        this.nativeSnapshot = Collections.unmodifiableList(new ArrayList<>(privateSnapshot));
        this.nativeItems = new IdentityHashMap<>();
        this.symmetryLabels = new HashMap<>();
        this.symmetryConflicts = new HashMap<>();

        final Set<StableIdentity> stableIdentities = new HashSet<>();
        final List<SurveilItem> capturedItems = new ArrayList<>(nativeSnapshot.size());
        for (int nativeOrdinal = 0; nativeOrdinal < nativeSnapshot.size(); nativeOrdinal++) {
            final Card card = nativeSnapshot.get(nativeOrdinal);
            if (card == null) {
                throw new SurveilPartitionAdmissionFailure(
                        SurveilPartitionAdmissionFailureReason.SESSION_INTEGRITY_FAILURE,
                        "private snapshot contains a null card");
            }
            if (nativeItems.containsKey(card)) {
                throw new SurveilPartitionAdmissionFailure(
                        SurveilPartitionAdmissionFailureReason.SESSION_INTEGRITY_FAILURE,
                        "private snapshot contains duplicate native identity");
            }
            if (!isVisibleToChooser(card, chooser)) {
                throw new SurveilPartitionAdmissionFailure(
                        SurveilPartitionAdmissionFailureReason.UNSUPPORTED_ADMISSION,
                        "private snapshot is not chooser-visible");
            }
            final StableIdentity stableIdentity;
            try {
                stableIdentity = new StableIdentity(card.getId(), card.getGameTimestamp());
            } catch (final RuntimeException failure) {
                throw new SurveilPartitionAdmissionFailure(
                        SurveilPartitionAdmissionFailureReason.SESSION_INTEGRITY_FAILURE,
                        "private stable identity is unavailable", failure);
            }
            if (!stableIdentities.add(stableIdentity)) {
                throw new SurveilPartitionAdmissionFailure(
                        SurveilPartitionAdmissionFailureReason.SESSION_INTEGRITY_FAILURE,
                        "private snapshot contains duplicate stable identity");
            }

            final String visibleName;
            try {
                visibleName = card.getName();
            } catch (final RuntimeException failure) {
                throw new SurveilPartitionAdmissionFailure(
                        SurveilPartitionAdmissionFailureReason.UNSUPPORTED_ADMISSION,
                        "chooser-visible projection is unavailable", failure);
            }
            if (visibleName == null) {
                throw new SurveilPartitionAdmissionFailure(
                        SurveilPartitionAdmissionFailureReason.UNSUPPORTED_ADMISSION,
                        "chooser-visible projection is unavailable");
            }
            final SurveilPartitionCard projection = new SurveilPartitionCard(0L, visibleName);
            final SurveilItem item = new SurveilItem(card, nativeOrdinal, stableIdentity, projection);
            nativeItems.put(card, item);
            capturedItems.add(item);
        }

        capturedItems.sort(Comparator
                .comparing((SurveilItem item) -> item.symmetryKey)
                .thenComparingInt(item -> item.stableIdentity.cardId())
                .thenComparingLong(item -> item.stableIdentity.gameTimestamp()));
        final List<SurveilItem> canonicalItems = new ArrayList<>(capturedItems.size());
        final List<SurveilPartitionCard> publicItems = new ArrayList<>(capturedItems.size());
        int canonicalRank = 1;
        for (final SurveilItem item : capturedItems) {
            item.itemId = SurveilPartitionItemId.opaqueItemId(canonicalRank++);
            item.projection = new SurveilPartitionCard(item.itemId, item.symmetryKey);
            canonicalItems.add(item);
            publicItems.add(item.projection);
        }
        this.canonicalPolicyItems = List.copyOf(canonicalItems);
        this.visibleItems = List.copyOf(publicItems);
        this.labels = new SurveilPartitionCandidateKind[canonicalPolicyItems.size()];
        this.retainedNativeList = List.of();
        this.retainedItems = List.of();
        this.remainingRetainedItemIds = new ArrayList<>();
        this.topFirstPrefix = new ArrayList<>();
        this.retainedOrderStep = 0;
        this.finalRetainedNativeOrder = canonicalPolicyItems.isEmpty() ? List.of() : null;
        this.l2aComplete = canonicalPolicyItems.isEmpty();
        this.l2bComplete = canonicalPolicyItems.isEmpty();
        this.pairReady = false;
    }

    SurveilPartitionSession(final long surveilSessionId, final Player chooser,
            final List<Card> privateSnapshot) {
        this(surveilSessionId, chooser, privateSnapshot, SurveilPartitionOwner.NATIVE);
    }

    synchronized long surveilSessionId() {
        return surveilSessionId;
    }

    synchronized SurveilPartitionOwner getOwner() {
        return selectedOwner;
    }

    synchronized boolean isEmptySnapshot() {
        return nativeSnapshot.isEmpty();
    }

    synchronized boolean isComplete() {
        return l2aComplete;
    }

    synchronized boolean isRetainedTopOrderComplete() {
        return l2bComplete;
    }

    synchronized boolean isPairReady() {
        return pairReady;
    }

    synchronized boolean isClosed() {
        return closed;
    }

    synchronized boolean hasOpenRequest() {
        return openRequest != null || openRetainedOrderRequest != null;
    }

    synchronized boolean isCaptureMaterializationReady() {
        return !closed && !l2aComplete && (selectedOwner == SurveilPartitionOwner.EXTERNAL
                || nativeMembershipVector != null)
                && openRequest == null && isIdentityStable();
    }

    synchronized boolean isIdentityStable() {
        try {
            return chooser.getId() == choosingPlayerId
                    && chooser.getGame() == game
                    && chooser.getGame().getId() == gameId;
        } catch (final RuntimeException ignored) {
            return false;
        }
    }

    synchronized DecisionRequest createMembershipRequest(final long requestId) {
        if (closed) {
            throw new IllegalStateException("Surveil session is closed: " + closeReason);
        }
        if (l2aComplete) {
            if (isEmptySnapshot()) {
                return null;
            }
            throw new IllegalStateException("Surveil session is complete");
        }
        requireMembershipAuthority();
        if (!isIdentityStable()) {
            throw new IllegalStateException("Surveil session identity is stale");
        }
        if (openRequest != null || openRetainedOrderRequest != null) {
            throw new IllegalStateException("Surveil session already has an open request");
        }

        final SurveilItem currentItem = canonicalPolicyItems.get(currentStep);
        final SurveilPartitionContext context = new SurveilPartitionContext(
                SurveilPartitionProfile.SURVEIL_PARTITION, surveilSessionId, currentStep,
                choosingPlayerId, canonicalPolicyItems.size(), visibleItems, currentItem.itemId);
        final DecisionRequest request = new DecisionRequest(requestId, DecisionType.CARD_SELECTION,
                List.of(
                        LegalCandidate.surveilPartition(0,
                                SurveilPartitionCandidateKind.CLASSIFY_GRAVEYARD, currentItem.projection),
                        LegalCandidate.surveilPartition(1,
                                SurveilPartitionCandidateKind.CLASSIFY_RETAIN, currentItem.projection)),
                context);
        openRequest = request;
        return request;
    }

    synchronized void applyMembershipCandidate(final LegalCandidate candidate) {
        if (closed) {
            throw new IllegalStateException("Surveil session is closed: " + closeReason);
        }
        requireMembershipAuthority();
        if (!isIdentityStable()) {
            throw new IllegalStateException("Surveil session identity is stale");
        }
        if (l2aComplete || openRequest == null || openRetainedOrderRequest != null) {
            throw new IllegalArgumentException("Surveil session has no outstanding membership request");
        }
        if (candidate == null || !belongsToOpenRequest(candidate)) {
            throw new IllegalArgumentException("Candidate does not belong to the outstanding request");
        }

        final SurveilPartitionContext context = openRequest.getSurveilPartitionContext();
        if (context == null || context.getProfile() != SurveilPartitionProfile.SURVEIL_PARTITION
                || context.getSurveilSessionId() != surveilSessionId
                || context.getChoosingPlayerId() != choosingPlayerId
                || context.getOriginalItemCount() != canonicalPolicyItems.size()
                || context.getDecisionStepIndex() != currentStep) {
            throw new IllegalArgumentException("Surveil request context does not match the session");
        }
        if (context.getVisibleItems() != visibleItems
                && !context.getVisibleItems().equals(visibleItems)) {
            throw new IllegalArgumentException("Surveil request projection does not match the session");
        }

        final SurveilItem currentItem = canonicalPolicyItems.get(currentStep);
        if (context.getCurrentItemId() != currentItem.itemId) {
            throw new IllegalArgumentException("Surveil request item does not match the session cursor");
        }
        final SurveilPartitionCandidateKind kind = candidate.getSurveilPartitionCandidateKind();
        final SurveilPartitionCard item = candidate.getSurveilPartitionCard();
        if ((kind != SurveilPartitionCandidateKind.CLASSIFY_GRAVEYARD
                && kind != SurveilPartitionCandidateKind.CLASSIFY_RETAIN)
                || item == null
                || item.getItemId() != currentItem.itemId
                || !candidate.getSemanticKey().equals(semanticKey(kind, currentItem.itemId))
                || candidate.getKind() != null
                || candidate.getTargetKind() != null
                || candidate.getPaymentKind() != null
                || candidate.getXValue() != null
                || candidate.getModeOrdinal() != null
                || !candidate.getModeDescription().isEmpty()
                || candidate.isModeUsesTargeting()
                || candidate.getCardSelectionKind() != null
                || candidate.getCardSelectionCard() != null
                || candidate.getAttackKind() != null
                || candidate.getAttackCard() != null
                || candidate.getAttackDefender() != null
                || candidate.getBlockKind() != null
                || candidate.getBlockerCard() != null
                || candidate.getBlockAttackerCard() != null
                || candidate.getMulliganKind() != null
                || candidate.getConfirmationKind() != null
                || candidate.getOrderKind() != null
                || candidate.getOrderItem() != null
                || candidate.getCopySpellResolveFirstOrderKind() != null
                || candidate.getCopySpellResolveFirstOrderItem() != null
                || candidate.getTargetEntityId() != -1
                || !candidate.getTargetName().isEmpty()
                || candidate.getTargetZone() != null
                || candidate.getSourceCardId() != -1
                || !candidate.getSourceName().isEmpty()
                || candidate.getSourceZone() != null
                || candidate.getSourceState() != null
                || !candidate.getAbilityDescription().isEmpty()
                || candidate.getSpellAbility() != null
                || candidate.getTarget() != null
                || candidate.getMana() != null) {
            throw new IllegalArgumentException("Candidate is not an exact Surveil membership choice");
        }
        if (labels[currentStep] != null) {
            throw new IllegalArgumentException("Surveil session step was already classified");
        }

        labels[currentStep] = kind;
        currentItem.label = kind;
        final EnumSet<SurveilPartitionCandidateKind> labelsForKey = symmetryLabels.computeIfAbsent(
                currentItem.symmetryKey,
                ignored -> EnumSet.noneOf(SurveilPartitionCandidateKind.class));
        labelsForKey.add(kind);
        symmetryConflicts.put(currentItem.symmetryKey, labelsForKey.size() > 1);
        currentStep++;
        openRequest = null;
        if (currentStep == canonicalPolicyItems.size()) {
            completeMapping();
        }
    }

    synchronized Long itemIdForExactCard(final Card card) {
        final SurveilItem item = nativeItems.get(card);
        return item == null ? null : item.itemId;
    }

    synchronized List<Card> privateSnapshot() {
        return nativeSnapshot;
    }

    synchronized List<Card> externalGraveyardSnapshotOrder() {
        if (!l2aComplete || completedMembershipVector == null
                || completedMembershipVector.length != canonicalPolicyItems.size()) {
            throw new IllegalStateException("Surveil external membership is incomplete");
        }
        final List<Card> graveyard = new ArrayList<>();
        for (final Card card : nativeSnapshot) {
            final SurveilItem item = nativeItems.get(card);
            if (item == null) {
                throw new IllegalStateException("Surveil external identity mapping is incomplete");
            }
            if (item.label == SurveilPartitionCandidateKind.CLASSIFY_GRAVEYARD) {
                graveyard.add(card);
            }
        }
        return List.copyOf(graveyard);
    }

    synchronized boolean isExternalTopFirstOrder(final List<Card> retainedOrder) {
        if (!l2bComplete || retainedOrder == null || retainedOrder.size() != retainedItems.size()) {
            return false;
        }
        if (retainedItems.isEmpty()) {
            return true;
        }
        if (retainedItems.size() == 1) {
            final Long itemId = itemIdForExactCard(retainedOrder.get(0));
            return itemId != null && itemId.longValue() == retainedItems.get(0).getItemId();
        }
        if (remainingRetainedItemIds.size() != 1) {
            return false;
        }
        final List<Long> expectedItemIds = new ArrayList<>(topFirstPrefix);
        expectedItemIds.add(remainingRetainedItemIds.get(0));
        if (expectedItemIds.size() != retainedOrder.size()) {
            return false;
        }
        final Set<Long> seen = new HashSet<>();
        for (int index = 0; index < retainedOrder.size(); index++) {
            final Long itemId = itemIdForExactCard(retainedOrder.get(index));
            if (itemId == null || itemId.longValue() != expectedItemIds.get(index)
                    || !seen.add(itemId)) {
                return false;
            }
        }
        return seen.size() == retainedItems.size();
    }

    synchronized DecisionRequest createRetainedTopOrderRequest(final long requestId) {
        if (closed) {
            throw new IllegalStateException("Surveil session is closed: " + closeReason);
        }
        if (!l2aComplete) {
            throw new IllegalStateException("Surveil L2A mapping is incomplete");
        }
        if (!isIdentityStable()) {
            throw new IllegalStateException("Surveil session identity is stale");
        }
        requireMembershipAuthority();
        if (openRequest != null || openRetainedOrderRequest != null) {
            throw new IllegalStateException("Surveil session already has an open request");
        }
        if (l2bComplete) {
            return null;
        }
        if (remainingRetainedItemIds.size() < 2) {
            throw new IllegalStateException("Surveil retained order has no selectable remaining items");
        }

        final List<LegalCandidate> candidates = new ArrayList<>(remainingRetainedItemIds.size());
        for (int index = 0; index < remainingRetainedItemIds.size(); index++) {
            candidates.add(LegalCandidate.surveilRetainedTopOrder(index,
                    retainedItemForId(remainingRetainedItemIds.get(index))));
        }
        final SurveilRetainedTopOrderContext context = new SurveilRetainedTopOrderContext(
                SurveilRetainedTopOrderProfile.SURVEIL_RETAINED_TOP_ORDER,
                SurveilRetainedTopOrderDirection.TOP_FIRST, surveilSessionId, retainedOrderStep,
                choosingPlayerId, retainedItems.size(), retainedItems);
        final DecisionRequest request = new DecisionRequest(requestId, DecisionType.ORDER, candidates, context);
        openRetainedOrderRequest = request;
        return request;
    }

    synchronized void applyRetainedTopOrderCandidate(final LegalCandidate candidate) {
        if (closed) {
            throw new IllegalStateException("Surveil session is closed: " + closeReason);
        }
        if (!isIdentityStable()) {
            throw new IllegalStateException("Surveil session identity is stale");
        }
        if (!l2aComplete || l2bComplete || openRetainedOrderRequest == null || openRequest != null) {
            throw new IllegalArgumentException("Surveil session has no outstanding retained order request");
        }

        final SurveilRetainedTopOrderContext context =
                openRetainedOrderRequest.getSurveilRetainedTopOrderContext();
        if (context == null
                || context.getProfile() != SurveilRetainedTopOrderProfile.SURVEIL_RETAINED_TOP_ORDER
                || context.getDirection() != SurveilRetainedTopOrderDirection.TOP_FIRST
                || context.getSurveilSessionId() != surveilSessionId
                || context.getDecisionStepIndex() != retainedOrderStep
                || context.getChoosingPlayerId() != choosingPlayerId
                || context.getRetainedItemCount() != retainedItems.size()
                || (context.getRetainedItems() != retainedItems
                        && !context.getRetainedItems().equals(retainedItems))
                || openRetainedOrderRequest.getDecisionType() != DecisionType.ORDER
                || openRetainedOrderRequest.getCandidates().size() != remainingRetainedItemIds.size()) {
            throw new IllegalArgumentException("Surveil retained order request context does not match the session");
        }
        if (candidate == null || candidate.getCandidateId() < 0
                || candidate.getCandidateId() >= openRetainedOrderRequest.getCandidates().size()
                || openRetainedOrderRequest.getCandidates().get(candidate.getCandidateId()) != candidate) {
            throw new IllegalArgumentException("Candidate does not belong to the outstanding retained order request");
        }

        final SurveilRetainedTopOrderCandidateKind kind =
                candidate.getSurveilRetainedTopOrderCandidateKind();
        final SurveilPartitionCard item = candidate.getSurveilRetainedTopOrderCard();
        final SurveilPartitionCard canonicalItem = item == null ? null : retainedItemForId(item.getItemId());
        if (kind != SurveilRetainedTopOrderCandidateKind.SELECT_NEXT_TOP
                || item == null
                || canonicalItem == null
                || !remainingRetainedItemIds.contains(item.getItemId())
                || canonicalItem != item
                || !LegalCandidate.surveilRetainedTopOrderSemanticKey(item)
                        .equals(candidate.getSemanticKey())
                || hasUnrelatedPayload(candidate)
                || candidate.getSurveilPartitionCandidateKind() != null
                || candidate.getSurveilPartitionCard() != null) {
            throw new IllegalArgumentException("Candidate is not an exact Surveil retained order choice");
        }

        topFirstPrefix.add(item.getItemId());
        remainingRetainedItemIds.remove(item.getItemId());
        retainedOrderStep++;
        openRetainedOrderRequest = null;
        if (remainingRetainedItemIds.size() == 1) {
            completeRetainedTopOrder();
        }
    }

    synchronized List<Card> finalRetainedNativeOrder() {
        if (closed) {
            throw new IllegalStateException("Surveil session is closed: " + closeReason);
        }
        if (!l2bComplete || finalRetainedNativeOrder == null) {
            throw new IllegalStateException("Surveil retained order is incomplete");
        }
        if (!isIdentityStable()) {
            throw new IllegalStateException("Surveil session identity is stale");
        }
        return finalRetainedNativeOrder;
    }

    synchronized void markPairReady() {
        if (closed) {
            throw new IllegalStateException("Surveil session is closed: " + closeReason);
        }
        if (pairReady) {
            return;
        }
        if (!l2aComplete || !l2bComplete || openRequest != null || openRetainedOrderRequest != null) {
            throw new IllegalStateException("Surveil pair is not ready");
        }
        if (!isIdentityStable()) {
            throw new IllegalStateException("Surveil session identity is stale");
        }
        if (selectedOwner == SurveilPartitionOwner.NATIVE) {
            requireNativeMembershipVector();
        } else if (completedMembershipVector == null) {
            throw new IllegalStateException("External Surveil membership labels are incomplete");
        }
        pairReady = true;
    }

    synchronized void recordNativeMembershipVector(
            final List<SurveilPartitionCandidateKind> canonicalLabels) {
        recordNativeMembershipVector(canonicalLabels, nativeSnapshot);
    }

    List<SurveilPartitionCandidateKind> canonicalMembershipVector(final List<Card> graveyardCards) {
        Objects.requireNonNull(graveyardCards, "graveyardCards");
        final Set<Card> graveyard = Collections.newSetFromMap(new IdentityHashMap<>());
        graveyard.addAll(graveyardCards);
        final List<SurveilPartitionCandidateKind> canonicalLabels = new ArrayList<>(
                canonicalPolicyItems.size());
        for (final SurveilItem item : canonicalPolicyItems) {
            canonicalLabels.add(graveyard.contains(item.nativeCard)
                    ? SurveilPartitionCandidateKind.CLASSIFY_GRAVEYARD
                    : SurveilPartitionCandidateKind.CLASSIFY_RETAIN);
        }
        return List.copyOf(canonicalLabels);
    }

    synchronized void recordSymmetryConflicts(final List<SurveilPartitionCandidateKind> canonicalLabels) {
        Objects.requireNonNull(canonicalLabels, "canonicalLabels");
        if (canonicalLabels.size() != canonicalPolicyItems.size()) {
            throw new IllegalArgumentException("Surveil symmetry labels have the wrong cardinality");
        }
        final Map<String, EnumSet<SurveilPartitionCandidateKind>> labelsByKey = new HashMap<>();
        for (int index = 0; index < canonicalLabels.size(); index++) {
            final SurveilPartitionCandidateKind kind = Objects.requireNonNull(
                    canonicalLabels.get(index), "canonical symmetry label");
            if (kind != SurveilPartitionCandidateKind.CLASSIFY_GRAVEYARD
                    && kind != SurveilPartitionCandidateKind.CLASSIFY_RETAIN) {
                throw new IllegalArgumentException("Surveil symmetry labels contain an unapproved kind");
            }
            labelsByKey.computeIfAbsent(canonicalPolicyItems.get(index).symmetryKey,
                    ignored -> EnumSet.noneOf(SurveilPartitionCandidateKind.class)).add(kind);
        }
        labelsByKey.values().stream()
                .filter(labelsForKey -> labelsForKey.size() > 1)
                .forEach(ignored -> SurveilPartitionDiagnostics.recordSymmetryConflict());
    }

    synchronized void recordNativeMembershipVector(final List<SurveilPartitionCandidateKind> canonicalLabels,
            final List<Card> retainedNativeOrder) {
        Objects.requireNonNull(canonicalLabels, "canonicalLabels");
        Objects.requireNonNull(retainedNativeOrder, "retainedNativeOrder");
        if (closed) {
            throw new IllegalStateException("Surveil session is closed: " + closeReason);
        }
        if (selectedOwner != SurveilPartitionOwner.NATIVE) {
            throw new IllegalStateException("Native membership vector requires NATIVE ownership");
        }
        if (l2aComplete && !isEmptySnapshot()) {
            throw new IllegalStateException("Surveil session is complete");
        }
        if (openRequest != null || currentStep != 0) {
            throw new IllegalStateException("Native membership vector must be recorded before a request");
        }
        if (nativeMembershipVector != null) {
            throw new IllegalStateException("Native membership vector was already recorded");
        }
        if (canonicalLabels.size() != labels.length) {
            throw new IllegalArgumentException("Native membership vector has the wrong cardinality");
        }

        final SurveilPartitionCandidateKind[] validated =
                new SurveilPartitionCandidateKind[canonicalLabels.size()];
        for (int index = 0; index < canonicalLabels.size(); index++) {
            final SurveilPartitionCandidateKind kind = Objects.requireNonNull(
                    canonicalLabels.get(index), "native membership kind");
            if (kind != SurveilPartitionCandidateKind.CLASSIFY_GRAVEYARD
                    && kind != SurveilPartitionCandidateKind.CLASSIFY_RETAIN) {
                throw new IllegalArgumentException("Native membership vector contains an unapproved kind");
            }
            validated[index] = kind;
        }
        this.retainedNativeList = List.copyOf(retainedNativeOrder);
        nativeMembershipVector = validated;
    }

    synchronized SurveilPartitionCandidateKind nativeMembershipKindAt(final int canonicalStep) {
        if (closed) {
            throw new IllegalStateException("Surveil session is closed: " + closeReason);
        }
        if (canonicalStep < 0 || canonicalStep >= labels.length) {
            throw new IllegalArgumentException("canonicalStep must be within the item range");
        }
        if (nativeMembershipVector == null) {
            throw new IllegalStateException("Surveil native membership vector has not been recorded");
        }
        final SurveilPartitionCandidateKind kind = nativeMembershipVector[canonicalStep];
        if (kind == null) {
            throw new IllegalStateException("Surveil membership step has not been classified");
        }
        return kind;
    }

    synchronized boolean isMappingFailed() {
        return mappingFailed;
    }

    synchronized void markClosed(final String reason) {
        if (closed) {
            return;
        }
        closed = true;
        closeReason = reason == null ? "CLOSED" : reason;
        openRequest = null;
        openRetainedOrderRequest = null;
    }

    private boolean belongsToOpenRequest(final LegalCandidate candidate) {
        for (final LegalCandidate openCandidate : openRequest.getCandidates()) {
            if (openCandidate == candidate) {
                return true;
            }
        }
        return false;
    }

    private void requireMembershipAuthority() {
        if (selectedOwner != SurveilPartitionOwner.NATIVE) {
            return;
        }
        requireNativeMembershipVector();
    }

    private void requireNativeMembershipVector() {
        if (!isEmptySnapshot() && nativeMembershipVector == null) {
            throw new IllegalStateException("Surveil native membership vector has not been recorded");
        }
    }

    private void initializeRetainedOrderState() {
        final List<SurveilPartitionCard> retained = new ArrayList<>();
        for (final SurveilItem item : canonicalPolicyItems) {
            if (item.label == SurveilPartitionCandidateKind.CLASSIFY_RETAIN) {
                retained.add(item.projection);
            }
        }
        retainedItems = List.copyOf(retained);
        remainingRetainedItemIds = new ArrayList<>();
        topFirstPrefix = new ArrayList<>();
        retainedOrderStep = 0;
        openRetainedOrderRequest = null;
        if (retainedItems.isEmpty()) {
            finalRetainedNativeOrder = List.of();
            l2bComplete = true;
            return;
        }
        if (retainedItems.size() == 1) {
            finalRetainedNativeOrder = List.of(nativeCardForItemId(retainedItems.get(0).getItemId()));
            l2bComplete = true;
            return;
        }
        for (final SurveilPartitionCard item : retainedItems) {
            remainingRetainedItemIds.add(item.getItemId());
        }
        finalRetainedNativeOrder = null;
        l2bComplete = false;
    }

    private void completeRetainedTopOrder() {
        if (remainingRetainedItemIds.size() != 1) {
            throw new IllegalStateException("Surveil retained order does not have exactly one final item");
        }
        final List<Long> finalItemIds = new ArrayList<>(topFirstPrefix);
        finalItemIds.add(remainingRetainedItemIds.get(0));
        if (finalItemIds.size() != retainedItems.size()) {
            throw new IllegalStateException("Surveil retained order has the wrong cardinality");
        }
        final List<Card> finalOrder = new ArrayList<>(finalItemIds.size());
        for (final long itemId : finalItemIds) {
            final Card nativeCard = nativeCardForItemId(itemId);
            if (nativeCard == null) {
                throw new IllegalStateException("Surveil retained order item is not native-backed");
            }
            finalOrder.add(nativeCard);
        }
        finalRetainedNativeOrder = List.copyOf(finalOrder);
        l2bComplete = true;
    }

    private SurveilPartitionCard retainedItemForId(final long itemId) {
        for (final SurveilPartitionCard item : retainedItems) {
            if (item.getItemId() == itemId) {
                return item;
            }
        }
        return null;
    }

    private Card nativeCardForItemId(final long itemId) {
        for (final SurveilItem item : canonicalPolicyItems) {
            if (item.itemId == itemId) {
                return item.nativeCard;
            }
        }
        return null;
    }

    private static boolean hasUnrelatedPayload(final LegalCandidate candidate) {
        return candidate.getKind() != null
                || candidate.getTargetKind() != null
                || candidate.getPaymentKind() != null
                || candidate.getXValue() != null
                || candidate.getModeOrdinal() != null
                || !candidate.getModeDescription().isEmpty()
                || candidate.isModeUsesTargeting()
                || candidate.getCardSelectionKind() != null
                || candidate.getCardSelectionCard() != null
                || candidate.getAttackKind() != null
                || candidate.getAttackCard() != null
                || candidate.getAttackDefender() != null
                || candidate.getBlockKind() != null
                || candidate.getBlockerCard() != null
                || candidate.getBlockAttackerCard() != null
                || candidate.getMulliganKind() != null
                || candidate.getConfirmationKind() != null
                || candidate.getOrderKind() != null
                || candidate.getOrderItem() != null
                || candidate.getCopySpellResolveFirstOrderKind() != null
                || candidate.getCopySpellResolveFirstOrderItem() != null
                || candidate.getTargetEntityId() != -1
                || !candidate.getTargetName().isEmpty()
                || candidate.getTargetZone() != null
                || candidate.getSourceCardId() != -1
                || !candidate.getSourceName().isEmpty()
                || candidate.getSourceZone() != null
                || candidate.getSourceState() != null
                || !candidate.getAbilityDescription().isEmpty()
                || candidate.getSpellAbility() != null
                || candidate.getTarget() != null
                || candidate.getMana() != null;
    }

    private void completeMapping() {
        try {
            for (int index = 0; index < canonicalPolicyItems.size(); index++) {
                if (labels[index] == null || canonicalPolicyItems.get(index).label == null) {
                    throw new IllegalStateException("Surveil native mapping is incomplete");
                }
            }
            this.completedMembershipVector = labels.clone();
            initializeRetainedOrderState();
            this.l2aComplete = true;
        } catch (final RuntimeException exception) {
            mappingFailed = true;
            throw exception;
        }
    }

    private static boolean isVisibleToChooser(final Card card, final Player chooser) {
        try {
            // Surveil privately reveals the chooser's own library cards to the native chooser
            // before the public projection is emitted; CardView intentionally hides libraries.
            if (card.getZone() != null && card.getZone().getZoneType() == ZoneType.Library
                    && card.getController() == chooser) {
                return true;
            }
            return !card.isFaceDown()
                    && card.getView() != null
                    && chooser.getView() != null
                    && card.getView().canBeShownTo(chooser.getView());
        } catch (final RuntimeException ignored) {
            return false;
        }
    }

    private static String semanticKey(final SurveilPartitionCandidateKind kind, final long itemId) {
        return "SURVEIL_PARTITION|" + kind.name() + "|" + itemId;
    }

    private static final class SurveilItem {
        private final Card nativeCard;
        private final int nativeOrdinal;
        private final StableIdentity stableIdentity;
        private final String symmetryKey;
        private long itemId;
        private SurveilPartitionCard projection;
        private SurveilPartitionCandidateKind label;

        private SurveilItem(final Card nativeCard, final int nativeOrdinal,
                final StableIdentity stableIdentity, final SurveilPartitionCard projection) {
            this.nativeCard = nativeCard;
            this.nativeOrdinal = nativeOrdinal;
            this.stableIdentity = stableIdentity;
            this.symmetryKey = projection.getVisibleName();
            this.projection = projection;
        }
    }

    private record StableIdentity(int cardId, long gameTimestamp) {
    }
}
