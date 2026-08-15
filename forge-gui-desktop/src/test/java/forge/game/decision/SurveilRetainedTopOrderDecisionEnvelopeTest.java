package forge.game.decision;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.trigger.TriggerType;
import forge.game.zone.ZoneType;
import org.testng.annotations.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

public class SurveilRetainedTopOrderDecisionEnvelopeTest extends AITest {
    @Test
    public void validR2RequestUsesTwoRemainingTypedCandidates() {
        final SurveilRetainedTopOrderContext context = retainedContext(2, 0);
        final DecisionRequest request = new DecisionRequest(100L, DecisionType.ORDER,
                candidatesFor(context), context);

        assertSame(request.getSurveilRetainedTopOrderContext(), context);
        assertNull(request.getOrderContext());
        assertNull(request.getCopySpellResolveFirstOrderContext());
        assertEquals(request.getCandidates().size(), 2);
        for (int index = 0; index < request.getCandidates().size(); index++) {
            final LegalCandidate candidate = request.getCandidates().get(index);
            assertEquals(candidate.getCandidateId(), index);
            assertEquals(candidate.getSurveilRetainedTopOrderCandidateKind(),
                    SurveilRetainedTopOrderCandidateKind.SELECT_NEXT_TOP);
            assertNotNull(candidate.getSurveilRetainedTopOrderCard());
            assertEquals(candidate.getSemanticKey(), "SURVEIL_RETAINED_TOP_ORDER|SELECT_NEXT_TOP|"
                    + candidate.getSurveilRetainedTopOrderCard().getItemId());
            assertL2BFactoryPayloadIsIsolated(candidate);
        }
        assertFalse(request.isForced());
    }

    @Test
    public void validR4Step1RequestUsesCanonicalRemainingCandidateIds() {
        final SurveilRetainedTopOrderContext context = retainedContext(4, 1);
        final DecisionRequest request = new DecisionRequest(101L, DecisionType.ORDER,
                candidatesFor(context), context);

        assertEquals(request.getCandidates().size(), 3);
        assertEquals(request.getCandidates().stream().map(LegalCandidate::getCandidateId).toList(),
                List.of(0, 1, 2));
        assertEquals(request.getCandidates().stream()
                .map(candidate -> candidate.getSurveilRetainedTopOrderCard().getItemId()).toList(),
                List.of(2L, 3L, 4L));
    }

    @Test
    public void rejectsCandidateWithMatchingItemIdButDifferentRetainedProjection() {
        final SurveilRetainedTopOrderContext context = retainedContext(2, 0);
        final List<LegalCandidate> candidates = new ArrayList<>(candidatesFor(context));
        final SurveilPartitionCard canonical = context.getRetainedItems().get(0);
        candidates.set(0, LegalCandidate.surveilRetainedTopOrder(0,
                new SurveilPartitionCard(canonical.getItemId(), "Counterfeit projection")));

        final IllegalArgumentException exception = expectThrows(IllegalArgumentException.class,
                () -> new DecisionRequest(102L, DecisionType.ORDER, candidates, context));
        assertTrue(exception.getMessage().contains("exact typed item shape"));
    }

    @Test
    public void rejectsWrongProfile() {
        final SurveilRetainedTopOrderContext context = retainedContext(2, 0);
        replaceField(context, "profile", null);

        final IllegalArgumentException exception = expectThrows(IllegalArgumentException.class,
                () -> new DecisionRequest(102L, DecisionType.ORDER,
                        candidatesFor(context), context));
        assertTrue(exception.getMessage().contains("profile must be SURVEIL_RETAINED_TOP_ORDER"));
    }

    @Test
    public void rejectsWrongDirection() {
        final SurveilRetainedTopOrderContext context = retainedContext(2, 0);
        replaceField(context, "direction", null);

        final IllegalArgumentException exception = expectThrows(IllegalArgumentException.class,
                () -> new DecisionRequest(103L, DecisionType.ORDER,
                        candidatesFor(context), context));
        assertTrue(exception.getMessage().contains("direction must be TOP_FIRST"));
    }

