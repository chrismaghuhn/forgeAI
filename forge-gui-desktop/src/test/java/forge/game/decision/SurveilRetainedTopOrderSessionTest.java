package forge.game.decision;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.card.CardFactory;
import forge.game.player.Player;
import forge.game.zone.ZoneType;
import org.testng.annotations.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

public class SurveilRetainedTopOrderSessionTest extends AITest {
    @Test
    public void ownerAndResolverUseTheControllerLocalDefaults() {
        final SurveilPartitionDecisionProvider provider = new SurveilPartitionDecisionProvider();
        assertEquals(provider.getOwner(), SurveilPartitionOwner.NATIVE);
        assertNull(provider.getResolver());
        assertFalse(provider.hasResolver());

        final SurveilPartitionDecisionProvider.Resolver resolver = request -> request.getCandidates().get(0);
        provider.setResolver(resolver);
        assertEquals(provider.getResolver(), resolver);
        assertTrue(provider.hasResolver());
        provider.setResolver(null);
        assertFalse(provider.hasResolver());
    }

    @Test
    public void externalOwnerWithoutResolverDoesNotFallBackToNative() {
        final Fixture fixture = fixture("Island", "Forest");
        final SurveilPartitionDecisionProvider provider = externalProvider();

        final SurveilPartitionSession session = provider.admit(fixture.chooser(), fixture.cards());

        assertEquals(session.getOwner(), SurveilPartitionOwner.EXTERNAL);
        assertNotNull(provider.createMembershipRequest(session));
        provider.closeSession(session);
    }

    @Test
    public void selectedOwnerIsImmutableAfterAdmission() {
        final Fixture fixture = fixture("Island");
        final SurveilPartitionDecisionProvider provider = new SurveilPartitionDecisionProvider();
        provider.setOwner(SurveilPartitionOwner.EXTERNAL);

        final SurveilPartitionSession session = provider.admit(fixture.chooser(), fixture.cards());
        provider.setOwner(SurveilPartitionOwner.NATIVE);

        assertEquals(session.getOwner(), SurveilPartitionOwner.EXTERNAL);
        final DecisionRequest request = provider.createMembershipRequest(session);
        final LegalCandidate retained = request.getCandidates().stream()
                .filter(candidate -> candidate.getSurveilPartitionCandidateKind()
                        == SurveilPartitionCandidateKind.CLASSIFY_RETAIN)
                .findFirst()
                .orElseThrow();
        provider.applyMembershipCandidate(session, retained);
        assertTrue(session.isComplete());
        assertNull(readField(session, "nativeMembershipVector"));
        provider.closeSession(session);
    }

    @Test
    public void foreignProviderCloseDoesNotCloseOrUnrouteTheOriginSession() {
        final Fixture fixture = fixture("Island", "Forest");
        final SurveilPartitionDecisionProvider originProvider = externalProvider();
        final SurveilPartitionDecisionProvider foreignProvider = externalProvider();
        final SurveilPartitionSession originSession = originProvider.admit(
                fixture.chooser(), fixture.cards());
        final SurveilPartitionSession foreignSession = foreignProvider.admit(
                fixture.chooser(), fixture.cards());

        foreignProvider.closeSession(originSession);

        assertFalse(originSession.isClosed());
        assertEquals(originProvider.activeSessionCount(), 1);
        assertFalse(foreignSession.isClosed());
        assertEquals(foreignProvider.activeSessionCount(), 1);

        originProvider.closeSession(originSession);
        foreignProvider.closeSession(foreignSession);
    }

    @Test
    public void staleFinalRetainedOrderHandoffFailsClosedAfterIdentityDrift() {
        final Fixture fixture = fixture("Island", "Forest", "Mountain");
        final Fixture otherFixture = fixture("Swamp");
        final SurveilPartitionDecisionProvider provider = externalProvider();
        final SurveilPartitionSession session = provider.admit(fixture.chooser(), fixture.cards());
        completeL2A(provider, session, Collections.nCopies(3,
                SurveilPartitionCandidateKind.CLASSIFY_RETAIN));

        while (!session.isRetainedTopOrderComplete()) {
            final DecisionRequest request = provider.createRetainedTopOrderRequest(session);
            provider.applyRetainedTopOrderCandidate(session, request.getCandidates().get(0));
        }
        assertNotNull(session.finalRetainedNativeOrder());

        replaceField(session, "game", otherFixture.game());

        expectThrows(IllegalStateException.class, session::finalRetainedNativeOrder);
        assertFalse(session.isPairReady());
        provider.closeSession(session);
    }

