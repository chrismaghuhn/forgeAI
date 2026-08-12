# FRL-02K-C2A - Triggered TARGET Provider Seam Audit

Status: C2A narrow production seam present; bounded quality-review revalidation completed with 81/81 focused C2A
tests and 20/20 retained/API/confirmation tests. The earlier broad reactor and package results are historical and
not current after the production routing changes; broad/package revalidation remains required.

Audit date: 2026-08-12

Repository: `chrismaghuhn/forgeAI`

Audit worktree: `C:\forgeAI-triggered-target-c2a`

Branch: `frl/02k-c2a-triggered-target-provider-seam`

Quality-review checkpoint HEAD before the fixes: `92cfca97aed30ad5468cc2e171d77a798d9e16ef`
(`fix: close nested Charm triggered target bypass`); branch ahead count was 27 relative to `origin/master`.

Base: `3851fdf3825` (`origin/master`, `FRL-02K-C2: audit triggered target ownership`)

This is the bounded quality-review revalidation checkpoint for the committed C2A seam. It documents the exact
admitted Blood Operative ETB target shape, the measured native/external ownership boundary, and the focused,
retained/API/confirmation, and configured-validation results actually rerun for this review. It does not generalize
triggered TARGET or add a CONFIRMATION boundary.

Evidence labels:

- `[BESTAETIGT]` - directly established by current source or a completed focused gate.
- `[STARKES INDIZ]` - reproducible evidence retained from a completed checkpoint, with a narrower boundary than a broad validation.
- `[UNKLAERT]` - not established by this worktree or its completed gates.
- `[BLOCKER]` - must remain closed before the relevant scope can be widened.

## 1. Checkpoint and decision

[BESTAETIGT] At the start of this quality review, `git status --short --branch` was clean. The requested branch was
`frl/02k-c2a-triggered-target-provider-seam...origin/master [ahead 27]` at
`92cfca97aed30ad5468cc2e171d77a798d9e16ef`; `3851fdf3825` is `origin/master`. The review then added bounded
production, regression-test, continuation-fixture, and audit-document corrections in this task.

The decision is deliberately narrow:

```text
Blood Operative ETB TARGET: admitted and operational for the exact profile below
global triggered TARGET: not admitted by this coordinator
Blood CONFIRMATION: not implemented
global CONFIRMATION: not implemented
```

The existing C2/C2R material remains evidence, not a claim that C2A is a universal target or confirmation adapter.

## 2. Exact production API and ownership split

[BESTAETIGT] The ownership is split at the controller/provider/coordinator boundaries, not through a global
provider or global request counter.

| Owner | Exact responsibility |
|---|---|
| `PlayerController` | Owns one `private final TargetDecisionProvider targetDecisionProvider` and one nullable `TargetDecisionProvider.Resolver targetDecisionResolver`; exposes final get/set accessors. The resolver is the per-controller external ownership switch. |
| `TargetDecisionProvider` | Enumerates Forge-legal public target candidates, creates immutable request values, and applies one selected candidate through the live Forge `TargetChoices`. Its `private long nextRequestId` is monotonic only within that provider instance; independent providers restart their local sequence. Request IDs are not globally unique. |
| `TargetDecisionProvider.Generation` | Returns `DECISION`, `COMPLETE`, or `INVALID_TARGETING`. Only `DECISION` carries a request. `COMPLETE` means the current target group is complete after Forge mutation/reassessment; `INVALID_TARGETING` means Forge cannot supply the mandatory target state. |
| `TriggeredTargetDecisionCoordinator` | Stateless admission/orchestration boundary. It has no mutable per-game fields. It checks the exact profile, rejects active continuations, calls the provider on the underlying live ability, routes native versus external ownership, and records the terminal V2 provenance. |
| `PlayerControllerAi` | Thin routing only: `prepareSingleSa` passes the wrapped ability, wrapper decider, controller-local provider, and nullable resolver to the coordinator; it invokes the native adapter only for the coordinator's native statuses and otherwise keeps the existing Forge path. |

[BESTAETIGT] `TargetDecisionProvider.apply(request, candidate)` is the authoritative Forge legality/mutation/completion
boundary. It requires a live `TARGET` request and a member candidate, rechecks current Forge legality, adds the
selected object to the live `TargetChoices`, and generates the next request or `COMPLETE`. The coordinator does not
duplicate legality or write a second target structure. For an externally owned target, a successful
`provider.apply(request, candidate)` must return a `COMPLETE` generation and leave exactly one live target identity
for this slice. For this admitted profile the initial target list is empty and the minimum is one, so an initial
`COMPLETE` generation is impossible; if observed on the admitted empty initial state, it is an integrity failure
rather than an external success. Native ownership is separate: a native callback can succeed by mapping its
post-callback identity to the immutable teacher request; it does not call `provider.apply` and does not require
completion regeneration.

