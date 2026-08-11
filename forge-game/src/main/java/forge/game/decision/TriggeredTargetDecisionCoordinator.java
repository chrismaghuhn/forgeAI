package forge.game.decision;

import forge.card.CardStateName;
import forge.game.ability.AbilityFactory;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.spellability.TargetRestrictions;
import forge.game.trigger.Trigger;
import forge.game.trigger.TriggerType;
import forge.game.trigger.WrappedAbility;
import forge.game.zone.ZoneType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Stateless admission boundary for the narrow FRL-02K-C2A triggered-target slice.
 *
 * <p>Task 5 deliberately admits the exact Blood Operative profile without asking Forge for legal
 * candidates. Request generation and continuation orchestration remain deferred to the next task.</p>
 */
public final class TriggeredTargetDecisionCoordinator {
    private static final String BLOOD_OPERATIVE = "Blood Operative";
    private static final String BLOOD_TRIGGER = "TrigChangeZone";

    private static final Map<String, String> BLOOD_TRIGGER_PARAMS = Map.of(
            "Mode", "ChangesZone",
            "Origin", "Any",
            "Destination", "Battlefield",
            "ValidCard", "Card.Self",
            "OptionalDecider", "You",
            "Execute", BLOOD_TRIGGER);
    private static final Map<String, String> BLOOD_EFFECT_PARAMS = Map.of(
            "DB", "ChangeZone",
            "Origin", "Graveyard",
            "Destination", "Exile",
            "ValidTgts", "Card");
    private static final Set<String> BLOOD_STATIC_EFFECT_PARAMS = Set.of(
            "DB", "Origin", "Destination", "ValidTgts", "TgtPrompt", "ValidTgtsDesc");
    private static final Set<String> BLOOD_LIVE_EFFECT_PARAMS = Set.of(
            "DB", "Origin", "Destination", "ValidTgts", "TgtPrompt", "ValidTgtsDesc",
            "TgtZone", "TargetMin", "TargetMax");

    public enum Classification {
        NOT_APPLICABLE,
        ADMITTED,
        UNSUPPORTED_TARGETED_TRIGGER
    }

    public enum PreparationStatus {
        NATIVE_WITH_TEACHER_CAPTURE,
        NATIVE_UNSUPPORTED_TARGETED_TRIGGER,
        PREPARED,
        NO_STACK,
        NOT_APPLICABLE
    }

    /** Classifies only the queued ability's trigger family; it does not generate a target request. */
    public Classification classify(final SpellAbility queuedAbility) {
        return evaluate(queuedAbility, implicitChooser(queuedAbility)).classification;
    }

    /**
     * Prepares the narrow boundary without invoking the provider, Forge AI, or the external resolver.
     *
     * <p>A wrapped ability supplies its trigger decider when this adapter is used by later routing.</p>
     */
    public Preparation prepare(final SpellAbility queuedAbility, final TargetDecisionProvider provider,
            final TargetDecisionProvider.Resolver resolver) {
        return prepareInternal(queuedAbility, implicitChooser(queuedAbility), resolver);
    }

    /** Four-argument compatibility overload used by the current RED tests and later controller routing. */
    public Preparation prepare(final SpellAbility queuedAbility, final Player chooser,
            final TargetDecisionProvider provider, final TargetDecisionProvider.Resolver resolver) {
        return prepareInternal(queuedAbility, chooser, resolver);
    }

    /** Thin adapter preserving the wrapped-ability call shape. */
    public Preparation prepare(final WrappedAbility wrapper, final Player chooser,
            final TargetDecisionProvider provider, final TargetDecisionProvider.Resolver resolver) {
        return prepare((SpellAbility) wrapper, chooser, provider, resolver);
    }

    /**
     * Completes the native placeholder without taking ownership of any continuation or target state.
     * Task 6 supplies the orchestration; Task 5 preserves the native result unchanged.
     */
    public boolean completeNative(final Preparation preparation, final boolean nativeResult) {
        Objects.requireNonNull(preparation, "preparation");
        return nativeResult;
    }

    /**
     * Enforces external ownership for a queued ability while preserving the native path when no resolver exists.
     * The exact admitted profile is also continuation-gated even when the resolver is null.
     */
    public void enforceExternalTargetBoundary(final SpellAbility queuedAbility,
            final TargetDecisionProvider.Resolver resolver) {
        if (queuedAbility == null) {
            return;
        }

        final Admission admission = evaluate(queuedAbility, implicitChooser(queuedAbility));
        if (admission.classification == Classification.NOT_APPLICABLE) {
            return;
        }
        if (admission.classification == Classification.ADMITTED) {
            rejectActiveContinuation();
            return;
        }
        if (resolver != null) {
            throw unsupported(admission);
        }
    }