    @Test
    public void rejectsRetainedItemCountBelowTwo() {
        final SurveilRetainedTopOrderContext context = retainedContext(1, 0);
        final List<LegalCandidate> candidates = List.of(
                LegalCandidate.surveilRetainedTopOrder(0, context.getRetainedItems().get(0)),
                LegalCandidate.surveilRetainedTopOrder(1, card(99L, "Mountain")));

        final IllegalArgumentException exception = expectThrows(IllegalArgumentException.class,
                () -> new DecisionRequest(104L, DecisionType.ORDER,
                        candidates, context));
        assertTrue(exception.getMessage().contains("retainedItemCount must be at least 2"));
    }

    @Test
    public void rejectsCandidateOutsideRetainedItems() {
        final SurveilRetainedTopOrderContext context = retainedContext(2, 0);
        final List<LegalCandidate> candidates = List.of(
                LegalCandidate.surveilRetainedTopOrder(0, card(99L, "Mountain")),
                LegalCandidate.surveilRetainedTopOrder(1, context.getRetainedItems().get(1)));

        expectThrows(IllegalArgumentException.class,
                () -> new DecisionRequest(105L, DecisionType.ORDER, candidates, context));
    }

    @Test
    public void rejectsDuplicateLookingCardWithWrongItemId() {
        final SurveilRetainedTopOrderContext context = retainedContext(2, 0);
        final List<LegalCandidate> candidates = List.of(
                LegalCandidate.surveilRetainedTopOrder(0, card(99L, "Island")),
                LegalCandidate.surveilRetainedTopOrder(1, card(2L, "Island")));

        expectThrows(IllegalArgumentException.class,
                () -> new DecisionRequest(106L, DecisionType.ORDER, candidates, context));
    }

    @Test
    public void rejectsWrongSemanticKey() {
        final SurveilRetainedTopOrderContext context = retainedContext(2, 0);
        final List<LegalCandidate> candidates = candidatesFor(context);
        replaceField(candidates.get(0), "semanticKey", "SURVEIL_RETAINED_TOP_ORDER|WRONG|1");

        expectThrows(IllegalArgumentException.class,
                () -> new DecisionRequest(107L, DecisionType.ORDER, candidates, context));
    }

    @Test
    public void rejectsNonContiguousRequestLocalCandidateIds() {
        final SurveilRetainedTopOrderContext context = retainedContext(2, 0);
        final List<LegalCandidate> candidates = List.of(
                LegalCandidate.surveilRetainedTopOrder(0, context.getRetainedItems().get(0)),
                LegalCandidate.surveilRetainedTopOrder(2, context.getRetainedItems().get(1)));

        expectThrows(IllegalArgumentException.class,
                () -> new DecisionRequest(108L, DecisionType.ORDER, candidates, context));
    }

    @Test
    public void rejectsDecisionStepOutsideZeroThroughRMinusTwo() {
        final SurveilRetainedTopOrderContext context = retainedContext(3, 2);
        final List<LegalCandidate> candidates = List.of(
                LegalCandidate.surveilRetainedTopOrder(0, context.getRetainedItems().get(1)),
                LegalCandidate.surveilRetainedTopOrder(1, context.getRetainedItems().get(2)));

        final IllegalArgumentException exception = expectThrows(IllegalArgumentException.class,
                () -> new DecisionRequest(109L, DecisionType.ORDER, candidates, context));
        assertTrue(exception.getMessage().contains(
                "decisionStepIndex must be within 0..retainedItemCount-2"));
    }