The relevant source is `forge-game/src/main/java/forge/game/player/PlayerController.java`,
`forge-game/src/main/java/forge/game/decision/TargetDecisionProvider.java`,
`forge-game/src/main/java/forge/game/decision/TriggeredTargetDecisionCoordinator.java`, and
`forge-ai/src/main/java/forge/ai/PlayerControllerAi.java`. The ownership introduction is retained in commit
`7616455fe07`; the coordinator and routing are retained in `9b5367dcea8` and `19cbe984fd4`.

## 3. Exact Blood admission profile

[BESTAETIGT] The exact admitted profile identifier is
`BLOOD_OPERATIVE_ETB_EXILE_GRAVEYARD_CARD_TARGET`. Admission is a semantic profile check. The source and trigger
provenance must be public and ordinary:

- Source is `Blood Operative`, in `CardStateName.Original`, not cloned, not face-down, and visible to both chooser and decider.
- The trigger is intrinsic, non-static, `ChangesZone`, has no spawning ability, and is not copied. The `WrappedAbility` and its live underlying ability are also intrinsic and non-copied.
- The chooser, wrapper decider, live activating player, and source controller are the same Forge player/seat. No alternate targeting player is accepted.

The trigger definition is normalized from the original and runtime maps. Its semantic keys are exactly:

```text
Mode=ChangesZone
Origin=Any
Destination=Battlefield
ValidCard=Card.Self
OptionalDecider=You
Execute=TrigChangeZone
```

The source's static `TrigChangeZone` SVar is normalized to the exact effect shape:

```text
DB=ChangeZone
Origin=Graveyard
Destination=Exile
ValidTgts=Card
```

The live underlying ability must be `ApiType.ChangeZone`, with Forge-normalized Graveyard target legality and a
single card target:

```text
TgtZone=Graveyard
TargetMin=1
TargetMax=1
initial TargetChoices: empty
sub/additional abilities: absent
pay cost: free
random target: false
random target count: false
```

The underlying ability must not carry `Optional`, `TargetingPlayer`, or a resolved targeting player. Optionality is
owned by the trigger's `OptionalDecider=You`; it is not duplicated on the underlying ChangeZone ability. The target
is therefore `Graveyard -> Exile`, `Card`, min=max=1, with no underlying optional flag. Target selection is
explicitly non-random: both Forge `TargetRestrictions.isRandomTarget()` and
`TargetRestrictions.isRandomNumTargets()` must be false.

[BESTAETIGT] `TriggerDescription`, `TgtPrompt`, and `ValidTgtsDesc` are presentation text and are removed from
normalization. Changing them does not change admission. Unknown semantic keys do change admission: an unknown
original trigger parameter, unknown live parameter, runtime effect mismatch, copied/generated/spawned provenance,
cloned source, non-empty initial targets, target-bound mismatch, or chooser mismatch is rejected as
`UNSUPPORTED_TARGETED_TRIGGER` rather than guessed through.

The admission and bound tests are in
`forge-gui-desktop/src/test/java/forge/game/decision/TriggeredTargetDecisionCoordinatorTest.java`; the public
profile fixture and external ownership test are in
`forge-gui-desktop/src/test/java/forge/view/FRL02KTriggeredTargetExternalOwnershipAuditTest.java`.

## 4. Runtime, native, and external ownership

### 4.1 Native path: nullable resolver is the Forge-preserving path

[BESTAETIGT] With a null controller resolver, the coordinator performs one provider generation for an admitted Blood
trigger. Only a `DECISION` generation enters `NATIVE_WITH_TEACHER_CAPTURE`: that generation supplies the immutable
teacher request/candidate view and the `List.copyOf` pre-target snapshot, records one `DECISION_TRACE_V2` request,
and then invokes the existing native Forge-AI target adapter exactly once. `completeNative` compares the
post-callback target list by object identity, requires exactly one newly added object, and maps that identity to
exactly one target candidate from the captured request. A false native result or any missing identity
mapping is `MAPPING_FAILED`; it is not converted into an external choice.

For the admitted empty initial state, `INVALID_TARGETING` is the native no-stack result. It is returned as the
native preparation outcome without a `DecisionRequest`, teacher capture, or trace capture; it does not become an
external request. `COMPLETE` on that state would contradict the empty initial target list and minimum-one profile
requirement, so it is an integrity failure (`TARGET_APPLICATION_INCOMPLETE`), not a native or external success.