    @Test
    public void externalL2ACompletesWithoutNativeVectorAndPublishesAuthoritativeLabels() {
        final Fixture fixture = fixture("Island", "Forest", "Mountain");
        final SurveilPartitionDecisionProvider provider = externalProvider();
        final SurveilPartitionSession session = provider.admit(fixture.chooser(), fixture.cards());
        final List<SurveilPartitionCandidateKind> expected = List.of(
                SurveilPartitionCandidateKind.CLASSIFY_RETAIN,
                SurveilPartitionCandidateKind.CLASSIFY_GRAVEYARD,
                SurveilPartitionCandidateKind.CLASSIFY_RETAIN);

        completeL2A(provider, session, expected);

        assertTrue(session.isComplete());
        assertNull(readField(session, "nativeMembershipVector"));
        assertEquals(readField(session, "completedMembershipVector"),
                expected.toArray(new SurveilPartitionCandidateKind[0]));
        provider.closeSession(session);
    }

    @Test
    public void everyL2BContextKeepsTheL2ASessionIdAndItemIds() {
        final Fixture fixture = fixture("Island", "Forest", "Mountain");
        final SurveilPartitionDecisionProvider provider = externalProvider();
        final SurveilPartitionSession session = provider.admit(fixture.chooser(), fixture.cards());
        completeL2A(provider, session, Collections.nCopies(3,
                SurveilPartitionCandidateKind.CLASSIFY_RETAIN));

        final List<Long> retainedIds = retainedItemIds(session);
        final long sessionId = session.surveilSessionId();
        int expectedStep = 0;
        while (!session.isRetainedTopOrderComplete()) {
            final DecisionRequest request = session.createRetainedTopOrderRequest(100L + expectedStep);
            final SurveilRetainedTopOrderContext context = request.getSurveilRetainedTopOrderContext();
            assertEquals(context.getSurveilSessionId(), sessionId);
            assertEquals(context.getDecisionStepIndex(), expectedStep);
            assertEquals(context.getRetainedItems().stream()
                    .map(SurveilPartitionCard::getItemId).collect(Collectors.toList()), retainedIds);
            assertEquals(request.getCandidates().stream()
                    .map(candidate -> candidate.getSurveilRetainedTopOrderCard().getItemId())
                    .collect(Collectors.toList()), retainedIds.subList(expectedStep, retainedIds.size()));
            session.applyRetainedTopOrderCandidate(request.getCandidates().get(0));
            expectedStep++;
        }

        assertEquals(expectedStep, 2);
        provider.closeSession(session);
    }

    @Test
    public void zeroRetainedCardsHaveNoL2BRequestOrState() {
        final Fixture fixture = fixture("Island", "Forest");
        final SurveilPartitionDecisionProvider provider = externalProvider();
        final SurveilPartitionSession session = provider.admit(fixture.chooser(), fixture.cards());

        completeL2A(provider, session, Collections.nCopies(2,
                SurveilPartitionCandidateKind.CLASSIFY_GRAVEYARD));

        assertTrue(session.isRetainedTopOrderComplete());
        assertNull(session.createRetainedTopOrderRequest(200L));
        assertNull(provider.createRetainedTopOrderRequest(session));
        assertEquals(session.finalRetainedNativeOrder(), List.of());
        assertEquals(readField(session, "retainedItems"), List.of());
        assertEquals(readField(session, "remainingRetainedItemIds"), List.of());
        assertNull(readField(session, "openRetainedOrderRequest"));
        provider.closeSession(session);
    }

    @Test
    public void oneRetainedCardHasNoL2BRequestAndDerivesTheSoleCard() {
        final Fixture fixture = fixture("Island", "Forest");
        final SurveilPartitionDecisionProvider provider = externalProvider();
        final SurveilPartitionSession session = provider.admit(fixture.chooser(), fixture.cards());
        completeL2A(provider, session, List.of(
                SurveilPartitionCandidateKind.CLASSIFY_RETAIN,
                SurveilPartitionCandidateKind.CLASSIFY_GRAVEYARD));

        assertTrue(session.isRetainedTopOrderComplete());
        assertNull(session.createRetainedTopOrderRequest(201L));
        assertEquals(session.finalRetainedNativeOrder().size(), 1);
        assertEquals(session.finalRetainedNativeOrder().get(0),
                nativeCardForItem(session, retainedItemIds(session).get(0)));
        provider.closeSession(session);
    }

