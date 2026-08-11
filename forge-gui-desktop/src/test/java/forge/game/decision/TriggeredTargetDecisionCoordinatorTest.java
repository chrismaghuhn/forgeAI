package forge.game.decision;

import forge.ai.AITest;
import forge.ai.LobbyPlayerAi;
import forge.ai.PlayerControllerAi;
import forge.game.Game;
import forge.game.ability.AbilityFactory;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.card.CardFactory;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.spellability.TargetRestrictions;
import forge.game.trigger.Trigger;
import forge.game.trigger.TriggerType;
import forge.game.trigger.WrappedAbility;
import forge.game.zone.ZoneType;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

public class TriggeredTargetDecisionCoordinatorTest extends AITest {
    @Test
    public void exactBloodEtbProfileIsAdmitted() {
        final BloodFixture fixture = bloodFixture();
        final TargetDecisionProvider provider = new TargetDecisionProvider();
        final AtomicInteger resolverCalls = new AtomicInteger();

        final var preparation = new TriggeredTargetDecisionCoordinator().prepare(
                fixture.wrapper(), fixture.chooser(), provider, request -> {
                    resolverCalls.incrementAndGet();
                    return request.getCandidates().get(0);
                });

        assertEquals(preparation.getStatus().name(), "PREPARED");
        assertEquals(normalizedOriginalTriggerProjection(fixture.trigger()), Map.of(
                "Mode", "ChangesZone",
                "Origin", "Any",
                "Destination", "Battlefield",
                "ValidCard", "Card.Self",
                "OptionalDecider", "You",
                "Execute", "TrigChangeZone"));
        final Map<String, String> staticChangeZone =
                AbilityFactory.getMapParams(fixture.source().getSVar("TrigChangeZone"));
        assertEquals(staticChangeZoneSemanticProjection(staticChangeZone), Map.of(
                "DB", "ChangeZone",
                "Origin", "Graveyard",
                "Destination", "Exile",
                "ValidTgts", "Card"));
        assertFalse(staticChangeZone.containsKey("Optional"));
        assertFalse(staticChangeZone.containsKey("TargetingPlayer"));
        assertEquals(fixture.trigger().getMode(), TriggerType.ChangesZone);
        assertEquals(fixture.trigger().getParam("Origin"), "Any");
        assertEquals(fixture.trigger().getParam("Destination"), "Battlefield");
        assertEquals(fixture.trigger().getParam("ValidCard"), "Card.Self");
        assertEquals(fixture.trigger().getParam("OptionalDecider"), "You");
        assertEquals(fixture.trigger().getParam("Execute"), "TrigChangeZone");
        assertEquals(fixture.ability().getApi(), ApiType.ChangeZone);
        assertEquals(fixture.ability().getParam("Origin"), "Graveyard");
        assertEquals(fixture.ability().getParam("Destination"), "Exile");
        assertEquals(fixture.ability().getParam("ValidTgts"), "Card");
        assertTrue(fixture.ability().getTargetRestrictions().getZone().contains(ZoneType.Graveyard));
        assertEquals(fixture.ability().getMinTargets(), 1);
        assertEquals(fixture.ability().getMaxTargets(), 1);
        assertFalse(fixture.ability().hasParam("Optional"));
        assertTrue(fixture.ability().getTargets().isEmpty());
        assertEquals(fixture.wrapper().getDecider().getId(), fixture.chooser().getId());
        assertEquals(fixture.ability().getActivatingPlayer().getId(), fixture.chooser().getId());
        assertEquals(fixture.source().getController().getId(), fixture.chooser().getId());
        assertEquals(resolverCalls.get(), 0,
                "preparation must not invoke the external resolver");
    }