This keeps the existing `brains.doTrigger` behavior and no-stack Blood route. It is teacher capture/diagnostic
mapping, not external policy ownership.

### 4.2 External path: resolver owns the target once

[BESTAETIGT] With a non-null controller resolver, only the admitted Blood profile can become `PREPARED`:

| Runtime shape | C2A result |
|---|---|
| No trigger or non-targeted ability | `NOT_APPLICABLE`; leave the normal native Forge path available. |
| Targeted but unsupported profile | Fail closed with `UNSUPPORTED_TARGETED_TRIGGER`; no resolver call and no Forge-AI fallback. |
| Zero legal Graveyard candidates | Provider returns `INVALID_TARGETING`; no impossible external request is exposed and no stack entry is created. The null-resolver path preserves Forge's native no-stack result. |
| One legal candidate | Request is forced; coordinator applies the sole candidate exactly once and records engine-forced terminal history without a policy callback. |
| Many legal candidates | Resolver is called once with the exact request; the selected candidate must be a member `TARGET_CARD` in that request, and provider `apply` is called exactly once. |

The external route never invokes the Forge-AI target callback, so external target A cannot be silently replaced by a
Forge-AI target. The applied live `TargetChoices` remains authoritative through the later optional trigger decision
and effect resolution. The later confirmation-time temporary evaluation is not a second C2A request. If the stored
target becomes illegal at stack time, Forge fizzles/clears the stale target according to its normal legality path;
C2A does not retarget or select a replacement.

[BESTAETIGT] A `RuntimeException` from `resolver.resolve(request)` is sanitized to
`INVALID_EXTERNAL_CANDIDATE`. It never falls back to the native Forge-AI callback and is never reclassified as
`MAPPING_FAILED`. The already-open `DECISION_TRACE_V2` request has no selected candidate; normal finalization leaves
the trace open for `TRACE_INCOMPLETE`. Resolver exception text and other private details are not exported.

## 5. Trace and integrity contract

[BESTAETIGT] C2A uses `DECISION_TRACE_V2` only. No V3 schema or new BC sample rule was introduced.

| Path | `nativeCallbackCompleted` | `mappingAttempted` | Terminal history | BC policy sample |
|---|---:|---:|---|---:|
| Native target mapped | `true` | `true` | `CHOSEN` | Eligible only when not forced and otherwise valid. |
| External target selected | `false` | `false` | `CHOSEN` | No. This is valid external history, not native behavior-cloning data. |
| Forced target | Engine-forced or native `true/true` | See path | `FORCED` | No; forced history is excluded. |
| Native callback could not map | `true` | `true` | `MAPPING_FAILED` | No; native mapping failure only. |
| Invalid external candidate | `false` | `false` | Sanitized to no selected candidate and `TRACE_INCOMPLETE` after finalization | No; it is not mislabeled as `MAPPING_FAILED`. |

`DecisionTraceTrainingValidator` preserves the existing rule: a BC sample is a valid, non-forced `CHOSEN` result
with a legal selected semantic key and native/mapping flags `true/true`. External TARGET history is valid only with
`false/false`, and forced results remain non-BC. Duplicate terminals, unknown request references, missing
terminals, illegal selected keys, and duplicate semantic candidate keys remain rejected.

[BESTAETIGT] The continuation guard runs before provider generation and before any resolver or native callback. The
fresh-JVM Task 8 child produced these exact four lines:

```text
reason=UNSUPPORTED_ACTION_CONTINUATION
provider_requests=0
resolver_calls=0
native_calls=0
```

The guard therefore does not invent a target request or attach a triggered target to a priority continuation.
Evidence is in `forge-game/src/main/java/forge/game/decision/DeterminismTrace.java`,
`forge-game/src/main/java/forge/game/decision/DecisionTraceTrainingValidator.java`,
`forge-gui-desktop/src/test/java/forge/game/decision/DeterminismTraceV2Test.java`, and
`forge-gui-desktop/src/test/java/forge/game/decision/TriggeredTargetContinuationProcessTest.java`.

## 6. Evidence matrix

The local XML reports under `forge-gui-desktop/target/surefire-reports/junitreports/` are generated focused-gate
artifacts, not a replacement for source or commit history. They contain no exported hidden host/card state; this
audit likewise records only the public profile and the two named public fixture choices required for ownership
comparison.

