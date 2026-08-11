package forge.game.decision;

import forge.ai.AITest;
import forge.ai.LobbyPlayerAi;
import forge.ai.PlayerControllerAi;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.trigger.Trigger;
import forge.game.trigger.TriggerType;
import forge.game.trigger.WrappedAbility;
import forge.game.zone.ZoneType;
import org.testng.annotations.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertSame;

public class TriggeredTargetDecisionCoordinatorTest extends AITest {
    @Test
    public void bloodWithoutResolverUsesNativePreparationWithTeacherCapture() {
        final BloodFixture fixture = bloodFixture();
        final TargetDecisionProvider provider = new TargetDecisionProvider();

        final var preparation = new TriggeredTargetDecisionCoordinator().prepare(
                fixture.wrapper(), fixture.chooser(), provider, null);

        assertPreparedRequest(preparation::getRequest, fixture);
        assertEquals(preparation.getStatus(),
                TriggeredTargetDecisionCoordinator.PreparationStatus.NATIVE_WITH_TEACHER_CAPTURE);
    }

    @Test
    public void bloodWithResolverUsesPreparedExternalPathForTwoTargetRequest() {
        final BloodFixture fixture = bloodFixture();
        final TargetDecisionProvider provider = new TargetDecisionProvider();
        final CountingTargetController nativeController = installCountingController(fixture);
        final AtomicInteger resolverCalls = new AtomicInteger();

        final var preparation = new TriggeredTargetDecisionCoordinator().prepare(
                fixture.wrapper(), fixture.chooser(), provider, request -> {
                    resolverCalls.incrementAndGet();
                    return request.getCandidates().get(0);
                });

        assertPreparedRequest(preparation::getRequest, fixture);
        assertEquals(preparation.getStatus(), TriggeredTargetDecisionCoordinator.PreparationStatus.PREPARED);
        assertEquals(resolverCalls.get(), 0,
                "preparation must not invoke the external resolver or Forge AI");
        assertEquals(nativeController.getChooseTargetsForCalls(), 0,
                "external preparation must not invoke the native target callback");
    }

    private BloodFixture bloodFixture() {
        final Game game = initAndCreateGame();
        final Player chooser = game.getPlayers().get(1);
        final Player opponent = game.getPlayers().get(0);
        final Card source = addCardToZone("Blood Operative", chooser, ZoneType.Battlefield);
        final Card firstTarget = addCardToZone("Runeclaw Bear", opponent, ZoneType.Graveyard);
        final Card secondTarget = addCardToZone("Llanowar Elves", opponent, ZoneType.Graveyard);
        final Trigger trigger = source.getTriggers().stream()
                .filter(candidate -> candidate.getMode() == TriggerType.ChangesZone)
                .filter(candidate -> "TrigChangeZone".equals(candidate.getParam("Execute")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Blood must expose its native ChangesZone trigger"));
        final SpellAbility ability = trigger.ensureAbility();
        ability.setActivatingPlayer(chooser);
        return new BloodFixture(game, chooser, ability,
                List.of(firstTarget.getId(), secondTarget.getId()).stream().sorted().toList(),
                new WrappedAbility(trigger, ability, null));
    }

    private static void assertPreparedRequest(final Supplier<DecisionRequest> requestSupplier,
            final BloodFixture fixture) {
        final DecisionRequest request = requestSupplier.get();
        assertNotNull(request);
        assertEquals(request.getRequestId(), 0L);
        assertEquals(request.getDecisionType(), DecisionType.TARGET);
        assertFalse(request.isForced());
        assertSame(request.getTargetContext().getAbility(), fixture.ability());
        assertEquals(request.getTargetContext().getChoosingPlayerId(), fixture.chooser().getId());
        assertFalse(request.getTargetContext().hasActionContinuation());
        assertEquals(request.getCandidates().stream()
                .filter(candidate -> candidate.getTargetKind() == TargetCandidateKind.TARGET_CARD)
                .map(LegalCandidate::getTargetEntityId)
                .sorted()
                .toList(), fixture.targetIds());
    }

    private static CountingTargetController installCountingController(final BloodFixture fixture) {
        final CountingTargetController controller = new CountingTargetController(
                fixture.game(), fixture.chooser());
        fixture.chooser().dangerouslySetController(controller);
        return controller;
    }

    private static final class CountingTargetController extends PlayerControllerAi {
        private int chooseTargetsForCalls;

        private CountingTargetController(final Game game, final Player player) {
            super(game, player, new LobbyPlayerAi(player.getName() + "-frl02k-c2a", null));
        }

        @Override
        public boolean chooseTargetsFor(final SpellAbility currentAbility) {
            chooseTargetsForCalls++;
            return true;
        }

        private int getChooseTargetsForCalls() {
            return chooseTargetsForCalls;
        }
    }

    private record BloodFixture(Game game, Player chooser, SpellAbility ability,
            List<Integer> targetIds, WrappedAbility wrapper) {
    }
}
