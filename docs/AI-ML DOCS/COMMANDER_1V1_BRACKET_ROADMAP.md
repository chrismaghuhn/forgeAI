# ForgeAI Commander 1v1 and Bracket-Specialist Roadmap

**Status:** Long-term roadmap / accepted planning direction
**Date:** 2026-08-13
**Base checkpoint:** `9200349284b3489a6a349de378c773bdfa2f6efc`

## Purpose

This document fixes the intended progression from the current controlled 1v1 Constructed environment to Commander specialization and, much later, multiplayer Commander.

The central sequencing decision is:

> Commander remains 1v1 through the Commander base-model stage and through training the full B1-B5 specialist family. Multiplayer is deliberately deferred until the 1v1 Commander model family is already trained and stable.

This is a roadmap decision, not an implementation milestone. It does not authorize current work to skip the existing ForgeRL environment gates.

---

## 1. Near-term path remains unchanged

The current ForgeRL path remains:

```text
FRL-02L1 SIMULTANEOUS_TRIGGER_ORDER
    -> FRL-02L2 SURVEIL_PARTITION_PLUS_ORDER
    -> MODERN DAMAGE_ASSIGNMENT
    -> FULL RUNTIME GAP AUDIT
    -> GAP CLOSURE, especially PAYMENT
    -> ZERO-UNSUPPORTED GATE
    -> RandomLegalPolicy
    -> ForgeRL benchmark
    -> observation / training-contract freeze
    -> Forge teacher trajectories
    -> Behavior Cloning
    -> online-RL algorithm bake-off
    -> self-play
    -> Constructed generalization
    -> Commander pivot
```

No Commander work should weaken or bypass the current requirements for:

```text
unsupported agent-required boundaries = 0
Forge-AI fallback                    = 0
MAPPING_FAILED                       = 0
TRACE_INCOMPLETE                     = 0
```

on the representative workload at the relevant environment gate.

---

## 2. Keep a permanent Constructed anchor

At least one current Constructed deck remains a long-lived regression anchor through the Commander pivot.

Conceptually:

```text
ANCHOR_DECK_V1
    -> current fixed matchup
    -> later new Constructed opponents
    -> historical model comparisons
    -> retained regression suite after Commander begins
```

The anchor is not the only deck used forever. Its purpose is longitudinal comparability:

```text
same deck
same matchup definitions
same seeds where applicable
same environment/checkpoint metadata
```

so changes in environment or policy quality can be measured against a stable reference.

The Constructed anchor remains available even after Commander becomes the main development target.

---

## 3. Commander starts as 1v1, not multiplayer

The first Commander environment target is:

```text
official Commander-relevant rules
1v1
fixed controlled decks
full games
```

Then:

```text
Commander 1v1 environment
    -> Commander 1v1 RandomLegalPolicy
    -> Commander 1v1 benchmark
    -> Commander 1v1 teacher / BC / RL
    -> Commander base model
```

The project should stay in 1v1 for as long as needed to make the Commander environment and policy strong.

This phase may last months. Multiplayer is not a prerequisite for a useful or complete Commander research program.

Commander 1v1 is expected to cover most card- and deck-level complexity first, including where applicable:

```text
100-card singleton decks
Commander zone semantics
Commander tax
color identity
Commander damage
large card pools
tutors and combos
stack interaction
hidden information
history
large deck-to-deck variation
```

Multiplayer-specific strategic problems are intentionally deferred.

---

## 4. One shared Commander base model

Do not train five unrelated bracket models from scratch unless evidence later shows that this is necessary.

Preferred model lineage:

```text
strong Constructed foundation
        -> Commander 1v1 foundation
        -> COMMANDER_BASE
              |-> COMMANDER_B1
              |-> COMMANDER_B2
              |-> COMMANDER_B3
              |-> COMMANDER_B4
              `-> COMMANDER_B5