    @Test
    public void twoRetainedCardsUseOneRequestAndBecomeCompleteBeforePairReady() {
        final Fixture fixture = fixture("Island", "Forest", "Mountain");
        final SurveilPartitionDecisionProvider provider = externalProvider();
        final SurveilPartitionSession session = provider.admit(fixture.chooser(), fixture.cards());
        completeL2A(provider, session, List.of(
                SurveilPartitionCandidateKind.CLASSIFY_RETAIN,
                SurveilPartitionCandidateKind.CLASSIFY_RETAIN,
                SurveilPartitionCandidateKind.CLASSIFY_GRAVEYARD));

        final DecisionRequest request = provider.createRetainedTopOrderRequest(session);
        assertNotNull(request);
        assertFalse(request.isForced());
        assertEquals(request.getCandidates().size(), 2);
        provider.applyRetainedTopOrderCandidate(session, request.getCandidates().get(1));

        assertTrue(session.isRetainedTopOrderComplete());
        assertFalse(session.isPairReady());
        assertNull(provider.createRetainedTopOrderRequest(session));
        assertEquals(session.finalRetainedNativeOrder(), List.of(
                nativeCardForItem(session, retainedItemIds(session).get(1)),
                nativeCardForItem(session, retainedItemIds(session).get(0))));
        provider.closeSession(session);
    }

    @Test
    public void retainedOrderRequestsShrinkFromRThroughTwoAndHaveNoLastItemRequest() {
        final Fixture fixture = fixture("Island", "Forest", "Mountain", "Swamp");
        final SurveilPartitionDecisionProvider provider = externalProvider();
        final SurveilPartitionSession session = provider.admit(fixture.chooser(), fixture.cards());
        completeL2A(provider, session, Collections.nCopies(4,
                SurveilPartitionCandidateKind.CLASSIFY_RETAIN));

        final List<Integer> candidateCounts = new java.util.ArrayList<>();
        while (!session.isRetainedTopOrderComplete()) {
            final DecisionRequest request = provider.createRetainedTopOrderRequest(session);
            candidateCounts.add(request.getCandidates().size());
            provider.applyRetainedTopOrderCandidate(session, request.getCandidates().get(0));
        }

        assertEquals(candidateCounts, List.of(4, 3, 2));
        assertNull(provider.createRetainedTopOrderRequest(session));
        assertEquals(session.finalRetainedNativeOrder().size(), 4);
        provider.closeSession(session);
    }

    @Test
    public void canonicalRemainingOrderSurvivesANonCanonicalFirstChoice() {
        final Fixture fixture = fixture("Island", "Forest", "Mountain");
        final SurveilPartitionDecisionProvider provider = externalProvider();
        final SurveilPartitionSession session = provider.admit(fixture.chooser(), fixture.cards());
        completeL2A(provider, session, Collections.nCopies(3,
                SurveilPartitionCandidateKind.CLASSIFY_RETAIN));
        final List<Long> retainedIds = retainedItemIds(session);

        final DecisionRequest first = provider.createRetainedTopOrderRequest(session);
        provider.applyRetainedTopOrderCandidate(session, first.getCandidates().get(1));
        final DecisionRequest second = provider.createRetainedTopOrderRequest(session);

        assertEquals(second.getCandidates().stream()
                .map(candidate -> candidate.getSurveilRetainedTopOrderCard().getItemId())
                .collect(Collectors.toList()), List.of(retainedIds.get(0), retainedIds.get(2)));
        provider.closeSession(session);
    }

