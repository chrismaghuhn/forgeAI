package forge.game.decision;

import forge.ai.AITest;
import forge.ai.PlayerControllerAi;
import forge.game.Game;
import forge.game.ability.AbilityFactory;
import forge.game.ability.AbilityKey;
import forge.game.ability.effects.CopySpellAbilityEffect;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.spellability.SpellAbilityStackInstance;
import forge.game.zone.ZoneType;
import org.testng.annotations.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/** Real CopySpellAbilityEffect -> order -> target setup -> MagicStack coverage. */
public class CopySpellResolveFirstOrderEngineIntegrationTest extends AITest {
    @Test
    public void copyEffectUsesL1COrderBeforeTargetSetupAndStackResolution() {
        final Game game = initAndCreateGame();
        final Player player = game.getPlayers().get(0);
        final Player opponent = game.getPlayers().get(1);
        assertTrue(player.getController() instanceof PlayerControllerAi);
        addCard("Runeclaw Bear", opponent);

        final Card source = addCardToZone("Pyromatics", player, ZoneType.Battlefield);
        final SpellAbility originalSpell = source.getFirstSpellAbility();
        originalSpell.setActivatingPlayer(player);
        final SpellAbility copyEffect = AbilityFactory.getAbility(
                "DB$ CopySpellAbility | Defined$ TriggeredSpellAbility | Amount$ 2 | MayChooseTarget$ True",
                source);
        copyEffect.setActivatingPlayer(player);
        copyEffect.setTriggeringObject(AbilityKey.SpellAbility, originalSpell);

        final AtomicInteger resolverCalls = new AtomicInteger();
        player.getController().setCopySpellResolveFirstOrderResolver(request -> {
            resolverCalls.incrementAndGet();
            assertEquals(request.getDecisionType(), DecisionType.ORDER);
            assertEquals(request.getCopySpellResolveFirstOrderContext().getProfile(),
                    CopySpellResolveFirstOrderProfile.COPY_SPELL_RESOLVE_FIRST_ORDER);
            assertEquals(request.getCopySpellResolveFirstOrderContext().getDirection(),
                    OrderDirection.RESOLVE_FIRST);
            return request.getCandidates().get(0);
        });

        new CopySpellAbilityEffect().resolve(copyEffect);

        assertEquals(resolverCalls.get(), 1);
        assertEquals(game.getStack().size(), 2);
        final Set<SpellAbility> copiedOnStack = new HashSet<>();
        for (final SpellAbilityStackInstance instance : game.getStack()) {
            final SpellAbility copied = instance.getSpellAbility();
            assertTrue(copied.isSpell());
            assertTrue(copied.isCopied());
            assertTrue(copied.getHostCard().isCopiedSpell());
            copiedOnStack.add(copied);
        }
        assertEquals(copiedOnStack.size(), 2);

        while (!game.getStack().isEmpty()) {
            game.getStack().resolveStack();
        }
        assertEquals(game.getStack().size(), 0);
    }
}