    @Test
    public void triggerDescriptionAndTargetPromptDoNotAffectAdmission() {
        final BloodFixture decorated = bloodFixture();
        decorated.trigger().putParam("TriggerDescription", "decorative trigger text");
        decorated.ability().putParam("TgtPrompt", "decorative target text");

        final var preparation = new TriggeredTargetDecisionCoordinator().prepare(
                decorated.wrapper(), decorated.chooser(), new TargetDecisionProvider(),
                request -> request.getCandidates().get(0));

        assertEquals(preparation.getStatus().name(), "PREPARED");

        final BloodFixture unknownTriggerParam = bloodFixture();
        unknownTriggerParam.trigger().putParam("UnknownSemantic", "True");
        assertUnsupportedTargeted(unknownTriggerParam, "UNSUPPORTED_PROFILE");

        final BloodFixture unknownLiveParam = bloodFixture();
        unknownLiveParam.ability().putParam("UnknownSemantic", "True");
        assertUnsupportedTargeted(unknownLiveParam, "UNSUPPORTED_PROFILE");
    }

    @Test
    public void unknownOriginalTriggerSemanticParameterRejectsAdmission() {
        final BloodFixture fixture = bloodFixture();
        fixture.trigger().getOriginalMapParams().put("UnknownSemantic", "True");

        assertEquals(fixture.trigger().getOriginalMapParams().get("UnknownSemantic"), "True");
        assertFalse(fixture.trigger().getMapParams().containsKey("UnknownSemantic"));
        assertUnsupportedTargeted(fixture, "UNSUPPORTED_PROFILE");
    }

    @Test
    public void liveChangeZoneMismatchRejectsStaticHit() {
        final BloodFixture fixture = bloodFixture();
        fixture.ability().setApi(ApiType.GainLife);

        assertUnsupportedTargeted(fixture, "LIVE_EFFECT_MISMATCH");
    }

    @Test
    public void liveTargetBoundsMismatchRejectsAdmission() {
        final BloodFixture minMismatch = bloodFixture();
        replaceLiveTargetBounds(minMismatch.ability(), "0", "1");
        assertEquals(minMismatch.ability().getMinTargets(), 0);
        assertEquals(minMismatch.ability().getMaxTargets(), 1);
        assertUnsupportedTargeted(minMismatch, "LIVE_EFFECT_MISMATCH");

        final BloodFixture maxMismatch = bloodFixture();
        replaceLiveTargetBounds(maxMismatch.ability(), "1", "2");
        assertEquals(maxMismatch.ability().getMinTargets(), 1);
        assertEquals(maxMismatch.ability().getMaxTargets(), 2);
        assertUnsupportedTargeted(maxMismatch, "LIVE_EFFECT_MISMATCH");
    }

    @Test
    public void runtimeRewriteRejectsStaticDefinition() {
        final BloodFixture triggerRewrite = bloodFixture();
        triggerRewrite.trigger().putParam("Destination", "Graveyard");
        assertUnsupportedTargeted(triggerRewrite, "UNSUPPORTED_PROFILE");

        final BloodFixture effectRewrite = bloodFixture();
        effectRewrite.source().setSVar("TrigChangeZone",
                "DB$ ChangeZone | Origin$ Library | Destination$ Exile | ValidTgts$ Card");
        assertUnsupportedTargeted(effectRewrite, "UNSUPPORTED_PROFILE");

        final BloodFixture optionalityRewrite = bloodFixture();
        optionalityRewrite.trigger().removeParam("OptionalDecider");
        assertUnsupportedTargeted(optionalityRewrite, "UNSUPPORTED_PROFILE");
    }

    @Test
    public void underlyingOptionalParamRejectsDuplicatedOptionality() {
        final BloodFixture fixture = bloodFixture();
        fixture.ability().putParam("Optional", "True");

        assertUnsupportedTargeted(fixture, "UNSUPPORTED_PROFILE");
    }

    @Test
    public void nonEmptyInitialTargetsFailBeforeGeneration() {
        final BloodFixture fixture = bloodFixture();
        fixture.ability().getTargets().add(fixture.firstTarget());

        final TriggeredTargetIntegrityException exception = assertUnsupportedTargeted(
                fixture, "NON_EMPTY_INITIAL_TARGETS");

        assertEquals(exception.getReason(), "NON_EMPTY_INITIAL_TARGETS");
        assertEquals(fixture.ability().getTargets().size(), 1);
    }