    @Test
    public void canonicalOrderUsesJavaCompareToThenCardIdThenGameTimestampAndIgnoresSnapshotOrdinal() {
        final Fixture fixture = canonicalFixture();
        final List<Card> input = fixture.cards();
        final List<Card> expectedCanonicalCards = List.of(
                input.get(1), input.get(3), input.get(2), input.get(0));
        final List<Card> permutedInput = List.of(
                input.get(0), input.get(2), input.get(1), input.get(3));
        final SurveilPartitionDecisionProvider firstProvider = externalProvider();
        final SurveilPartitionDecisionProvider secondProvider = externalProvider();
        final SurveilPartitionSession firstSession = firstProvider.admit(
                fixture.chooser(), input, SurveilPartitionOwner.EXTERNAL);
        final SurveilPartitionSession secondSession = secondProvider.admit(
                fixture.chooser(), permutedInput, SurveilPartitionOwner.EXTERNAL);

        final DecisionRequest firstL2A = firstProvider.createMembershipRequest(firstSession);
        final DecisionRequest secondL2A = secondProvider.createMembershipRequest(secondSession);
        final List<String> expectedNames = List.of("B", "a", "a", "a");
        assertEquals(firstL2A.getSurveilPartitionContext().getVisibleItems().stream()
                .map(SurveilPartitionCard::getVisibleName).collect(Collectors.toList()), expectedNames);
        assertEquals(secondL2A.getSurveilPartitionContext().getVisibleItems().stream()
                .map(SurveilPartitionCard::getVisibleName).collect(Collectors.toList()), expectedNames);
        assertEquals(publicProjection(firstL2A.getSurveilPartitionContext().getVisibleItems()), List.of(
                SurveilPartitionItemId.opaqueItemId(1) + "|B",
                SurveilPartitionItemId.opaqueItemId(2) + "|a",
                SurveilPartitionItemId.opaqueItemId(3) + "|a",
                SurveilPartitionItemId.opaqueItemId(4) + "|a"));
        assertEquals(publicProjection(firstL2A.getSurveilPartitionContext().getVisibleItems()),
                publicProjection(secondL2A.getSurveilPartitionContext().getVisibleItems()));
        assertEquals(itemIdsForCards(firstSession, expectedCanonicalCards),
                firstL2A.getSurveilPartitionContext().getVisibleItems().stream()
                        .map(SurveilPartitionCard::getItemId).collect(Collectors.toList()));
        assertEquals(itemIdsForCards(firstSession, input), itemIdsForCards(secondSession, input));
        final Map<Card, Integer> firstNativeOrdinals = nativeOrdinals(firstSession);
        final Map<Card, Integer> secondNativeOrdinals = nativeOrdinals(secondSession);
        assertNotEquals(firstNativeOrdinals.get(input.get(1)), secondNativeOrdinals.get(input.get(1)));
        assertNotEquals(firstNativeOrdinals.get(input.get(2)), secondNativeOrdinals.get(input.get(2)));

        completeAllRetained(firstProvider, firstSession, firstL2A);
        completeAllRetained(secondProvider, secondSession, secondL2A);
        final DecisionRequest firstL2B = firstProvider.createRetainedTopOrderRequest(firstSession);
        final DecisionRequest secondL2B = secondProvider.createRetainedTopOrderRequest(secondSession);
        final List<Long> l2aItemIds = firstL2A.getSurveilPartitionContext().getVisibleItems().stream()
                .map(SurveilPartitionCard::getItemId).collect(Collectors.toList());
        assertEquals(firstL2B.getSurveilRetainedTopOrderContext().getRetainedItems().stream()
                .map(SurveilPartitionCard::getItemId).collect(Collectors.toList()), l2aItemIds);
        assertEquals(firstL2B.getCandidates().stream()
                .map(candidate -> candidate.getSurveilRetainedTopOrderCard().getItemId())
                .collect(Collectors.toList()), l2aItemIds);
        assertEquals(firstL2B.getCandidates().stream()
                .map(candidate -> candidate.getSurveilRetainedTopOrderCard().getVisibleName())
                .collect(Collectors.toList()), expectedNames);
        assertEquals(publicProjection(firstL2B.getSurveilRetainedTopOrderContext().getRetainedItems()),
                publicProjection(secondL2B.getSurveilRetainedTopOrderContext().getRetainedItems()));
        assertNotEquals(l2aItemIds, l2aItemIds.stream().sorted().collect(Collectors.toList()));

        firstProvider.closeSession(firstSession);
        secondProvider.closeSession(secondSession);
    }