| Gate / checkpoint | Completed outcome | Evidence and qualification |
|---|---|---|
| Quality-review starting checkpoint | `[BESTAETIGT]` clean requested branch/worktree; checkpoint HEAD `92cfca97aed30ad5468cc2e171d77a798d9e16ef`; base `3851fdf3825`; branch ahead 27 | `git status --short --branch`, `git rev-parse HEAD`, and `git rev-list --left-right --count origin/master...HEAD`; current worktree `C:\forgeAI-triggered-target-c2a` |
| Quality-review RED baseline | `[BESTAETIGT]` 17 total; 12 passed, 5 failed, 0 errors, 0 skips | Test-only RED run before production edits; four targeted additional-child cases were not rejected and the cyclic-parent case reached `StackOverflowError` in recursive `SpellAbility.getTrigger` |
| Provider/API | `[BESTAETIGT] 32/32` | `TargetDecisionProviderTest` 27/27 + `FRL02KTriggeredTargetProviderAuditTest` 3/3 + `DecisionPublicApiReflectionTest` 2/2 in the retained JUnit reports |
| Task 5 coordinator checkpoint | `[BESTAETIGT] 15/17` before Task 6; two request failures were explicitly deferred | Retained Task 5 gate outcome; the later correction/orchestration commits are `c2779afa449`, `9b5367dcea8`, and `0f85ab32582` |
| Task 6 / Task 10 coordinator | `[BESTAETIGT] 28/28` | `TriggeredTargetDecisionCoordinatorTest`; includes native 0/1/many and five native mapping-failure tests: callback false, zero new targets, multiple new targets, foreign target, and the duplicate-target setup that reaches the multiple-new-target guard. Forge rejects the duplicate live identity; this case does not construct an ambiguous identity mapping |
| Task 6 focused gate | `[BESTAETIGT] 26/26` after Task 8; pre-Task 8 was 25/26 with one known validator RED | Completed post-Task 8 focused gate; no broad reactor/build result is inferred |
| Task 8 validator/continuation | `[BESTAETIGT] 10/10` = V2 validator/trace 9/9 plus fresh-JVM continuation 1/1 | `DeterminismTraceV2Test` and `TriggeredTargetContinuationProcessTest`; exact child output is in section 5 |
| External ownership and boundary regressions | `[BESTAETIGT] 16/16` | `FRL02KTriggeredTargetExternalOwnershipAuditTest`; includes native/external ownership, five invalid-candidate cases, throwing-resolver sanitization, copied/non-wrapped/Charm/nested-child rejection, four additional-child routes (`TrueSubAbility`, `FalseSubAbility`, `FallbackAbility`, and a non-`Choices` additional list), and the bounded cyclic-parent rejection. All resolver/native/chooser/order/stack fallback counters remain zero on the fail-closed routes. |
| Throwing-resolver focused gate | `[BESTAETIGT] 1/1` | `throwingResolverFailsClosedWithoutNativeFallbackOrMappingFailure`; `RuntimeException` is sanitized to `INVALID_EXTERNAL_CANDIDATE`, with no native fallback and no `MAPPING_FAILED` reclassification |
| Current focused C2A suite | `[BESTAETIGT] 81/81` | Exact selector ran `TriggeredTargetDecisionCoordinatorTest` 28/28, `TriggeredTargetContinuationProcessTest` 1/1, `FRL02KTriggeredTargetExternalOwnershipAuditTest` 16/16, `DeterminismTraceV2Test` 9/9, and `TargetDecisionProviderTest` 27/27; 0 failures/errors/skips; Maven `BUILD SUCCESS`. |
| Current retained/API/confirmation suite | `[BESTAETIGT] 20/20` | Exact selector ran `FRL02KTriggeredTargetProviderAuditTest` 3/3, `DecisionPublicApiReflectionTest` 2/2, `PriorityActionDiagnosticsTest` 11/11, and `forge.ai.ability.FRL02KConfirmationAuditTest` 4/4; 0 failures/errors/skips; Maven `BUILD SUCCESS`. |
| Earlier Task 12 C canonical/native ownership-difference workload | `[UNKLAERT] historical only; not current` | The prior `FRL02KTriggeredTargetOwnershipAuditTest` result belongs to the earlier Task 12 checkpoint and was not counted after the 9e4a0ec, 6443e76, 92cfca97 routing changes and this review. A combined rerun exceeded the bounded 120-second command limit and was terminated; no current pass is claimed. |
| Earlier Task 12 D broad reactor tests | `[UNKLAERT] historical only; not current` | The 720-test result (714 passed, 6 configured stress skips) from the 7e014aff Task 12 documentation checkpoint is retained as history, not current evidence after production routing changes. Broad revalidation was not rerun in this bounded review. |
| Earlier Task 12 E package build | `[UNKLAERT] historical only; not current` | The earlier package success from the 7e014aff Task 12 documentation checkpoint is retained as history, not current evidence after production routing changes. Package revalidation was not rerun in this bounded review. |
| Current configured Checkstyle/validation | `[BESTAETIGT] clean (0 violations)` | Exact `mvn -pl forge-gui-desktop -am validate` completed with Maven `BUILD SUCCESS`; configured `checkstyle-validation` reported 0 violations in all six reactor modules. `git diff --check` also exited 0. |
| Earlier canonical reference workload | `[UNKLAERT] historical only; not current` | Izzet Guild Kit vs Dimir Guild Kit, 10 games, seed `20260810`, remains prior evidence in the confirmation/determinism reports and was not rerun in this bounded review. |