    @Test
    public void rejectsCandidateCountDifferentFromRemainingCount() {
        final SurveilRetainedTopOrderContext context = retainedContext(3, 1);
        final List<LegalCandidate> candidates = List.of(
                LegalCandidate.surveilRetainedTopOrder(0, context.getRetainedItems().get(0)),
                LegalCandidate.surveilRetainedTopOrder(1, context.getRetainedItems().get(1)),
                LegalCandidate.surveilRetainedTopOrder(2, context.getRetainedItems().get(2)));

        final IllegalArgumentException exception = expectThrows(IllegalArgumentException.class,
                () -> new DecisionRequest(110L, DecisionType.ORDER, candidates, context));
        assertTrue(exception.getMessage().contains(
                "candidate count must equal retainedItemCount - decisionStepIndex"));
    }

    @Test
    public void rejectsL1AndL1CPayloadsAttachedToL2BContext() {
        final Game game = initAndCreateGame();
        final Player chooser = game.getPlayers().get(0);
        final SimultaneousTriggerOrderItem l1Item = new SimultaneousTriggerOrderItem(1L,
                new CardSelectionCard(addCard("Island", chooser)), TriggerType.AbilityCast,
                ApiType.Effect);
        final CopySpellResolveFirstOrderItem l1cItem = new CopySpellResolveFirstOrderItem(1L,
                new CopySpellResolveFirstOrderSourceProjection("Pyromantics"), ApiType.DealDamage,
                CopySpellResolveFirstOrderItemKind.COPIED_SPELL);
        final SurveilRetainedTopOrderContext l2bContext = retainedContext(2, 0);

        expectThrows(IllegalArgumentException.class,
                () -> new DecisionRequest(111L, DecisionType.ORDER,
                        List.of(LegalCandidate.order(0, OrderCandidateKind.SELECT_RESOLVE_FIRST, l1Item),
                                LegalCandidate.order(1, OrderCandidateKind.SELECT_RESOLVE_FIRST,
                                        new SimultaneousTriggerOrderItem(2L,
                                                new CardSelectionCard(addCard("Mountain", chooser)),
                                                TriggerType.AbilityCast, ApiType.Effect))), l2bContext));
        expectThrows(IllegalArgumentException.class,
                () -> new DecisionRequest(112L, DecisionType.ORDER,
                        List.of(LegalCandidate.copySpellResolveFirstOrder(
                                        0, CopySpellResolveFirstOrderItemKind.COPIED_SPELL, l1cItem),
                                LegalCandidate.copySpellResolveFirstOrder(
                                        1, CopySpellResolveFirstOrderItemKind.COPIED_SPELL,
                                        new CopySpellResolveFirstOrderItem(2L,
                                                new CopySpellResolveFirstOrderSourceProjection("Lightning"),
                                                ApiType.DealDamage,
                                                CopySpellResolveFirstOrderItemKind.COPIED_SPELL))), l2bContext));
    }

    @Test
    public void rejectsL2BPayloadAttachedToL1Order() {
        final Game game = initAndCreateGame();
        final Player chooser = game.getPlayers().get(0);
        final SurveilRetainedTopOrderContext l2bItems = retainedContext(2, 0);
        final LegalCandidate first = LegalCandidate.order(0, OrderCandidateKind.SELECT_RESOLVE_FIRST,
                new SimultaneousTriggerOrderItem(1L,
                        new CardSelectionCard(addCard("Island", chooser)), TriggerType.AbilityCast,
                        ApiType.Effect));
        final LegalCandidate second = LegalCandidate.order(1, OrderCandidateKind.SELECT_RESOLVE_FIRST,
                new SimultaneousTriggerOrderItem(2L,
                        new CardSelectionCard(addCard("Mountain", chooser)), TriggerType.AbilityCast,
                        ApiType.Effect));
        attachL2BPayload(first, l2bItems.getRetainedItems().get(0));
        attachL2BPayload(second, l2bItems.getRetainedItems().get(1));
        final SimultaneousTriggerOrderContext l1Context = new SimultaneousTriggerOrderContext(
                SimultaneousTriggerOrderProfile.SIMULTANEOUS_TRIGGER_ORDER,
                OrderDirection.RESOLVE_FIRST, 1L, 0, 2, chooser.getId());

        final IllegalArgumentException exception = expectThrows(IllegalArgumentException.class,
                () -> new DecisionRequest(118L, DecisionType.ORDER, List.of(first, second), l1Context));
        assertEquals(exception.getMessage(), "ORDER candidates must be SELECT_RESOLVE_FIRST items");
    }