    @Test
    public void chooserMustMatchDeciderActivatorAndSourceController() {
        final BloodFixture deciderMismatch = bloodFixture();
        assertUnsupportedTargeted(withWrapper(deciderMismatch,
                new WrappedAbility(deciderMismatch.trigger(), deciderMismatch.ability(),
                        deciderMismatch.opponent())), "UNSUPPORTED_PROFILE");

        final BloodFixture activatorMismatch = bloodFixture();
        activatorMismatch.ability().setActivatingPlayer(activatorMismatch.opponent());
        assertUnsupportedTargeted(activatorMismatch, "UNSUPPORTED_PROFILE");

        final BloodFixture controllerMismatch = bloodFixture();
        controllerMismatch.source().setController(controllerMismatch.opponent(),
                controllerMismatch.game().getNextTimestamp());
        assertUnsupportedTargeted(controllerMismatch, "UNSUPPORTED_PROFILE");
    }

    @Test
    public void copiedWrapperAndClonedSourceAreSeparateProvenanceFailures() {
        final BloodFixture copiedBase = bloodFixture();
        final WrappedAbility copiedWrapper = (WrappedAbility) CardFactory.copySpellAbilityAndPossiblyHost(
                copiedBase.wrapper(), copiedBase.wrapper(), copiedBase.chooser());
        assertTrue(copiedWrapper.isCopied());
        assertUnsupportedTargeted(withWrapper(copiedBase, copiedWrapper), "UNSUPPORTED_PROFILE");

        final BloodFixture cloned = clonedSourceFixture(bloodFixture());
        assertTrue(cloned.source().isCloned());
        assertUnsupportedTargeted(cloned, "UNSUPPORTED_PROFILE");
    }

    @Test
    public void nonTargetedTriggerIsNotApplicableAndNativeWithResolver() {
        final TriggeredFixture fixture = nonTargetedFixture();
        final AtomicInteger resolverCalls = new AtomicInteger();
        final CountingTargetController nativeController = installCountingController(
                fixture.game(), fixture.chooser());

        final var preparation = new TriggeredTargetDecisionCoordinator().prepare(
                fixture.wrapper(), fixture.chooser(), new TargetDecisionProvider(), request -> {
                    resolverCalls.incrementAndGet();
                    return request.getCandidates().get(0);
                });

        assertEquals(preparation.getStatus().name(), "NOT_APPLICABLE");
        assertNull(preparation.getRequest());
        assertFalse(fixture.ability().usesTargeting());
        assertEquals(resolverCalls.get(), 0);
        assertEquals(nativeController.getChooseTargetsForCalls(), 0);
    }

    @Test
    public void unsupportedTargetedProfileFailsClosedOnlyWithResolver() {
        final TriggeredFixture fixture = targetedProfileFixture();

        assertUnsupportedTargeted(fixture, "UNSUPPORTED_PROFILE");
    }

    @Test
    public void copiedGeneratedSpawningAndTargetingPlayerCasesNeverFallbackExternally() {
        final List<Consumer<BloodFixture>> unsupportedCases = List.of(
                fixture -> fixture.wrapper().setCopied(true),
                fixture -> {
                    fixture.trigger().setIntrinsic(false);
                    fixture.ability().setIntrinsic(false);
                    fixture.wrapper().setIntrinsic(false);
                },
                fixture -> fixture.trigger().setSpawningAbility(fixture.ability()),
                fixture -> fixture.trigger().putParam("Static", "True"),
                fixture -> {
                    fixture.ability().putParam("TargetingPlayer", "You");
                    fixture.ability().setTargetingPlayer(fixture.chooser());
                });

        for (final Consumer<BloodFixture> unsupportedCase : unsupportedCases) {
            final BloodFixture fixture = bloodFixture();
            unsupportedCase.accept(fixture);
            final TriggeredTargetIntegrityException exception = assertUnsupportedTargeted(
                    fixture, "UNSUPPORTED_PROFILE");
            assertNotEquals(exception.getStatus().name(), "NOT_APPLICABLE");
        }
    }