    @Test
    public void secondL2BCandidatePresentationAndActionsAreSnapshotPermutationInvariant() {
        final Fixture fixture = canonicalFixture();
        final List<Card> input = fixture.cards();
        final List<Card> firstSnapshot = List.of(input.get(0), input.get(1), input.get(2), input.get(3));
        final List<Card> secondSnapshot = List.of(input.get(2), input.get(0), input.get(3), input.get(1));
        final SurveilPartitionDecisionProvider firstProvider = externalProvider();
        final SurveilPartitionDecisionProvider secondProvider = externalProvider();
        final SurveilPartitionSession firstSession = firstProvider.admit(
                fixture.chooser(), firstSnapshot, SurveilPartitionOwner.EXTERNAL);
        final SurveilPartitionSession secondSession = secondProvider.admit(
                fixture.chooser(), secondSnapshot, SurveilPartitionOwner.EXTERNAL);

        final DecisionRequest firstL2A = firstProvider.createMembershipRequest(firstSession);
        final DecisionRequest secondL2A = secondProvider.createMembershipRequest(secondSession);
        completeAllRetained(firstProvider, firstSession, firstL2A);
        completeAllRetained(secondProvider, secondSession, secondL2A);

        DecisionRequest firstL2B = firstProvider.createRetainedTopOrderRequest(firstSession);
        DecisionRequest secondL2B = secondProvider.createRetainedTopOrderRequest(secondSession);
        final int[] actionSequence = {1, 0, 1};
        for (int step = 0; step < actionSequence.length; step++) {
            assertEquals(l2bPresentation(firstL2B), l2bPresentation(secondL2B));
            final int choiceIndex = actionSequence[step];
            assertEquals(firstL2B.getCandidates().get(choiceIndex).getSemanticKey(),
                    secondL2B.getCandidates().get(choiceIndex).getSemanticKey());
            firstProvider.applyRetainedTopOrderCandidate(firstSession,
                    firstL2B.getCandidates().get(choiceIndex));
            secondProvider.applyRetainedTopOrderCandidate(secondSession,
                    secondL2B.getCandidates().get(choiceIndex));
            if (step + 1 < actionSequence.length) {
                firstL2B = firstProvider.createRetainedTopOrderRequest(firstSession);
                secondL2B = secondProvider.createRetainedTopOrderRequest(secondSession);
            }
        }

        assertTrue(firstProvider.isRetainedTopOrderComplete(firstSession));
        assertTrue(secondProvider.isRetainedTopOrderComplete(secondSession));
        assertEquals(itemIdsForCards(firstSession, firstProvider.finalRetainedNativeOrder(firstSession)),
                itemIdsForCards(secondSession, secondProvider.finalRetainedNativeOrder(secondSession)));
        firstProvider.closeSession(firstSession);
        secondProvider.closeSession(secondSession);
    }

    @Test
    public void duplicateVisibleNamesRemainDistinctByItemId() {
        final Fixture fixture = customFixture(
                new CardSpec("Island", 9501, 750001L),
                new CardSpec("Island", 9502, 750002L),
                new CardSpec("Forest", 9503, 750003L));
        final SurveilPartitionDecisionProvider provider = externalProvider();
        final SurveilPartitionSession session = provider.admit(fixture.chooser(), fixture.cards());
        completeL2A(provider, session, Collections.nCopies(3,
                SurveilPartitionCandidateKind.CLASSIFY_RETAIN));

        final List<SurveilPartitionCard> islandItems = retainedItems(session).stream()
                .filter(item -> item.getVisibleName().equals("Island"))
                .collect(Collectors.toList());
        assertEquals(islandItems.size(), 2);
        assertFalse(islandItems.get(0).getItemId() == islandItems.get(1).getItemId());

        final DecisionRequest request = provider.createRetainedTopOrderRequest(session);
        assertEquals(request.getCandidates().stream()
                .map(candidate -> candidate.getSurveilRetainedTopOrderCard().getVisibleName())
                .filter("Island"::equals).count(), 2L);
        final List<LegalCandidate> islandCandidates = request.getCandidates().stream()
                .filter(candidate -> "Island".equals(
                        candidate.getSurveilRetainedTopOrderCard().getVisibleName()))
                .collect(Collectors.toList());
        assertEquals(islandCandidates.size(), 2);
        assertNotEquals(islandCandidates.get(0).getSurveilRetainedTopOrderCard().getItemId(),
                islandCandidates.get(1).getSurveilRetainedTopOrderCard().getItemId());
        provider.applyRetainedTopOrderCandidate(session, islandCandidates.get(0));
        final DecisionRequest remaining = provider.createRetainedTopOrderRequest(session);
        assertEquals(remaining.getCandidates().stream()
                .map(candidate -> candidate.getSurveilRetainedTopOrderCard().getVisibleName())
                .filter("Island"::equals).count(), 1L);
        provider.closeSession(session);
    }