    private static Preparation prepareInternal(final SpellAbility queuedAbility, final Player chooser,
            final TargetDecisionProvider.Resolver resolver) {
        if (queuedAbility == null) {
            return Preparation.of(PreparationStatus.NO_STACK, "NO_STACK");
        }

        final Admission admission = evaluate(queuedAbility, chooser);
        if (admission.classification == Classification.NOT_APPLICABLE) {
            return Preparation.of(PreparationStatus.NOT_APPLICABLE, admission.reason());
        }
        if (admission.classification == Classification.ADMITTED) {
            rejectActiveContinuation();
            return Preparation.of(resolver == null
                    ? PreparationStatus.NATIVE_WITH_TEACHER_CAPTURE : PreparationStatus.PREPARED,
                    admission.reason());
        }
        if (resolver != null) {
            throw unsupported(admission);
        }
        return Preparation.of(PreparationStatus.NATIVE_UNSUPPORTED_TARGETED_TRIGGER, admission.reason());
    }

    private static Admission evaluate(final SpellAbility queuedAbility, final Player chooser) {
        if (queuedAbility == null) {
            return Admission.notApplicable();
        }

        final Trigger trigger;
        try {
            trigger = queuedAbility.getTrigger();
            if (trigger == null) {
                return Admission.notApplicable();
            }
            if (!queuedAbility.usesTargeting()) {
                return Admission.notApplicable();
            }
        } catch (final RuntimeException ex) {
            return Admission.unsupported(TriggeredTargetIntegrityException.Reason.UNSUPPORTED_PROFILE);
        }

        if (!(queuedAbility instanceof WrappedAbility wrapper)) {
            return Admission.unsupported(TriggeredTargetIntegrityException.Reason.UNSUPPORTED_PROFILE);
        }

        try {
            return admitBlood(wrapper, chooser, trigger);
        } catch (final RuntimeException ex) {
            return Admission.unsupported(TriggeredTargetIntegrityException.Reason.UNSUPPORTED_PROFILE);
        }
    }

    private static Admission admitBlood(final WrappedAbility wrapper, final Player chooser,
            final Trigger trigger) {
        final SpellAbility liveAbility = wrapper.getWrappedAbility();
        final Player decider = wrapper.getDecider();
        final Card source = wrapper.getHostCard();
        if (liveAbility == null || trigger == null || source == null || chooser == null || decider == null) {
            return Admission.unsupported(TriggeredTargetIntegrityException.Reason.UNSUPPORTED_PROFILE);
        }
        if (!BLOOD_OPERATIVE.equals(source.getName())
                || source.getCurrentStateName() != CardStateName.Original
                || source.isCloned()) {
            return Admission.unsupported(TriggeredTargetIntegrityException.Reason.UNSUPPORTED_PROFILE);
        }
        if (source.isFaceDown()
                || source.getView() == null
                || chooser.getView() == null
                || decider.getView() == null
                || !source.getView().canBeShownTo(chooser.getView())
                || !source.getView().canBeShownTo(decider.getView())) {
            return Admission.unsupported(TriggeredTargetIntegrityException.Reason.UNSUPPORTED_PROFILE);
        }
        if (!trigger.isIntrinsic() || trigger.isStatic() || trigger.getMode() != TriggerType.ChangesZone
                || trigger.getSpawningAbility() != null || wrapper.isCopied()
                || liveAbility.isCopied() || !wrapper.isIntrinsic() || !liveAbility.isIntrinsic()) {
            return Admission.unsupported(TriggeredTargetIntegrityException.Reason.UNSUPPORTED_PROFILE);
        }
        if (!samePlayer(chooser, decider)
                || !samePlayer(decider, liveAbility.getActivatingPlayer())
                || !samePlayer(decider, source.getController())) {
            return Admission.unsupported(TriggeredTargetIntegrityException.Reason.UNSUPPORTED_PROFILE);
        }
        if (!BLOOD_TRIGGER_PARAMS.equals(normalize(trigger.getOriginalMapParams(), "TriggerDescription"))
                || !BLOOD_TRIGGER_PARAMS.equals(normalize(trigger.getMapParams(), "TriggerDescription"))) {
            return Admission.unsupported(TriggeredTargetIntegrityException.Reason.UNSUPPORTED_PROFILE);
        }
        if (!matchesStaticEffect(source)) {
            return Admission.unsupported(TriggeredTargetIntegrityException.Reason.UNSUPPORTED_PROFILE);
        }

        return matchesLiveEffect(liveAbility);
    }

    private static boolean matchesStaticEffect(final Card source) {
        if (!source.hasSVar(BLOOD_TRIGGER)) {
            return false;
        }
        try {
            final Map<String, String> params = AbilityFactory.getMapParams(source.getSVar(BLOOD_TRIGGER));
            if (!BLOOD_STATIC_EFFECT_PARAMS.containsAll(params.keySet())) {
                return false;
            }
            return BLOOD_EFFECT_PARAMS.equals(normalize(params, "TgtPrompt", "ValidTgtsDesc"));
        } catch (final RuntimeException ex) {
            return false;
        }
    }