    @Test
    public void unsupportedTargetedProfileRemainsNativeWithoutResolver() {
        final TriggeredFixture fixture = targetedProfileFixture();

        final var preparation = new TriggeredTargetDecisionCoordinator().prepare(
                fixture.wrapper(), fixture.chooser(), new TargetDecisionProvider(), null);

        assertTrue(preparation.getStatus().name().startsWith("NATIVE"),
                "unsupported targeted profiles remain on the native path without a resolver");
        assertNotEquals(preparation.getStatus().name(), "NOT_APPLICABLE");
        assertNotEquals(preparation.getStatus().name(), "PREPARED");
    }

    @Test
    public void nonWrappedTargetedTriggerFailsClosedWhenExternalOwnershipIsActive() {
        final TriggeredFixture fixture = targetedProfileFixture();
        final TargetDecisionProvider provider = new TargetDecisionProvider();
        final AtomicInteger resolverCalls = new AtomicInteger();
        final CountingTargetController nativeController = installCountingController(
                fixture.game(), fixture.chooser());
        final SpellAbility nonWrapped = fixture.ability();
        assertFalse(nonWrapped instanceof WrappedAbility);

        final TriggeredTargetIntegrityException exception = expectThrows(
                TriggeredTargetIntegrityException.class,
                () -> new TriggeredTargetDecisionCoordinator().prepare(
                        nonWrapped, fixture.chooser(), provider, request -> {
                            resolverCalls.incrementAndGet();
                            return request.getCandidates().get(0);
                        }));

        assertEquals(exception.getStatus().name(), "UNSUPPORTED_TARGETED_TRIGGER");
        assertEquals(exception.getReason(), "UNSUPPORTED_PROFILE");
        assertEquals(resolverCalls.get(), 0);
        assertEquals(nativeController.getChooseTargetsForCalls(), 0);
    }

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
        final CountingTargetController nativeController = installCountingController(
                fixture.game(), fixture.chooser());
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
        final Trigger trigger = bloodTrigger(source);
        final SpellAbility ability = trigger.ensureAbility();
        ability.setActivatingPlayer(chooser);
        return new BloodFixture(game, chooser, opponent, source, trigger, ability, firstTarget,
                List.of(firstTarget.getId(), secondTarget.getId()).stream().sorted().toList(),
                new WrappedAbility(trigger, ability, chooser));
    }

    private TriggeredFixture nonTargetedFixture() {
        final Game game = initAndCreateGame();
        final Player chooser = game.getPlayers().get(1);
        final Card source = addCardToZone("Quirion Sentinel", chooser, ZoneType.Battlefield);
        final Trigger trigger = triggerFor(source, TriggerType.ChangesZone, "TrigMana");
        final SpellAbility ability = trigger.ensureAbility();
        ability.setActivatingPlayer(chooser);
        return new TriggeredFixture(game, chooser, source, trigger, ability,
                new WrappedAbility(trigger, ability, chooser));
    }

    private TriggeredFixture targetedProfileFixture() {
        final Game game = initAndCreateGame();
        final Player chooser = game.getPlayers().get(1);
        final Card source = addCardToZone("Quill-Slinger Boggart", chooser, ZoneType.Battlefield);
        final Trigger trigger = triggerFor(source, TriggerType.SpellCast, "TrigLoseLife");
        final SpellAbility ability = trigger.ensureAbility();
        ability.setActivatingPlayer(chooser);
        return new TriggeredFixture(game, chooser, source, trigger, ability,
                new WrappedAbility(trigger, ability, chooser));
    }

    private BloodFixture clonedSourceFixture(final BloodFixture base) {
        final Card clonedSource = addCardToZone("Blood Operative", base.chooser(), ZoneType.Battlefield);
        clonedSource.addCloneState(CardFactory.getCloneStates(base.source(), clonedSource, base.ability()),
                base.game().getNextTimestamp());
        final Trigger trigger = bloodTrigger(clonedSource);
        final SpellAbility ability = trigger.ensureAbility();
        ability.setActivatingPlayer(base.chooser());
        return new BloodFixture(base.game(), base.chooser(), base.opponent(), clonedSource, trigger, ability,
                base.firstTarget(), base.targetIds(), new WrappedAbility(trigger, ability, base.chooser()));
    }

    private static Trigger bloodTrigger(final Card source) {
        return triggerFor(source, TriggerType.ChangesZone, "TrigChangeZone");
    }

    private static Trigger triggerFor(final Card source, final TriggerType mode, final String execute) {
        return source.getTriggers().stream()
                .filter(candidate -> candidate.getMode() == mode)
                .filter(candidate -> execute.equals(candidate.getParam("Execute")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("expected trigger fixture is unavailable"));
    }

    private static Map<String, String> normalizedOriginalTriggerProjection(final Trigger trigger) {
        final Map<String, String> original = trigger.getOriginalMapParams();
        return Map.of(
                "Mode", original.get("Mode"),
                "Origin", original.get("Origin"),
                "Destination", original.get("Destination"),
                "ValidCard", original.get("ValidCard"),
                "OptionalDecider", original.get("OptionalDecider"),
                "Execute", original.get("Execute"));
    }

    private static Map<String, String> staticChangeZoneSemanticProjection(
            final Map<String, String> staticChangeZone) {
        return Map.of(
                "DB", staticChangeZone.get("DB"),
                "Origin", staticChangeZone.get("Origin"),
                "Destination", staticChangeZone.get("Destination"),
                "ValidTgts", staticChangeZone.get("ValidTgts"));
    }

    private static void replaceLiveTargetBounds(final SpellAbility ability,
            final String minTargets, final String maxTargets) {
        final Map<String, String> liveParams = new HashMap<>(ability.getMapParams());
        liveParams.put("TargetMin", minTargets);
        liveParams.put("TargetMax", maxTargets);
        ability.setTargetRestrictions(new TargetRestrictions(liveParams));
    }

    private static BloodFixture withWrapper(final BloodFixture fixture, final WrappedAbility wrapper) {
        return new BloodFixture(fixture.game(), fixture.chooser(), fixture.opponent(), fixture.source(),
                fixture.trigger(), fixture.ability(), fixture.firstTarget(), fixture.targetIds(), wrapper);
    }

    private static TriggeredTargetIntegrityException assertUnsupportedTargeted(
            final Fixture fixture, final String reason) {
        final TargetDecisionProvider provider = new TargetDecisionProvider();
        final AtomicInteger resolverCalls = new AtomicInteger();
        final CountingTargetController nativeController = installCountingController(
                fixture.game(), fixture.chooser());

        final TriggeredTargetIntegrityException exception = expectThrows(
                TriggeredTargetIntegrityException.class,
                () -> new TriggeredTargetDecisionCoordinator().prepare(
                        fixture.wrapper(), fixture.chooser(), provider, request -> {
                            resolverCalls.incrementAndGet();
                            return request.getCandidates().get(0);
                        }));

        assertEquals(exception.getStatus().name(), "UNSUPPORTED_TARGETED_TRIGGER");
        assertEquals(exception.getReason(), reason);
        assertEquals(resolverCalls.get(), 0,
                "unsupported external ownership must not invoke the resolver");
        assertEquals(nativeController.getChooseTargetsForCalls(), 0,
                "unsupported external ownership must not fall back to Forge AI");
        return exception;
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

    private static CountingTargetController installCountingController(final Game game, final Player player) {
        final CountingTargetController controller = new CountingTargetController(
                game, player);
        player.dangerouslySetController(controller);
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

    private interface Fixture {
        Game game();
        Player chooser();
        Card source();
        Trigger trigger();
        SpellAbility ability();
        WrappedAbility wrapper();
    }

    private record BloodFixture(Game game, Player chooser, Player opponent, Card source,
            Trigger trigger, SpellAbility ability, Card firstTarget, List<Integer> targetIds,
            WrappedAbility wrapper) implements Fixture {
    }

    private record TriggeredFixture(Game game, Player chooser, Card source, Trigger trigger,
            SpellAbility ability, WrappedAbility wrapper) implements Fixture {
    }
}