    @Test
    public void alreadyOrderedItemAndForeignCandidateAreRejected() {
        final Fixture fixture = fixture("Island", "Forest", "Mountain");
        final SurveilPartitionDecisionProvider provider = externalProvider();
        final SurveilPartitionSession session = provider.admit(fixture.chooser(), fixture.cards());
        completeL2A(provider, session, Collections.nCopies(3,
                SurveilPartitionCandidateKind.CLASSIFY_RETAIN));
        final DecisionRequest first = provider.createRetainedTopOrderRequest(session);
        final LegalCandidate selected = first.getCandidates().get(0);
        provider.applyRetainedTopOrderCandidate(session, selected);

        final SurveilPartitionCard orderedItem = selected.getSurveilRetainedTopOrderCard();
        final DecisionRequest current = provider.createRetainedTopOrderRequest(session);
        final LegalCandidate currentRequestCandidate = current.getCandidates().get(0);
        replaceField(currentRequestCandidate, "surveilRetainedTopOrderCard", orderedItem);
        replaceField(currentRequestCandidate, "semanticKey",
                LegalCandidate.surveilRetainedTopOrderSemanticKey(orderedItem));
        expectThrows(IllegalArgumentException.class,
                () -> provider.applyRetainedTopOrderCandidate(session, currentRequestCandidate));
        expectThrows(IllegalArgumentException.class,
                () -> provider.applyRetainedTopOrderCandidate(session,
                        LegalCandidate.surveilRetainedTopOrder(0, orderedItem)));
        provider.closeSession(session);
    }

    @Test
    public void onlyOneL2BRequestHandleCanBeOpen() {
        final Fixture fixture = fixture("Island", "Forest", "Mountain");
        final SurveilPartitionDecisionProvider provider = externalProvider();
        final SurveilPartitionSession session = provider.admit(fixture.chooser(), fixture.cards());
        completeL2A(provider, session, Collections.nCopies(3,
                SurveilPartitionCandidateKind.CLASSIFY_RETAIN));

        final DecisionRequest request = provider.createRetainedTopOrderRequest(session);
        expectThrows(IllegalStateException.class, () -> provider.createRetainedTopOrderRequest(session));
        provider.applyRetainedTopOrderCandidate(session, request.getCandidates().get(0));
        assertNotNull(provider.createRetainedTopOrderRequest(session));
        provider.closeSession(session);
    }

    @Test
    public void prematureFinalRetainedOrderHandoffLeavesSessionBeforePairReady() {
        final Fixture fixture = fixture("Island", "Forest", "Mountain");
        final SurveilPartitionDecisionProvider provider = externalProvider();
        final SurveilPartitionSession session = provider.admit(fixture.chooser(), fixture.cards());
        completeL2A(provider, session, Collections.nCopies(3,
                SurveilPartitionCandidateKind.CLASSIFY_RETAIN));
        provider.createRetainedTopOrderRequest(session);

        expectThrows(IllegalStateException.class, session::finalRetainedNativeOrder);
        assertFalse(session.isPairReady());
        assertTrue(session.isComplete());
        assertFalse(session.isRetainedTopOrderComplete());
        provider.closeSession(session);
    }

    @Test
    public void markPairReadyRequiresClosedOutL2AAndL2BHandles() {
        final Fixture fixture = fixture("Island", "Forest");
        final SurveilPartitionDecisionProvider provider = externalProvider();
        final SurveilPartitionSession session = provider.admit(fixture.chooser(), fixture.cards());
        completeL2A(provider, session, Collections.nCopies(2,
                SurveilPartitionCandidateKind.CLASSIFY_RETAIN));
        final DecisionRequest request = provider.createRetainedTopOrderRequest(session);

        expectThrows(IllegalStateException.class, session::markPairReady);
        provider.applyRetainedTopOrderCandidate(session, request.getCandidates().get(0));
        session.markPairReady();
        assertTrue(session.isPairReady());
        provider.closeSession(session);
    }

    @Test
    public void closeBeforeAndAfterPairReadyIsIdempotentAndUnroutesParent() {
        final Fixture beforePairFixture = fixture("Island", "Forest", "Mountain");
        final SurveilPartitionDecisionProvider beforePairProvider = externalProvider();
        final SurveilPartitionSession beforePair = beforePairProvider.admit(
                beforePairFixture.chooser(), beforePairFixture.cards());
        completeL2A(beforePairProvider, beforePair, Collections.nCopies(3,
                SurveilPartitionCandidateKind.CLASSIFY_RETAIN));
        beforePairProvider.createRetainedTopOrderRequest(beforePair);
        beforePairProvider.closeSession(beforePair);
        beforePairProvider.closeSession(beforePair);

        assertEquals(beforePairProvider.activeSessionCount(), 0);
        assertFalse(beforePair.hasOpenRequest());
        expectThrows(IllegalStateException.class,
                () -> beforePairProvider.createRetainedTopOrderRequest(beforePair));

        final Fixture afterPairFixture = fixture("Island", "Forest");
        final SurveilPartitionDecisionProvider afterPairProvider = externalProvider();
        final SurveilPartitionSession afterPair = afterPairProvider.admit(
                afterPairFixture.chooser(), afterPairFixture.cards());
        completeL2A(afterPairProvider, afterPair, Collections.nCopies(2,
                SurveilPartitionCandidateKind.CLASSIFY_RETAIN));
        final DecisionRequest request = afterPairProvider.createRetainedTopOrderRequest(afterPair);
        afterPairProvider.applyRetainedTopOrderCandidate(afterPair, request.getCandidates().get(0));
        afterPair.markPairReady();
        afterPairProvider.closeSession(afterPair);
        afterPairProvider.closeSession(afterPair);

        assertEquals(afterPairProvider.activeSessionCount(), 0);
        assertTrue(afterPair.isPairReady());
        expectThrows(IllegalStateException.class,
                () -> afterPairProvider.applyRetainedTopOrderCandidate(afterPair,
                        request.getCandidates().get(0)));
    }