    @Test
    public void rejectsL2BPayloadAttachedToL1COrder() {
        final Game game = initAndCreateGame();
        final Player chooser = game.getPlayers().get(0);
        final SurveilRetainedTopOrderContext l2bItems = retainedContext(2, 0);
        final LegalCandidate first = LegalCandidate.copySpellResolveFirstOrder(
                0, CopySpellResolveFirstOrderItemKind.COPIED_SPELL,
                new CopySpellResolveFirstOrderItem(1L,
                        new CopySpellResolveFirstOrderSourceProjection("Pyromantics"), ApiType.DealDamage,
                        CopySpellResolveFirstOrderItemKind.COPIED_SPELL));
        final LegalCandidate second = LegalCandidate.copySpellResolveFirstOrder(
                1, CopySpellResolveFirstOrderItemKind.COPIED_SPELL,
                new CopySpellResolveFirstOrderItem(2L,
                        new CopySpellResolveFirstOrderSourceProjection("Lightning"), ApiType.DealDamage,
                        CopySpellResolveFirstOrderItemKind.COPIED_SPELL));
        attachL2BPayload(first, l2bItems.getRetainedItems().get(0));
        attachL2BPayload(second, l2bItems.getRetainedItems().get(1));
        final CopySpellResolveFirstOrderContext l1cContext = new CopySpellResolveFirstOrderContext(
                CopySpellResolveFirstOrderProfile.COPY_SPELL_RESOLVE_FIRST_ORDER,
                OrderDirection.RESOLVE_FIRST, 1L, 0, 2, chooser.getId());

        final IllegalArgumentException exception = expectThrows(IllegalArgumentException.class,
                () -> new DecisionRequest(119L, DecisionType.ORDER, List.of(first, second), l1cContext));
        assertEquals(exception.getMessage(), "ORDER candidates must be COPIED_SPELL items");
    }

    @Test
    public void rejectsL2BPayloadAttachedToCardSelection() {
        final DecisionRequest generic = genericCardSelectionRequest();
        final LegalCandidate candidate = LegalCandidate.surveilRetainedTopOrder(0,
                card(1L, "Island"));

        expectThrows(IllegalArgumentException.class,
                () -> new DecisionRequest(113L, DecisionType.CARD_SELECTION,
                        List.of(candidate), generic.getCardSelectionContext()));
    }

    @Test
    public void rejectsL2BPayloadWithoutL2BContext() {
        final SurveilRetainedTopOrderContext context = retainedContext(2, 0);

        expectThrows(IllegalArgumentException.class,
                () -> new DecisionRequest(114L, DecisionType.ORDER,
                        candidatesFor(context)));
    }

    @Test
    public void rejectsNullRetainedTopOrderFactoryItem() {
        final NullPointerException exception = expectThrows(NullPointerException.class,
                () -> LegalCandidate.surveilRetainedTopOrder(120, null));
        assertEquals(exception.getMessage(), "item");
    }