### 6.1 Quality-review exact commands

The bounded quality-review verification commands were run from `C:\forgeAI-triggered-target-c2a`:

```text
mvn -pl forge-gui-desktop -am '-Dtest=FRL02KTriggeredTargetExternalOwnershipAuditTest,TriggeredTargetContinuationProcessTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -pl forge-gui-desktop -am '-Dtest=TriggeredTargetDecisionCoordinatorTest,TriggeredTargetContinuationProcessTest,FRL02KTriggeredTargetExternalOwnershipAuditTest,DeterminismTraceV2Test,TargetDecisionProviderTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -pl forge-gui-desktop -am '-Dtest=FRL02KTriggeredTargetProviderAuditTest,DecisionPublicApiReflectionTest,PriorityActionDiagnosticsTest,FRL02KConfirmationAuditTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -pl forge-gui-desktop -am validate
git diff --check
```

The first selector is the explicit RED/GREEN regression gate: RED was 17 total with 5 expected failures before
production edits; GREEN was 17/17. The combined C2A selector then passed 81/81, and the retained/API/confirmation
selector passed 20/20. A separate combined attempt that included the long canonical ownership workload exceeded the
120-second bounded command limit and was terminated; it is not evidence of a pass. The earlier Task 12 broad and
package commands remain historical and are intentionally not repeated here.

The prior Task 12 commands, retained for historical traceability only, were:

```text
mvn -pl forge-gui-desktop -am test
mvn -pl forge-gui-desktop -am -DskipTests package
```

The fresh continuation child still emits exactly:

```text
reason=UNSUPPORTED_ACTION_CONTINUATION
provider_requests=0
resolver_calls=0
native_calls=0
```

The child now installs a non-null controller resolver that increments an `AtomicInteger` and returns `null`; the
zero resolver count is therefore an observed boundary result rather than a hard-coded fixture value.

The current A aggregate was an individual JUnit/TestNG result with no stdout payload; the 81 passing assertions are
therefore recorded as the class-level counts above. The external-ownership method itself asserts the native/external
choice and callback-count evidence, and it never invokes the native Forge-AI callback on the external route.

The canonical reference command retained by the existing audit is:

```text
java [diagnostic properties] -cp ..\forge-gui-desktop\target\forge-gui-desktop-2.0.14-SNAPSHOT-jar-with-dependencies.jar forge.view.Main sim -d "Izzet Guild Kit" "Dimir Guild Kit" -n 10 -s 20260810 -q
```

## 7. Remaining scope

[BESTAETIGT] C2A supports only the exact Blood Operative ETB target profile in this document. The following remain
open and must not be inferred from the focused target gates:

- global triggered `TARGET` admission;
- Blood `CONFIRMATION` ownership;
- global `CONFIRMATION` ownership;
- any copied, granted, hidden, static, generated, alternate-chooser, multi-effect, or otherwise different Blood shape;
- the six explicitly skipped `NetworkPlayIntegrationTest` stress cases unless a separate run enables
  `-Drun.stress.tests=true`.

Blood is not agent-complete. Its ETB target is supported only within the exact profile and only as `TARGET`; the
later optional trigger decision remains outside this C2A boundary.

## Narrow status

```text
Blood Operative ETB TARGET: SUPPORTED (exact profile only)
global triggered TARGET: OPEN
Blood CONFIRMATION: OPEN
global CONFIRMATION: OPEN
Current bounded C2A gates: VALIDATED (81/81 focused; 20/20 retained/API/confirmation; validate/checkstyle clean)
Task 12 broad reactor/package: HISTORICAL ONLY (not current after production routing changes)
FRL_02K_C2A_REVALIDATION_REQUIRED
```