```

`COMMANDER_BASE` should learn the transferable Commander skills shared across power levels, such as:

```text
rules and legal decisions
stack interaction
combat
resource management
hidden-information reasoning
history use
card/entity representation
Commander-specific rules
basic threat/value estimation
```

The B1-B5 models then specialize from the common base instead of relearning Magic independently.

The checkpoint family should preserve lineage and compatibility metadata, for example:

```text
commander_base_vN
commander_b1_vN
commander_b2_vN
commander_b3_vN
commander_b4_vN
commander_b5_vN
```

Exact naming is an implementation detail; shared ancestry is the architectural intent.

---

## 5. Five user-facing bracket specialists

The intended user-facing Commander model family contains one specialist per bracket bucket:

```text
COMMANDER_B1
COMMANDER_B2
COMMANDER_B3
COMMANDER_B4
COMMANDER_B5
```

The purpose is not to make B5 the default opponent for every player.

Instead, normal matchmaking should pair the player with the specialist appropriate to the deck-power bucket selected or assigned by the product/runtime configuration.

Conceptually:

```text
player deck metadata: B3
        -> select COMMANDER_B3
        -> AI receives a B3-appropriate deck
        -> B3-vs-B3 game
```

A player using a B3 deck should not be forced to play the B5/cEDH-oriented specialist with a B5 deck merely because B5 is the highest-power model.

Each specialist should become competent at its own environment.

---

## 6. Deck power and AI difficulty are separate axes

Do not equate a stronger AI difficulty setting with a stronger deck bracket.

The product/training model should preserve two independent dimensions:

```text
DECK POWER / BRACKET BUCKET
B1 B2 B3 B4 B5

AI DECISION QUALITY / DIFFICULTY
for example: Easy / Normal / Strong / Expert
```

Therefore a valid user-facing configuration is:

```text
Player deck: B3
AI deck:     B3
AI model:    COMMANDER_B3
Difficulty:  Strong
```

Increasing AI difficulty should primarily improve decision quality, search budget, policy quality, or another explicitly defined policy mechanism. It should not silently replace the opponent deck with a higher bracket.

Cross-bracket games may exist as explicit challenge or research modes, but are not the default fairness model.

---

## 7. Start each bracket with one anchor deck

The first specialization stage should remain controlled.

Initial Commander specialist ladder:

```text
B1 anchor deck -> COMMANDER_B1 initial specialist
B2 anchor deck -> COMMANDER_B2 initial specialist
B3 anchor deck -> COMMANDER_B3 initial specialist
B4 anchor deck -> COMMANDER_B4 initial specialist
B5 anchor deck -> COMMANDER_B5 initial specialist
```

Each bracket therefore receives a permanent anchor workload analogous to the Constructed anchor.

The first goal is not immediate universal deck generalization. It is to establish that each specialist can play one representative deck at its intended power level with:

```text
complete ForgeRL ownership
stable evaluation
reproducible checkpoints
measurable learning progress
```

---

## 8. Expand to multiple decks per bracket

After the five fixed-deck specialists are stable, expand each bracket independently:

```text
B1: deck A, B, C, ...
B2: deck A, B, C, ...
B3: deck A, B, C, ...
B4: deck A, B, C, ...
B5: deck A, B, C, ...
```

This stage converts a fixed-deck specialist into a bracket specialist that can generalize across multiple commanders, archetypes, and card pools within its bucket.

Every added deck or deck family must run through coverage certification rather than assuming that ForgeRL support transfers automatically.

Suggested certification dimensions include:

```text
complete games
agent-required callback coverage
unsupported count
Forge-AI fallback count
MAPPING_FAILED count
TRACE_INCOMPLETE count
DecisionType distribution
hidden-information safety
performance
```

The first new decks are expected to expose more environment gaps than later additions. Coverage should therefore be treated as an expanding frontier, not as a one-time binary property.

---

## 9. Bracket metadata must not become game rules

Bracket assignment is metadata for curriculum, evaluation, model selection, and matchmaking. It is not a replacement rules engine.

Do not encode logic such as:

```text
if bracket == B3:
    change Magic legality
```

Forge remains authoritative for Magic and Commander rules.

Bracket information should be represented through versioned external metadata/configuration, for example conceptually:

```text
bracket_scheme_version
bracket_bucket
anchor_deck_id
model_specialization_id
```

This keeps the AI architecture robust if the external bracket scheme changes over time.

Do not hard-code permanent assumptions that there can only ever be exactly five external buckets into Forge's rules semantics.

The current roadmap nevertheless targets five specialist buckets, B1 through B5, because that is the intended product/training organization for this phase.

---

## 10. A single all-bracket model is optional, not required

The five specialists are allowed to remain the production architecture.

There is no requirement that:

```text
COMMANDER_B1 ... COMMANDER_B5
```

must eventually be replaced by one universal model.

A cross-bracket Commander generalist may be trained later as a research experiment or shared-base improvement, but it must earn replacement through evaluation.

If five specialized checkpoints provide better power-level fidelity, training stability, or user experience, retaining five production specialists is a valid final design.

---

## 11. Multiplayer is explicitly deferred

Do not begin multiplayer Commander merely because the Commander rules environment exists.

Multiplayer starts only after the 1v1 Commander model family is sufficiently mature.

Target order:

```text
Commander 1v1 environment
    -> COMMANDER_BASE
    -> COMMANDER_B1
    -> COMMANDER_B2
    -> COMMANDER_B3
    -> COMMANDER_B4
    -> COMMANDER_B5
    -> multiple decks / bracket generalization
    -> all five bracket models trained and evaluated