    @Test
    public void nativeOwnerStillRequiresNativeMembershipVector() {
        final Fixture fixture = fixture("Island", "Forest");
        final SurveilPartitionDecisionProvider provider = new SurveilPartitionDecisionProvider();
        final SurveilPartitionSession session = provider.admit(fixture.chooser(), fixture.cards());

        expectThrows(IllegalStateException.class, () -> provider.createMembershipRequest(session));
        provider.closeSession(session);
    }

    private static SurveilPartitionDecisionProvider externalProvider() {
        final SurveilPartitionDecisionProvider provider = new SurveilPartitionDecisionProvider();
        provider.setOwner(SurveilPartitionOwner.EXTERNAL);
        return provider;
    }

    private static void completeL2A(final SurveilPartitionDecisionProvider provider,
            final SurveilPartitionSession session, final List<SurveilPartitionCandidateKind> expected) {
        for (final SurveilPartitionCandidateKind kind : expected) {
            final DecisionRequest request = provider.createMembershipRequest(session);
            final LegalCandidate selected = request.getCandidates().stream()
                    .filter(candidate -> candidate.getSurveilPartitionCandidateKind() == kind)
                    .findFirst()
                    .orElseThrow();
            provider.applyMembershipCandidate(session, selected);
        }
    }

    private Fixture fixture(final String... names) {
        final Game game = initAndCreateGame();
        final Player chooser = game.getPlayers().get(1);
        final List<Card> cards = Arrays.stream(names)
                .map(name -> addCardToZone(name, chooser, ZoneType.Hand))
                .collect(Collectors.toList());
        return new Fixture(game, chooser, cards);
    }

    private Fixture customFixture(final CardSpec... specs) {
        final Game game = initAndCreateGame();
        final Player chooser = game.getPlayers().get(1);
        final List<Card> cards = Arrays.stream(specs)
                .map(spec -> customCard(chooser, spec))
                .collect(Collectors.toList());
        return new Fixture(game, chooser, cards);
    }

    private Fixture canonicalFixture() {
        final Game game = initAndCreateGame();
        final Player chooser = game.getPlayers().get(1);
        final List<Card> cards = List.of(
                customVisibleCard(chooser, "a", 9702, 770001L),
                customVisibleCard(chooser, "B", 9703, 770004L),
                customVisibleCard(chooser, "a", 9701, 770003L),
                customVisibleCard(chooser, "a", 9701, 770002L));
        return new Fixture(game, chooser, cards);
    }

    private Card customCard(final Player chooser, final CardSpec spec) {
        final Card template = createCard(spec.name(), chooser);
        final Card card = CardFactory.getCard(template.getPaperCard(), chooser, spec.cardId(), chooser.getGame());
        card.setGameTimestamp(spec.gameTimestamp());
        chooser.getZone(ZoneType.Hand).add(card);
        return card;
    }

    private Card customVisibleCard(final Player chooser, final String visibleName,
            final int cardId, final long gameTimestamp) {
        final Card card = customCard(chooser, new CardSpec("Island", cardId, gameTimestamp));
        card.setName(visibleName);
        return card;
    }

    private static void completeAllRetained(final SurveilPartitionDecisionProvider provider,
            final SurveilPartitionSession session, final DecisionRequest firstRequest) {
        LegalCandidate requestCandidate = firstRequest.getCandidates().stream()
                .filter(candidate -> candidate.getSurveilPartitionCandidateKind()
                        == SurveilPartitionCandidateKind.CLASSIFY_RETAIN)
                .findFirst().orElseThrow();
        provider.applyMembershipCandidate(session, requestCandidate);
        while (!session.isComplete()) {
            final DecisionRequest request = provider.createMembershipRequest(session);
            requestCandidate = request.getCandidates().stream()
                    .filter(candidate -> candidate.getSurveilPartitionCandidateKind()
                            == SurveilPartitionCandidateKind.CLASSIFY_RETAIN)
                    .findFirst().orElseThrow();
            provider.applyMembershipCandidate(session, requestCandidate);
        }
    }