    private static Admission matchesLiveEffect(final SpellAbility liveAbility) {
        final Map<String, String> params = liveAbility.getMapParams();
        if (params == null || !BLOOD_LIVE_EFFECT_PARAMS.containsAll(params.keySet())) {
            return Admission.unsupported(TriggeredTargetIntegrityException.Reason.UNSUPPORTED_PROFILE);
        }
        if (liveAbility.hasParam("Optional") || liveAbility.hasParam("TargetingPlayer")
                || liveAbility.getTargetingPlayer() != null) {
            return Admission.unsupported(TriggeredTargetIntegrityException.Reason.UNSUPPORTED_PROFILE);
        }
        if (liveAbility.getTargets() == null || !liveAbility.getTargets().isEmpty()) {
            return Admission.unsupported(TriggeredTargetIntegrityException.Reason.NON_EMPTY_INITIAL_TARGETS);
        }
        if (!BLOOD_EFFECT_PARAMS.equals(normalize(params, "TgtPrompt", "ValidTgtsDesc", "TgtZone",
                "TargetMin", "TargetMax")) || liveAbility.getApi() != ApiType.ChangeZone) {
            return Admission.unsupported(TriggeredTargetIntegrityException.Reason.LIVE_EFFECT_MISMATCH);
        }

        final TargetRestrictions restrictions = liveAbility.getTargetRestrictions();
        try {
            if (!liveAbility.usesTargeting() || restrictions == null || restrictions.isRandomTarget()
                    || restrictions.isRandomNumTargets() || !List.of(ZoneType.Graveyard).equals(restrictions.getZone())
                    || liveAbility.getMinTargets() != 1 || liveAbility.getMaxTargets() != 1
                    || liveAbility.getSubAbility() != null || !liveAbility.getAdditionalAbilities().isEmpty()
                    || !liveAbility.getAdditionalAbilityLists().isEmpty()
                    || liveAbility.getPayCosts() == null || !liveAbility.getPayCosts().isFree()
                    || (params.containsKey("TgtZone") && !"Graveyard".equals(params.get("TgtZone")))
                    || (params.containsKey("TargetMin") && !"1".equals(params.get("TargetMin")))
                    || (params.containsKey("TargetMax") && !"1".equals(params.get("TargetMax")))) {
                return Admission.unsupported(TriggeredTargetIntegrityException.Reason.LIVE_EFFECT_MISMATCH);
            }
        } catch (final RuntimeException ex) {
            return Admission.unsupported(TriggeredTargetIntegrityException.Reason.LIVE_EFFECT_MISMATCH);
        }
        return Admission.admitted();
    }

    private static Map<String, String> normalize(final Map<String, String> params, final String... ignoredKeys) {
        if (params == null) {
            return Map.of();
        }
        final Map<String, String> normalized = new HashMap<>(params);
        for (final String ignoredKey : ignoredKeys) {
            normalized.remove(ignoredKey);
        }
        return normalized;
    }

    private static Player implicitChooser(final SpellAbility queuedAbility) {
        return queuedAbility instanceof WrappedAbility wrapper ? wrapper.getDecider() : null;
    }

    private static boolean samePlayer(final Player first, final Player second) {
        return first != null && second != null && (first.equals(second) || first.getId() == second.getId());
    }

    private static void rejectActiveContinuation() {
        if (PriorityActionDiagnostics.hasActiveActionContinuation()) {
            throw new TriggeredTargetIntegrityException(
                    TriggeredTargetIntegrityException.Reason.UNSUPPORTED_ACTION_CONTINUATION);
        }
    }

    private static TriggeredTargetIntegrityException unsupported(final Admission admission) {
        return new TriggeredTargetIntegrityException(admission.failureReason);
    }

    public static final class Preparation {
        private final PreparationStatus status;
        private final String reason;
        private final DecisionRequest request;

        private Preparation(final PreparationStatus status0, final String reason0) {
            status = status0;
            reason = reason0;
            request = null;
        }

        private static Preparation of(final PreparationStatus status, final String reason) {
            return new Preparation(status, reason);
        }

        public PreparationStatus getStatus() {
            return status;
        }

        public String getReason() {
            return reason;
        }

        /* Package-private for the current same-package RED tests; Task 6 owns request exposure/orchestration. */
        DecisionRequest getRequest() {
            return request;
        }
    }

    private static final class Admission {
        private final Classification classification;
        private final String reason;
        private final TriggeredTargetIntegrityException.Reason failureReason;

        private Admission(final Classification classification0, final String reason0,
                final TriggeredTargetIntegrityException.Reason failureReason0) {
            classification = classification0;
            reason = reason0;
            failureReason = failureReason0;
        }

        private static Admission notApplicable() {
            return new Admission(Classification.NOT_APPLICABLE, "NOT_APPLICABLE", null);
        }

        private static Admission admitted() {
            return new Admission(Classification.ADMITTED, "ADMITTED", null);
        }

        private static Admission unsupported(final TriggeredTargetIntegrityException.Reason reason) {
            return new Admission(Classification.UNSUPPORTED_TARGETED_TRIGGER, reason.name(), reason);
        }

        private String reason() {
            return reason;
        }
    }
}