    @Test
    public void existingDecisionFamiliesRemainValid() {
        final Game game = initAndCreateGame();
        final Player chooser = game.getPlayers().get(0);
        final SimultaneousTriggerOrderItem l1First = new SimultaneousTriggerOrderItem(1L,
                new CardSelectionCard(addCard("Island", chooser)), TriggerType.AbilityCast,
                ApiType.Effect);
        final SimultaneousTriggerOrderItem l1Second = new SimultaneousTriggerOrderItem(2L,
                new CardSelectionCard(addCard("Mountain", chooser)), TriggerType.AbilityCast,
                ApiType.Effect);
        final DecisionRequest l1 = new DecisionRequest(115L, DecisionType.ORDER,
                List.of(
                        LegalCandidate.order(0, OrderCandidateKind.SELECT_RESOLVE_FIRST, l1First),
                        LegalCandidate.order(1, OrderCandidateKind.SELECT_RESOLVE_FIRST, l1Second)),
                new SimultaneousTriggerOrderContext(
                        SimultaneousTriggerOrderProfile.SIMULTANEOUS_TRIGGER_ORDER,
                        OrderDirection.RESOLVE_FIRST, 2L, 0, 2, chooser.getId()));

        final CopySpellResolveFirstOrderItem l1cFirst = new CopySpellResolveFirstOrderItem(1L,
                new CopySpellResolveFirstOrderSourceProjection("Pyromantics"), ApiType.DealDamage,
                CopySpellResolveFirstOrderItemKind.COPIED_SPELL);
        final CopySpellResolveFirstOrderItem l1cSecond = new CopySpellResolveFirstOrderItem(2L,
                new CopySpellResolveFirstOrderSourceProjection("Lightning"), ApiType.DealDamage,
                CopySpellResolveFirstOrderItemKind.COPIED_SPELL);
        final DecisionRequest l1c = new DecisionRequest(116L, DecisionType.ORDER,
                List.of(
                        LegalCandidate.copySpellResolveFirstOrder(
                                0, CopySpellResolveFirstOrderItemKind.COPIED_SPELL, l1cFirst),
                        LegalCandidate.copySpellResolveFirstOrder(
                                1, CopySpellResolveFirstOrderItemKind.COPIED_SPELL, l1cSecond)),
                new CopySpellResolveFirstOrderContext(
                        CopySpellResolveFirstOrderProfile.COPY_SPELL_RESOLVE_FIRST_ORDER,
                        OrderDirection.RESOLVE_FIRST, 3L, 0, 2, chooser.getId()));

        final DecisionRequest generic = genericCardSelectionRequest();
        final SurveilPartitionContext surveilContext = new SurveilPartitionContext(
                SurveilPartitionProfile.SURVEIL_PARTITION, 19L, 0, chooser.getId(), 2,
                List.of(new SurveilPartitionCard(11L, "Island"),
                        new SurveilPartitionCard(12L, "Forest")), 11L);
        final SurveilPartitionCard current = surveilContext.getVisibleItems().get(0);
        final DecisionRequest surveil = new DecisionRequest(117L, DecisionType.CARD_SELECTION,
                List.of(
                        LegalCandidate.surveilPartition(0,
                                SurveilPartitionCandidateKind.CLASSIFY_GRAVEYARD, current),
                        LegalCandidate.surveilPartition(1,
                                SurveilPartitionCandidateKind.CLASSIFY_RETAIN, current)),
                surveilContext);

        assertSame(l1.getOrderContext().getProfile(),
                SimultaneousTriggerOrderProfile.SIMULTANEOUS_TRIGGER_ORDER);
        assertSame(l1c.getCopySpellResolveFirstOrderContext().getProfile(),
                CopySpellResolveFirstOrderProfile.COPY_SPELL_RESOLVE_FIRST_ORDER);
        assertNotNull(generic.getCardSelectionContext());
        assertNull(generic.getSurveilPartitionContext());
        assertSame(surveil.getSurveilPartitionContext(), surveilContext);
        assertNull(surveil.getSurveilRetainedTopOrderContext());
    }

    private static List<LegalCandidate> candidatesFor(
            final SurveilRetainedTopOrderContext context) {
        final List<LegalCandidate> candidates = new ArrayList<>();
        for (int index = context.getDecisionStepIndex();
                index < context.getRetainedItemCount(); index++) {
            candidates.add(LegalCandidate.surveilRetainedTopOrder(candidates.size(),
                    context.getRetainedItems().get(index)));
        }
        return candidates;
    }