    private static List<Long> retainedItemIds(final SurveilPartitionSession session) {
        return retainedItems(session).stream().map(SurveilPartitionCard::getItemId)
                .collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    private static List<SurveilPartitionCard> retainedItems(final SurveilPartitionSession session) {
        return (List<SurveilPartitionCard>) readField(session, "retainedItems");
    }

    private static Card nativeCardForItem(final SurveilPartitionSession session, final long itemId) {
        try {
            final Field mapField = session.getClass().getDeclaredField("nativeItems");
            mapField.setAccessible(true);
            final java.util.Map<?, ?> nativeItems = (java.util.Map<?, ?>) mapField.get(session);
            for (final Object item : nativeItems.values()) {
                final Field itemIdField = item.getClass().getDeclaredField("itemId");
                itemIdField.setAccessible(true);
                if (itemIdField.getLong(item) == itemId) {
                    final Field nativeCardField = item.getClass().getDeclaredField("nativeCard");
                    nativeCardField.setAccessible(true);
                    return (Card) nativeCardField.get(item);
                }
            }
            throw new AssertionError("missing itemId " + itemId);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static List<Long> itemIdsForCards(final SurveilPartitionSession session,
            final List<Card> cards) {
        final Map<Card, Long> itemIds = nativeItemIds(session);
        return cards.stream().map(itemIds::get).collect(Collectors.toList());
    }

    private static Map<Card, Long> nativeItemIds(final SurveilPartitionSession session) {
        try {
            final Field mapField = session.getClass().getDeclaredField("nativeItems");
            mapField.setAccessible(true);
            final Map<?, ?> nativeItems = (Map<?, ?>) mapField.get(session);
            final Map<Card, Long> itemIds = new IdentityHashMap<>();
            for (final Object item : nativeItems.values()) {
                final Field nativeCardField = item.getClass().getDeclaredField("nativeCard");
                final Field itemIdField = item.getClass().getDeclaredField("itemId");
                nativeCardField.setAccessible(true);
                itemIdField.setAccessible(true);
                itemIds.put((Card) nativeCardField.get(item), itemIdField.getLong(item));
            }
            return itemIds;
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static Map<Card, Integer> nativeOrdinals(final SurveilPartitionSession session) {
        try {
            final Field mapField = session.getClass().getDeclaredField("nativeItems");
            mapField.setAccessible(true);
            final Map<?, ?> nativeItems = (Map<?, ?>) mapField.get(session);
            final Map<Card, Integer> nativeOrdinals = new IdentityHashMap<>();
            for (final Object item : nativeItems.values()) {
                final Field nativeCardField = item.getClass().getDeclaredField("nativeCard");
                final Field nativeOrdinalField = item.getClass().getDeclaredField("nativeOrdinal");
                nativeCardField.setAccessible(true);
                nativeOrdinalField.setAccessible(true);
                nativeOrdinals.put((Card) nativeCardField.get(item), nativeOrdinalField.getInt(item));
            }
            return nativeOrdinals;
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static List<String> publicProjection(final List<SurveilPartitionCard> items) {
        return items.stream().map(item -> item.getItemId() + "|" + item.getVisibleName())
                .collect(Collectors.toList());
    }

    private static List<String> l2bPresentation(final DecisionRequest request) {
        final SurveilRetainedTopOrderContext context = request.getSurveilRetainedTopOrderContext();
        final List<String> presentation = new ArrayList<>();
        presentation.add(Integer.toString(context.getDecisionStepIndex()));
        presentation.addAll(publicProjection(context.getRetainedItems()));
        presentation.add("CANDIDATES");
        presentation.addAll(request.getCandidates().stream()
                .map(candidate -> candidate.getCandidateId() + "|"
                        + candidate.getSurveilRetainedTopOrderCandidateKind() + "|"
                        + candidate.getSemanticKey() + "|"
                        + candidate.getSurveilRetainedTopOrderCard().getItemId() + "|"
                        + candidate.getSurveilRetainedTopOrderCard().getVisibleName())
                .collect(Collectors.toList()));
        return presentation;
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

    private static void replaceField(final Object target, final String fieldName, final Object value) {
        try {
            final Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private record CardSpec(String name, int cardId, long gameTimestamp) {
    }

    private record Fixture(Game game, Player chooser, List<Card> cards) {
    }
}
