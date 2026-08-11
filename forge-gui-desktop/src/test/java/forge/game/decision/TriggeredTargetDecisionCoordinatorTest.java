package forge.game.decision;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.trigger.Trigger;
import forge.game.trigger.TriggerType;
import forge.game.trigger.WrappedAbility;
import forge.game.zone.ZoneType;
import org.testng.annotations.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;

public class TriggeredTargetDecisionCoordinatorTest extends AITest {
    @Test
    public void bloodWithoutResolverUsesNativePreparationWithTeacherCapture() {
        final BloodFixture fixture = bloodFixture();
        final TargetDecisionProvider provider = new TargetDecisionProvider();
        assertTwoTargetRequest(provider, fixture);

        final var preparation = new TriggeredTargetDecisionCoordinator().prepare(
                fixture.wrapper(), fixture.chooser(), provider, null);

        assertEquals(preparation.getStatus(),
                TriggeredTargetDecisionCoordinator.PreparationStatus.NATIVE_WITH_TEACHER_CAPTURE);
    }

    @Test
    public void bloodWithResolverUsesPreparedExternalPathForTwoTargetRequest() {
        final BloodFixture fixture = bloodFixture();
        final TargetDecisionProvider provider = new TargetDecisionProvider();
        assertTwoTargetRequest(provider, fixture);
        final AtomicInteger resolverCalls = new AtomicInteger();

        final var preparation = new TriggeredTargetDecisionCoordinator().prepare(
                fixture.wrapper(), fixture.chooser(), provider, request -> {
                    resolverCalls.incrementAndGet();
                    return request.getCandidates().get(0);
                });

        assertEquals(preparation.getStatus(), TriggeredTargetDecisionCoordinator.PreparationStatus.PREPARED);
        assertEquals(resolverCalls.get(), 0,
                "preparation must not invoke the external resolver or Forge AI");
    }

    private BloodFixture bloodFixture() {
        final Game game = initAndCreateGame();
        final Player chooser = game.getPlayers().get(1);
        final Player opponent = game.getPlayers().get(0);
        final Card source = addCardToZone("Blood Operative", chooser, ZoneType.Battlefield);
        addCardToZone("Runeclaw Bear", opponent, ZoneType.Graveyard);
        addCardToZone("Llanowar Elves", opponent, ZoneType.Graveyard);
        final Trigger trigger = source.getTriggers().stream()
                .filter(candidate -> candidate.getMode() == TriggerType.ChangesZone)
                .filter(candidate -> "TrigChangeZone".equals(candidate.getParam("Execute")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Blood must expose its native ChangesZone trigger"));
        final SpellAbility ability = trigger.ensureAbility();
        ability.setActivatingPlayer(chooser);
        return new BloodFixture(chooser, ability, new WrappedAbility(trigger, ability, chooser));
    }

    private static void assertTwoTargetRequest(final TargetDecisionProvider provider,
            final BloodFixture fixture) {
        final TargetDecisionProvider.Generation generation = provider.generateTargetRequest(
                fixture.ability(), fixture.chooser(), null);
        assertEquals(generation.getStatus(), TargetDecisionProvider.Status.DECISION);
        assertFalse(generation.getRequest().isForced());
        assertEquals(generation.getRequest().getCandidates().stream()
                .filter(candidate -> candidate.getTargetKind() == TargetCandidateKind.TARGET_CARD)
                .count(), 2L);
    }

    private record BloodFixture(Player chooser, SpellAbility ability, WrappedAbility wrapper) {
    }
}