    private static void attachL2BPayload(final LegalCandidate candidate,
            final SurveilPartitionCard item) {
        replaceField(candidate, "surveilRetainedTopOrderCandidateKind",
                SurveilRetainedTopOrderCandidateKind.SELECT_NEXT_TOP);
        replaceField(candidate, "surveilRetainedTopOrderCard", item);
    }

    private static void assertL2BFactoryPayloadIsIsolated(final LegalCandidate candidate) {
        assertNull(candidate.getKind());
        assertEquals(candidate.getSourceCardId(), -1);
        assertEquals(candidate.getSourceName(), "");
        assertNull(candidate.getSourceZone());
        assertNull(candidate.getSourceState());
        assertEquals(candidate.getAbilityDescription(), "");
        assertNull(candidate.getSpellAbility());
        assertNull(candidate.getTargetKind());
        assertEquals(candidate.getTargetEntityId(), -1);
        assertEquals(candidate.getTargetName(), "");
        assertNull(candidate.getTargetZone());
        assertNull(candidate.getTarget());
        assertNull(candidate.getPaymentKind());
        assertNull(candidate.getMana());
        assertNull(candidate.getXValue());
        assertNull(candidate.getModeOrdinal());
        assertEquals(candidate.getModeDescription(), "");
        assertFalse(candidate.isModeUsesTargeting());
        assertNull(candidate.getMode());
        assertNull(candidate.getCardSelectionKind());
        assertNull(candidate.getCardSelectionCard());
        assertNull(candidate.getAttackKind());
        assertNull(candidate.getAttackCard());
        assertNull(candidate.getAttackDefender());
        assertNull(candidate.getBlockKind());
        assertNull(candidate.getBlockerCard());
        assertNull(candidate.getBlockAttackerCard());
        assertNull(candidate.getConfirmationKind());
        assertNull(candidate.getMulliganKind());
        assertNull(candidate.getOrderKind());
        assertNull(candidate.getOrderItem());
        assertNull(candidate.getCopySpellResolveFirstOrderKind());
        assertNull(candidate.getCopySpellResolveFirstOrderItem());
        assertNull(candidate.getSurveilPartitionCandidateKind());
        assertNull(candidate.getSurveilPartitionCard());
    }

    private static SurveilRetainedTopOrderContext retainedContext(final int retainedItemCount,
            final int decisionStepIndex) {
        final List<SurveilPartitionCard> items = new ArrayList<>();
        for (int index = 0; index < retainedItemCount; index++) {
            items.add(card(index + 1L, index < 2 ? "Island" : "Forest"));
        }
        return new SurveilRetainedTopOrderContext(
                SurveilRetainedTopOrderProfile.SURVEIL_RETAINED_TOP_ORDER,
                SurveilRetainedTopOrderDirection.TOP_FIRST, 19L, decisionStepIndex, 7,
                retainedItemCount, items);
    }

    private static SurveilPartitionCard card(final long itemId, final String visibleName) {
        return new SurveilPartitionCard(itemId, visibleName);
    }

    private DecisionRequest genericCardSelectionRequest() {
        final Game game = initAndCreateGame();
        final Player chooser = game.getPlayers().get(1);
        final SpellAbility source = spell(addCardToZone("Izzet Charm", chooser, ZoneType.Hand));
        source.setActivatingPlayer(chooser);
        final Card card = addCardToZone("Island", chooser, ZoneType.Hand);
        final CardCollection valid = new CardCollection(card);
        final CardSelectionDecisionProvider provider = new CardSelectionDecisionProvider();
        final CardSelectionDecisionProvider.SessionStart start = provider.beginSession(
                chooser, chooser, source, valid, 1, 1, valid);
        return provider.generateNext(start.getSession(), null).getRequest();
    }

    private static SpellAbility spell(final Card card) {
        return card.getSpellAbilities().stream().filter(SpellAbility::isSpell).findFirst().orElseThrow();
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
}
