package forge.game.decision;

import forge.ai.AITest;
import forge.ai.PlayerControllerAi;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class SurveilRetainedTopOrderEngineIntegrationTest extends AITest {
    @Test
    public void externalOwnerContinuesThroughExistingPlayerSurveilHandoff() {
        final var game = initAndCreateGame();
        final Player player = game.getPlayers().get(1);
        final Card graveCard = addCardToZone("Island", player, ZoneType.Library);
        final Card retainedCard = addCardToZone("Forest", player, ZoneType.Library);
        final SpellAbility cause = addCardToZone("Opt", player, ZoneType.Hand).getFirstSpellAbility();
        final AtomicInteger nativeCallbackCalls = new AtomicInteger();

        final PlayerControllerAi controller = new PlayerControllerAi(game, player, player.getLobbyPlayer()) {
            @Override
            public ImmutablePair<CardCollection, CardCollection> arrangeForSurveil(final CardCollection topN) {
                nativeCallbackCalls.incrementAndGet();
                throw new AssertionError("EXTERNAL must never call arrangeForSurveil");
            }
        };
        player.addController(game.getNextTimestamp(), player, controller, false);
        controller.getSurveilPartitionDecisionProvider().setOwner(SurveilPartitionOwner.EXTERNAL);
        controller.getSurveilPartitionDecisionProvider().setResolver(request -> request.getCandidates().stream()
                .filter(candidate -> candidate.getSurveilPartitionCard().getVisibleName()
                        .equals(graveCard.getName()))
                .findFirst()
                .map(candidate -> request.getCandidates().stream()
                        .filter(other -> other.getSurveilPartitionCandidateKind()
                                == SurveilPartitionCandidateKind.CLASSIFY_GRAVEYARD)
                        .findFirst().orElseThrow())
                .orElseGet(() -> request.getCandidates().stream()
                        .filter(candidate -> candidate.getSurveilPartitionCandidateKind()
                                == SurveilPartitionCandidateKind.CLASSIFY_RETAIN)
                        .findFirst().orElseThrow()));

        player.surveil(2, cause, new HashMap<>());

        assertEquals(nativeCallbackCalls.get(), 0);
        assertTrue(graveCard.isInZone(ZoneType.Graveyard));
        assertTrue(retainedCard.isInZone(ZoneType.Library));
        assertEquals(player.getZone(ZoneType.Graveyard).getCards().stream()
                .filter(card -> card.getName().equals(graveCard.getName())).count(), 1L);
        assertEquals(player.getZone(ZoneType.Graveyard).getCards().stream()
                .filter(card -> card.getName().equals(retainedCard.getName())).count(), 0L);
        assertEquals(player.getZone(ZoneType.Library).getCards().stream()
                .filter(card -> card.getName().equals(graveCard.getName())).count(), 0L);
        assertEquals(player.getZone(ZoneType.Library).getCards().stream()
                .filter(card -> card.getName().equals(retainedCard.getName())).count(), 1L);
        assertEquals(player.getSurveilThisTurn(), 1);
    }

    @Test
    public void externalOwnerPreservesL2BTopOrderThroughPlayerSurveilHandoff() {
        final var game = initAndCreateGame();
        final Player player = game.getPlayers().get(1);
        final Card graveCard = addCardToZone("Island", player, ZoneType.Library);
        final Card retainedA = addCardToZone("Forest", player, ZoneType.Library);
        final Card retainedB = addCardToZone("Mountain", player, ZoneType.Library);
        final SpellAbility cause = addCardToZone("Opt", player, ZoneType.Hand).getFirstSpellAbility();
        final AtomicInteger nativeCallbackCalls = new AtomicInteger();

        final PlayerControllerAi controller = new PlayerControllerAi(game, player, player.getLobbyPlayer()) {
            @Override
            public ImmutablePair<CardCollection, CardCollection> arrangeForSurveil(final CardCollection topN) {
                nativeCallbackCalls.incrementAndGet();
                throw new AssertionError("EXTERNAL must never call arrangeForSurveil");
            }
        };
        player.addController(game.getNextTimestamp(), player, controller, false);
        controller.getSurveilPartitionDecisionProvider().setOwner(SurveilPartitionOwner.EXTERNAL);
        controller.getSurveilPartitionDecisionProvider().setResolver(request -> {
            if (request.getSurveilPartitionContext() == null) {
                return request.getCandidates().stream()
                        .filter(candidate -> candidate.getSurveilRetainedTopOrderCard().getVisibleName()
                                .equals(retainedB.getName()))
                        .findFirst()
                        .orElseThrow();
            }
            return request.getCandidates().stream()
                    .filter(candidate -> candidate.getSurveilPartitionCard().getVisibleName()
                            .equals(graveCard.getName()))
                    .findFirst()
                    .map(candidate -> request.getCandidates().stream()
                            .filter(other -> other.getSurveilPartitionCandidateKind()
                                    == SurveilPartitionCandidateKind.CLASSIFY_GRAVEYARD)
                            .findFirst().orElseThrow())
                    .orElseGet(() -> request.getCandidates().stream()
                            .filter(candidate -> candidate.getSurveilPartitionCandidateKind()
                                    == SurveilPartitionCandidateKind.CLASSIFY_RETAIN)
                            .findFirst().orElseThrow());
        });

        player.surveil(3, cause, new HashMap<>());

        assertEquals(nativeCallbackCalls.get(), 0);
        assertTrue(graveCard.isInZone(ZoneType.Graveyard));
        assertEquals(player.getZone(ZoneType.Library).getCards().get(0).getName(), retainedB.getName());
        assertEquals(player.getZone(ZoneType.Library).getCards().get(1).getName(), retainedA.getName());
        assertEquals(player.getZone(ZoneType.Graveyard).getCards().stream()
                .filter(card -> card.getName().equals(graveCard.getName())).count(), 1L);
        assertEquals(player.getSurveilThisTurn(), 1);
    }
}