========================================================
ONLY THEN: Multiplayer Commander
========================================================
```

This deliberately pushes multiplayer months behind the initial Commander pivot if required.

The project should not spend current engineering time solving multiplayer-only strategy while 1v1 Commander coverage, training, or bracket specialization is still incomplete.

Architectural interfaces may remain future-compatible with multiple seats, but that is different from implementing or training multiplayer now.

---

## 12. Multiplayer entry gate

Before starting the dedicated multiplayer phase, require evidence that the 1v1 program is mature enough to serve as its foundation.

The exact numerical thresholds should be defined later, but the gate should include at least:

```text
Commander 1v1 representative games complete through ForgeRL
zero or explicitly accepted unsupported boundaries on certified pools
no silent Forge-AI fallback
stable hidden-information/history contract
stable Commander base checkpoint
trained B1 specialist
trained B2 specialist
trained B3 specialist
trained B4 specialist
trained B5 specialist
multiple-deck evaluation within each target bracket
sufficient simulation/inference performance for multi-agent experiments
```

If these conditions are not met, multiplayer remains deferred.

---

## 13. Why waiting helps multiplayer

When multiplayer eventually begins, the project should already possess a rich opponent pool:

```text
B1 current checkpoints
B1 historical checkpoints
B2 current checkpoints
B2 historical checkpoints
...
B5 current checkpoints
B5 historical checkpoints
```

That is substantially better than beginning multiplayer self-play with four copies of one untrained or newly initialized policy.

Historical and bracket-specialized policies can provide opponent diversity and reduce the risk that one current self-play policy develops brittle private conventions.

For a normal bracket-specific multiplayer curriculum, use predominantly same-bracket opponents and decks.

Example:

```text
B3 learner
    vs B3 checkpoint v12
    vs B3 checkpoint v9
    vs B3 checkpoint v6
```

Mixed-bracket pods are useful research/challenge workloads, but should remain distinct from normal fair bracket-specific evaluation.

---

## 14. Multiplayer-specific work begins only in the multiplayer phase

Once the multiplayer gate is passed, the roadmap becomes:

```text
3-player environment validation
    -> 4-player environment validation
    -> multi-opponent observation and history validation
    -> multiplayer threat assessment
    -> multi-agent opponent pool / league
    -> multiplayer self-play
    -> strong multiplayer Commander agents
```

This phase must address genuinely multiplayer-specific problems such as:

```text
variable seat count
multiple opponents
APNAP across more than two players
multi-opponent target selection
opponent modelling
threat assessment
kingmaking-sensitive decisions
resource expenditure that benefits third parties
multi-agent credit assignment
larger observation/action domains
```

These are intentionally not prerequisites for the B1-B5 1v1 specialist program.

---

## 15. Long-term roadmap summary

```text
PHASE A -- Constructed environment
    complete v0 decision boundaries
    zero-unsupported gate
    RandomLegalPolicy

PHASE B -- Constructed learning
    BC
    RL bake-off
    self-play
    multi-deck generalization
    retain Constructed anchor

PHASE C -- Commander 1v1 foundation
    Commander rules/environment
    Commander 1v1 zero-unsupported coverage
    Commander 1v1 training
    COMMANDER_BASE

PHASE D -- Commander 1v1 specialists
    COMMANDER_B1
    COMMANDER_B2
    COMMANDER_B3
    COMMANDER_B4
    COMMANDER_B5

PHASE E -- Bracket generalization
    multiple decks per bracket
    certified coverage
    stable specialist family

PHASE F -- Multiplayer Commander, deferred
    only after B1-B5 family is trained/evaluated
    3 players
    4 players
    multi-agent opponent pool / league
```

The intended product progression is therefore:

> first build a strong Magic 1v1 foundation, then a strong Commander 1v1 base, then five bracket-specialized Commander model families, and only after those models exist use them as the foundation and opponent pool for multiplayer Commander.
