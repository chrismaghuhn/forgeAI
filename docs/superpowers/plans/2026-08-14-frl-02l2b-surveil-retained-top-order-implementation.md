# FRL-02L2B Surveil Retained-Top ORDER Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the approved FRL-02L2B shared Surveil seam so one parent
session owns typed L2A partitioning, optional TOP_FIRST retained-top ORDER,
native capture, external resolution, and native-compatible Pair handoff.

**Architecture:** Extend the existing SurveilPartitionSession rather than
creating a second public parent or a generic continuation engine. Add a narrow
typed L2B ORDER contract to DecisionRequest, LegalCandidate, and Decision Trace
V3; keep L2A itemIds and canonical projection authoritative; and make the
coordinator select NATIVE or EXTERNAL before fallible parent admission. Native
capture preserves the exact callback Pair, while EXTERNAL resolves the two
approved stages and synthesizes fresh mutable Pair collections only after full
invariant validation.

**Tech Stack:** Java 17, Maven, Forge game/GUI-desktop modules, existing
DecisionRequest/LegalCandidate and DeterminismTrace V2/V3 infrastructure,
Apache Commons Pair, and TestNG-based focused tests.

---

## Authority and execution boundary

The normative source is:

~~~text
docs/superpowers/specs/2026-08-14-frl-02l2b-surveil-retained-top-order-design.md
~~~

The existing L2A implementation and its plan provide repository conventions:

~~~text
docs/superpowers/specs/2026-08-14-frl-02l2a-surveil-partition-design.md
docs/superpowers/plans/2026-08-14-frl-02l2a-surveil-partition-implementation.md
~~~

The implementation must preserve the current engine boundary:

~~~text
Player.surveil
  → existing top-N snapshot
  → SurveilPartitionDecisionCoordinator
  → exact native-compatible Pair
  → existing Pair.right movement, Pair.left reversal, events, and triggers
~~~

This plan does not authorize implementation during plan authoring. The current
task writes this plan only. No production code, test code, commit, push, or
pull request is performed while saving the plan.

## File map

### New production files

- Create:
  forge-game/src/main/java/forge/game/decision/SurveilPartitionOwner.java
  Narrow NATIVE/EXTERNAL owner value used only by the shared Surveil seam.
- Create:
  forge-game/src/main/java/forge/game/decision/SurveilRetainedTopOrderProfile.java
  Typed V3 profile containing SURVEIL_RETAINED_TOP_ORDER.
- Create:
  forge-game/src/main/java/forge/game/decision/SurveilRetainedTopOrderDirection.java
  Typed context direction containing TOP_FIRST; never serialized as a trace
  field.
- Create:
  forge-game/src/main/java/forge/game/decision/SurveilRetainedTopOrderCandidateKind.java
  Typed candidate kind containing SELECT_NEXT_TOP.
- Create:
  forge-game/src/main/java/forge/game/decision/SurveilRetainedTopOrderContext.java
  Immutable public L2B context carrying only approved session, step, retained
  cardinality, and SurveilPartitionCard projection data.

### Modified production files

- Modify:
  forge-game/src/main/java/forge/game/decision/LegalCandidate.java
  Add the narrow L2B candidate payload, factory, getters, and semantic-key
  validation data.
- Modify:
  forge-game/src/main/java/forge/game/decision/DecisionRequest.java
  Add the mutually exclusive L2B context and exact typed ORDER validation
  without broadening existing L1/L1C ORDER or generic CARD_SELECTION rules.
- Modify:
  forge-game/src/main/java/forge/game/decision/SurveilPartitionSession.java
  Retain the same parent across L2A and L2B, preserve exact itemIds, and hold
  private retained-order progress without creating a second public session.
- Modify:
  forge-game/src/main/java/forge/game/decision/SurveilPartitionDecisionProvider.java
  Add owner/resolver configuration, L2B request/application methods, and
  idempotent registry-detaching close behavior.
- Modify:
  forge-game/src/main/java/forge/game/decision/SurveilPartitionDecisionCoordinator.java
  Implement owner-before-admission routing, complete native prevalidation,
  typed native L2B capture, external L2A/L2B resolution, Pair synthesis, and
  pre/post-PAIR_READY failure routing.
- Modify:
  forge-game/src/main/java/forge/game/decision/SurveilPartitionDiagnostics.java
  Add process-local counters for L2B requests, owner routing, Pair synthesis,
  and terminal outcomes without exposing session or Card identity.
- Modify:
  forge-game/src/main/java/forge/game/decision/DecisionTraceRequestRecord.java
  Add the L2B V3 profile and exact L2B-bearing predicate.
- Modify:
  forge-game/src/main/java/forge/game/decision/DeterminismTrace.java
  Make every L2B-bearing trace V3-authoritative, including an isolated L2B
  request; do not add a trace version or serialized direction field.
- Modify:
  forge-game/src/main/java/forge/game/decision/DecisionTraceTrainingValidator.java
  Add narrow external L2A support and the typed L2B fail-closed branch before
  generic ORDER fallback.

### New focused test files

- Create:
  forge-gui-desktop/src/test/java/forge/game/decision/SurveilRetainedTopOrderPublicApiTest.java
- Create:
  forge-gui-desktop/src/test/java/forge/game/decision/SurveilRetainedTopOrderDecisionEnvelopeTest.java
- Create:
  forge-gui-desktop/src/test/java/forge/game/decision/SurveilRetainedTopOrderSessionTest.java
- Create:
  forge-gui-desktop/src/test/java/forge/game/decision/SurveilRetainedTopOrderDecisionCoordinatorTest.java
- Create:
  forge-gui-desktop/src/test/java/forge/game/decision/SurveilRetainedTopOrderTraceTest.java
- Create:
  forge-gui-desktop/src/test/java/forge/game/decision/SurveilRetainedTopOrderEngineIntegrationTest.java
- Create:
  forge-gui-desktop/src/test/java/forge/view/FRL02L2BSurveilRetainedTopOrderAuditTest.java

### Existing tests kept green

- Keep:
  forge-gui-desktop/src/test/java/forge/game/decision/SurveilPartitionSessionTest.java
- Keep:
  forge-gui-desktop/src/test/java/forge/game/decision/SurveilPartitionTraceTest.java
- Keep:
  forge-gui-desktop/src/test/java/forge/game/decision/DecisionPublicApiReflectionTest.java
- Do not change the Human or AI arrangeForSurveil implementations.
- Do not change Player.surveil movement, replacement, event, trigger, or
  reversal behavior.

## Locked invariants for every task

The implementer must keep these facts visible while executing each task:

~~~text
actual top-N empty
  → no owner routing
  → no parent admission
  → no L2A/L2B request
  → existing Player.surveil behavior

non-empty snapshot
  → OWNER_SELECTED
  → fallible parent admission
~~~

~~~text
NATIVE:
  native arrangeForSurveil exactly once
  exact native Pair remains gameplay authority
  complete capture-plan prevalidation before first Surveil handle

EXTERNAL:
  native arrangeForSurveil exactly zero times
  no native fallback after owner selection
  owned failure before PAIR_READY is fail-closed
  cleanup failure after PAIR_READY cannot suppress the Pair or handoff
~~~

~~~text
same parent SURVEIL session
same surveilSessionId
same parent-session-local, stage-stable itemIds
L2A → optional L2B → PAIR_READY or terminal close
~~~

~~~text
R = 0: no L2B state, no request
R = 1: no L2B state, no forced request, order derived
R >= 2: exactly R - 1 non-forced ORDER requests
~~~

The final item is derived internally. There is no DONE candidate, whole
permutation candidate, pairwise protocol, artificial final request, generic
ORDER framework, or new DecisionType.

## Task 1: Add the narrow typed L2B public model

**Files:**

- Create:
  forge-game/src/main/java/forge/game/decision/SurveilPartitionOwner.java
- Create:
  forge-game/src/main/java/forge/game/decision/SurveilRetainedTopOrderProfile.java
- Create:
  forge-game/src/main/java/forge/game/decision/SurveilRetainedTopOrderDirection.java
- Create:
  forge-game/src/main/java/forge/game/decision/SurveilRetainedTopOrderCandidateKind.java
- Create:
  forge-game/src/main/java/forge/game/decision/SurveilRetainedTopOrderContext.java
- Create:
  forge-gui-desktop/src/test/java/forge/game/decision/SurveilRetainedTopOrderPublicApiTest.java

- [ ] Step 1: Add the owner and typed L2B enums

Use exact narrow enums:

~~~java
public enum SurveilPartitionOwner {
    NATIVE,
    EXTERNAL
}
~~~

~~~java
public enum SurveilRetainedTopOrderProfile {
    SURVEIL_RETAINED_TOP_ORDER
}

public enum SurveilRetainedTopOrderDirection {
    TOP_FIRST
}

public enum SurveilRetainedTopOrderCandidateKind {
    SELECT_NEXT_TOP
}
~~~

Do not add a generic ORDER direction or change the existing
OrderDirection.RESOLVE_FIRST contract.

- [ ] Step 2: Add the immutable public L2B context

Implement SurveilRetainedTopOrderContext with exactly these semantic fields:

~~~java
private final SurveilRetainedTopOrderProfile profile;
private final SurveilRetainedTopOrderDirection direction;
private final long surveilSessionId;
private final int decisionStepIndex;
private final int choosingPlayerId;
private final int retainedItemCount;
private final List<SurveilPartitionCard> retainedItems;
~~~

Expose read-only getters for those fields. Copy retainedItems with
List.copyOf. The context must reject null profile, direction, or retained
projection entries and negative step/cardinality values.

retainedItemCount is the original retained cardinality R and does not shrink.
The current legal remaining set is expressed by the request candidates, not by
exposing a private prefix, Pair, native order, or raw Card.

- [ ] Step 3: Add public API reflection and information-boundary tests

Add tests that assert:

~~~text
public context getters =
  profile, direction, surveilSessionId, decisionStepIndex,
  choosingPlayerId, retainedItemCount, retainedItems

public payload contains no:
  Card, CardView, CardLKI, cardId, gameTimestamp, snapshot ordinal,
  library position, zone, Pair, native order, graveyard order, AI, or RNG
~~~

Use the existing TestNG assertions and reflection style from
SurveilPartitionPublicApiTest. Add duplicate-looking
SurveilPartitionCard projections with distinct itemIds and assert that both
remain present.

- [ ] Step 4: Run the public-model test

Run:

~~~text
mvn -pl forge-gui-desktop -am "-Dtest=SurveilRetainedTopOrderPublicApiTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
~~~

Expected: the new typed model tests pass once the model is implemented; no
generic decision public-API test changes are needed.

## Task 2: Extend LegalCandidate and DecisionRequest with typed L2B semantics

**Files:**

- Modify:
  forge-game/src/main/java/forge/game/decision/LegalCandidate.java
- Modify:
  forge-game/src/main/java/forge/game/decision/DecisionRequest.java
- Create:
  forge-gui-desktop/src/test/java/forge/game/decision/SurveilRetainedTopOrderDecisionEnvelopeTest.java

- [ ] Step 1: Add the L2B candidate payload to LegalCandidate

Add fields parallel to the existing typed candidate families:

~~~java
private final SurveilRetainedTopOrderCandidateKind surveilRetainedTopOrderCandidateKind;
private final SurveilPartitionCard surveilRetainedTopOrderCard;
~~~

Add the factory:

~~~java
public static LegalCandidate surveilRetainedTopOrder(
        final int candidateId,
        final SurveilPartitionCard item) {
    return new LegalCandidate(candidateId,
            "SURVEIL_RETAINED_TOP_ORDER|SELECT_NEXT_TOP|" + item.getItemId(),
            SurveilRetainedTopOrderCandidateKind.SELECT_NEXT_TOP,
            item);
}
~~~

Use the existing constructor pattern so every unrelated payload is null.
Add getters for the two L2B fields. Do not reuse OrderCandidateKind,
SimultaneousTriggerOrderItem, CopySpellResolveFirstOrderItem, or a generic
permutation candidate.

- [ ] Step 2: Add the mutually exclusive L2B context to DecisionRequest

Add a field, constructor overload, and getter:

~~~java
private final SurveilRetainedTopOrderContext surveilRetainedTopOrderContext;

DecisionRequest(final long requestId, final DecisionType decisionType,
        final List<LegalCandidate> candidates,
        final SurveilRetainedTopOrderContext context)
~~~

The constructor validation must enforce:

~~~text
DecisionType = ORDER
exactly one ORDER context is present
L2B context profile = SURVEIL_RETAINED_TOP_ORDER
direction = TOP_FIRST
retainedItemCount >= 2
0 <= decisionStepIndex <= retainedItemCount - 2
candidate count = retainedItemCount - decisionStepIndex
candidateId = canonical request-local range 0..candidateCount-1
each candidate kind = SELECT_NEXT_TOP
each candidate Card projection belongs to retainedItems
each candidate semantic key is exact
no duplicate semantic keys or itemIds
no unrelated typed payload
~~~

When the L2B context is absent, preserve the current L1/L1C ORDER validation
behavior. When an L2B candidate payload is present without the L2B context,
reject it. Do not broaden generic CARD_SELECTION.

- [ ] Step 3: Add envelope tests for legal and illegal L2B requests

Cover these exact cases:

~~~text
valid R=2 request with two remaining candidates
valid R>=3 request with canonical remaining candidates
wrong profile
wrong direction
retainedItemCount < 2
candidate outside retainedItems
duplicate-looking card with the wrong itemId
wrong semantic key
non-contiguous request-local candidateId
decisionStepIndex outside 0..R-2
candidate count not equal to R - decisionStepIndex
generic L1/L1C ORDER payload attached to L2B
L2B payload attached to CARD_SELECTION
L2B candidate without L2B context
~~~

The envelope test must not inspect a private TOP_FIRST prefix. An
already-ordered/stale itemId is a session-state test and belongs in
SurveilRetainedTopOrderSessionTest.

Add regression assertions that existing Simultaneous Trigger ORDER, Copy Spell
ORDER, generic CARD_SELECTION, and existing Surveil partition requests keep
their current validation behavior.

- [ ] Step 4: Run the envelope tests and decision regression tests

Run:

~~~text
mvn -pl forge-gui-desktop -am "-Dtest=SurveilRetainedTopOrderDecisionEnvelopeTest,SurveilPartitionDecisionEnvelopeTest,CardSelectionDecisionProviderTest,DecisionPublicApiReflectionTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
~~~

Expected: typed L2B requests validate narrowly and existing envelope tests
remain green.

## Task 3: Extend the existing parent session through L2B

**Files:**

- Modify:
  forge-game/src/main/java/forge/game/decision/SurveilPartitionSession.java
- Modify:
  forge-game/src/main/java/forge/game/decision/SurveilPartitionDecisionProvider.java
- Create:
  forge-gui-desktop/src/test/java/forge/game/decision/SurveilRetainedTopOrderSessionTest.java

- [ ] Step 1: Add configured owner and resolver state to the provider

Add a controller-local resolver using the existing provider pattern:

~~~java
@FunctionalInterface
public interface Resolver {
    LegalCandidate choose(DecisionRequest request);
}

private SurveilPartitionOwner owner = SurveilPartitionOwner.NATIVE;
private Resolver resolver;

public void setOwner(final SurveilPartitionOwner owner);
public SurveilPartitionOwner getOwner();
public void setResolver(final Resolver resolver);
public Resolver getResolver();
public boolean hasResolver();
~~~

Default owner is NATIVE. Selecting EXTERNAL with no resolver remains EXTERNAL
and fails closed; it must not silently select NATIVE. Owner selection is read
before admission and is not a DecisionRequest.

- [ ] Step 2: Preserve the L2A state and add private L2B progress

Extend SurveilPartitionSession without creating a second public parent class.
Keep these existing authorities unchanged:

~~~text
nativeSnapshot
nativeItems IdentityHashMap
canonicalPolicyItems
visibleItems
nativeMembershipVector
retainedNativeList
visibleName → cardId → gameTimestamp comparator
~~~

Add private L2B state containing:

~~~java
private List<SurveilPartitionCard> retainedItems;
private List<Long> remainingRetainedItemIds;
private List<Long> topFirstPrefix;
private int retainedOrderStep;
private DecisionRequest openRetainedOrderRequest;
private List<Card> finalRetainedNativeOrder;
private final SurveilPartitionOwner selectedOwner;
private SurveilPartitionCandidateKind[] completedMembershipVector;
private boolean l2aComplete;
private boolean l2bComplete;
private boolean pairReady;
~~~

The selectedOwner is immutable parent state. The provider owner is only the
configuration read for a future operation. Pass the selected value into
admission:

~~~java
final SurveilPartitionOwner selectedOwner = provider.getOwner();
final SurveilPartitionSession session =
        provider.admit(chooser, privateSnapshot, selectedOwner);
~~~

Change the provider admission signature to accept selectedOwner and store it
as a final session field. Add a session owner getter for coordinator and
session validation. No session method may reread mutable provider owner
configuration after admission.

Rename the current L2A completion meaning to L2A_COMPLETE. Keep these states
separate:

~~~text
L2A_COMPLETE
  → partition labels are complete
L2B_COMPLETE
  → retained TOP_FIRST order is complete
PAIR_READY
  → authoritative Pair is accepted for handoff after complete capture
    materialization, or has been fully synthesized for external handoff
~~~

After L2A_COMPLETE, make the L2B completion transition explicit for every
retained cardinality:

~~~text
R = 0:
  finalRetainedNativeOrder = empty
  no L2B child state
  no L2B request
  L2B_COMPLETE = true

R = 1:
  finalRetainedNativeOrder = the sole exact retained Card
  no L2B child state
  no L2B request
  L2B_COMPLETE = true

R >= 2:
  initialize the private remaining set from the exact L2A retained complement
  ordered by canonical public order
  L2B_COMPLETE = false until the R-1 requests and derived final item complete
~~~

Keep retainedItems at original cardinality R while filtering only request
candidates. The R=0/R=1 skip transitions are required before the coordinator
can mark the parent PAIR_READY; they are not implicit merely because no L2B
child state exists.

nativeMembershipVector remains native-capture-only. NATIVE uses the
prevalidated vector to materialize expected labels. EXTERNAL does not require,
populate, or consult nativeMembershipVector; its completed external labels
become completedMembershipVector and the authoritative L2A membership vector.

- [ ] Step 3: Add typed L2B request generation

Add package-private session/provider methods with this behavior:

~~~java
DecisionRequest createRetainedTopOrderRequest(final long requestId);
void applyRetainedTopOrderCandidate(final LegalCandidate candidate);
boolean isRetainedTopOrderComplete();
List<Card> finalRetainedNativeOrder();
void markPairReady();
~~~

createRetainedTopOrderRequest must:

- return null for R=0 or R=1;
- reject a closed, stale, or already-open parent;
- expose only the current remaining retained itemIds;
- present candidates in canonical L2A order;
- emit at least two candidates and forced=false;
- use the same surveilSessionId and exact itemIds;
- set direction TOP_FIRST only in typed context.

The existing L2A request generation must become owner-aware:

~~~text
NATIVE:
  require nativeMembershipVector
  select expected L2A candidates from native capture data

EXTERNAL:
  do not require nativeMembershipVector
  generate the same typed two-candidate L2A request
  apply the chosen label directly to L2A state
  make completed external labels authoritative at L2A_COMPLETE
~~~

applyMembershipCandidate must use the same owner-aware branch. Its external
branch is the executable path for typed external L2A; it must not call the
native-vector precondition.

applyRetainedTopOrderCandidate must:

- validate the exact parent session, stage, step, and current open request;
- validate request-local candidateId and exact semantic key;
- reject stale, foreign, duplicated, or already-ordered itemIds;
- append the selected itemId to the TOP_FIRST prefix;
- remove it from the remaining set;
- derive the last remaining item internally;
- mark L2B_COMPLETE only after the complete retained order exists;
- never mark PAIR_READY.

The coordinator may call markPairReady only after all of the following:

- NATIVE: the exact native Pair is already gameplay-authoritative, complete L2A
  and L2B capture materialization has succeeded, the R=0/R=1 skip transition
  or the R>=2 retained-order transition has set L2B_COMPLETE, and no capture
  RequestHandle remains open;
- EXTERNAL: the typed L2A/L2B resolution is complete, all resolver handles are
  terminal, and the fresh Pair has passed all invariants and is fully
  constructed.

markPairReady must reject an incomplete L2A/L2B state or any still-open handle
and must be the only transition to PAIR_READY. A gameplay-authoritative native
Pair therefore does not by itself make the session PAIR_READY.

No DONE candidate and no final forced request may be created.

- [ ] Step 4: Make close/detach terminal and idempotent

Change provider.closeSession so registry removal happens first and
session.markClosed follows as non-throwing secondary bookkeeping. Repeated
close calls must be harmless. After close:

~~~text
active registry does not contain the parent
future requests are rejected
the parent cannot be reused
no RequestHandle remains owned by the parent
~~~

Do not close the parent immediately after L2A. Close only after PAIR_READY or
a terminal failure.

- [ ] Step 5: Add session handoff and cardinality tests

Test:

~~~text
same surveilSessionId from L2A context to every L2B context
same itemIds without remapping
selected owner remains immutable after admission even if provider configuration changes
EXTERNAL L2A completes without nativeMembershipVector
completed external labels become the authoritative membership vector
R=0 creates no L2B state/request
R=1 creates no L2B state/request and derives the sole item
R=2 creates one request
R=3 creates two requests
R>=3 candidates shrink R,R-1,...,2
last item has no request/result row
L2B_COMPLETE occurs before PAIR_READY
Pair-synthesis failure leaves the parent pre-PAIR_READY
only one handle is open
canonical remaining candidate order
duplicate visibleName cards remain distinct by itemId
session rejects an already-ordered itemId through its private remaining-set check
close before and after PAIR_READY is idempotent
closed parent is unroutable and unreusable
~~~

- [ ] Step 6: Run the parent-session tests

Run:

~~~text
mvn -pl forge-gui-desktop -am "-Dtest=SurveilRetainedTopOrderSessionTest,SurveilPartitionSessionTest,SurveilPartitionDecisionProviderTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
~~~

Expected: L2A behavior remains green and the parent remains available through
L2B only until terminal close.

## Task 4: Implement owner-before-admission routing and native capture

**Files:**

- Modify:
  forge-game/src/main/java/forge/game/decision/SurveilPartitionDecisionCoordinator.java
- Modify:
  forge-game/src/main/java/forge/game/decision/SurveilPartitionDiagnostics.java
- Create:
  forge-gui-desktop/src/test/java/forge/game/decision/SurveilRetainedTopOrderDecisionCoordinatorTest.java

- [ ] Step 1: Route owner before fallible admission

Refactor captureNativeSurveil around this exact sequence:

~~~text
originalTopN reference captured
if actual top-N is empty: no owner/admission/L2A/L2B
owner = provider.getOwner()
SNAPSHOT_READY → OWNER_SELECTED
attempt parent admission
~~~

For NATIVE admission failure:

~~~text
no capture parent
invoke native arrangeForSurveil exactly once with originalTopN
return exact native result
~~~

For EXTERNAL admission failure:

~~~text
native callback count = 0
no Pair
terminal fail-closed
no native fallback
~~~

Owner routing must be deterministic, side-effect-free, request-free, and
permanent for the operation.

- [ ] Step 2: Preserve exact native callback and exception semantics

Keep the native callback invocation as one direct call:

~~~java
final Pair<CardCollection, CardCollection> nativePair =
        nativeArrange.apply(originalTopN);
~~~

Do not clone, normalize, repair, reorder, or synthesize that Pair. Private
ArrayList/IdentityHashMap copies are validation-only. If the callback throws,
rethrow the exact original RuntimeException unchanged without retry.

- [ ] Step 3: Build and validate the complete native capture plan

After successful native callback completion and before any Surveil
RequestHandle:

~~~text
native callback returns exact nativePair
  → exact nativePair becomes gameplay-authoritative immediately
  → no replacement, normalization, repair, reorder, or synthesized Pair
validate complete Pair partition by exact Card identity
derive complete canonical L2A membership vector
derive exact retained complement
map every Pair.left Card to its parent itemId
validate retained cardinality
validate no duplicate/foreign/missing item
validate native Pair.left as a complete TOP_FIRST permutation
construct immutable capture plan for L2A and L2B
retain exact nativePair as the capture target
DO NOT mark PAIR_READY
only after successful complete prevalidation open the first Surveil RequestHandle
~~~

Expected validation failure before the first handle must:

~~~text
emit zero L2A rows
emit zero L2B rows
preserve the exact native Pair
do not enter PAIR_READY
close the parent through the terminal-failure path
continue native Player.surveil gameplay
~~~

The native Pair must remain untouched even when the capture plan is invalid.

- [ ] Step 4: Materialize native L2A and L2B rows sequentially

For L2A, use the existing typed
SURVEIL_PARTITION/CARD_SELECTION request and the native membership vector.
For L2B, reconstruct the native retained sequence as R-1
SURVEIL_RETAINED_TOP_ORDER/ORDER requests:

~~~text
exact native Pair is already gameplay-authoritative
  → session is not yet PAIR_READY
native Pair.left sequence
  → exact parent itemIds
  → choose next TOP_FIRST item
  → record native CHOSEN true/true
  → apply to current parent state
  → derive final remaining item
after L2A_COMPLETE:
  R=0 → derive empty final retained order; set L2B_COMPLETE=true
  R=1 → derive the sole exact retained Card; set L2B_COMPLETE=true
  R>=2 → complete the R-1 ORDER rows; derive the final item; set L2B_COMPLETE=true
after all expected capture handles are terminal and capture materialization succeeds:
  → session.markPairReady()
  → detach/close the parent
  → return the exact original nativePair
~~~

All L2B native rows use teacher eligibility NOT_APPLICABLE. The shared native
callback completion flag means the one parent callback completed; it is not a
new callback per row. The R=0/R=1 branches set L2B_COMPLETE without creating a
child state or request. PAIR_READY is set only after the final successful
capture handle is terminal; it is not set merely because the native Pair was
accepted as gameplay authority.

- [ ] Step 5: Isolate post-handle capture failures

If an unexpected native capture/instrumentation failure occurs after a handle
opens:

~~~text
already-terminal CHOSEN rows remain unchanged
mapping attempted → MAPPING_FAILED with true/true
mapping never attempted → UNOBSERVED with true/false
current handle receives at most one terminal result
no further L2A/L2B rows
exact native Pair remains authoritative
native gameplay continues
parent closes terminally
session does not enter the PAIR_READY success state
return the exact authoritative native Pair
~~~

Terminalize the current handle correctly, preserve all earlier terminal rows,
and detach/close the parent through the terminal-failure path. Do not require a
successful PAIR_READY transition after a post-handle failure. Do not let trace
or diagnostic failure replace the Pair or cause a second native callback.

- [ ] Step 6: Add native coordinator tests

Add exact tests for:

~~~text
NATIVE callback count = 1 for every non-empty admitted operation
admission failure calls native once and emits no rows
callback exception identity is rethrown unchanged
malformed Pair produces zero L2A/L2B rows before first handle
valid native Pair is the same Pair returned to Player.surveil
Pair collections are never repaired or reordered
R=0/1/2/3 native row counts follow the locked cardinality
R=0/R=1 explicitly set L2B_COMPLETE without a child state or request
PAIR_READY is never reached while a capture handle is open
successful PAIR_READY follows complete capture materialization, not callback return
post-handle failure closes terminally without requiring PAIR_READY
no extra Human prompt, AI shuffle, or RNG draw
post-handle MAPPING_FAILED true/true
post-handle UNOBSERVED true/false
registry detaches on every terminal path
~~~

- [ ] Step 7: Run the coordinator tests

Run:

~~~text
mvn -pl forge-gui-desktop -am "-Dtest=SurveilRetainedTopOrderDecisionCoordinatorTest,SurveilPartitionDecisionCoordinatorTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
~~~

Expected: native capture remains gameplay-neutral and the new L2B rows are
only emitted after complete prevalidation.

## Task 5: Implement the external L2A/L2B owner plane and Pair synthesis

**Files:**

- Modify:
  forge-game/src/main/java/forge/game/decision/SurveilPartitionDecisionCoordinator.java
- Modify:
  forge-game/src/main/java/forge/game/decision/SurveilPartitionDecisionProvider.java
- Modify:
  forge-gui-desktop/src/test/java/forge/game/decision/SurveilRetainedTopOrderDecisionCoordinatorTest.java
- Create:
  forge-gui-desktop/src/test/java/forge/game/decision/SurveilRetainedTopOrderEngineIntegrationTest.java

- [ ] Step 1: Resolve external L2A directly through typed requests

When owner is EXTERNAL:

~~~text
do not call arrangeForSurveil
admit the parent
create typed L2A CARD_SELECTION requests
open one request handle
resolver chooses one legal CLASSIFY_GRAVEYARD or CLASSIFY_RETAIN candidate
apply the candidate to the parent
record external CHOSEN false/false
derive exact retained complement
~~~

Do not create a fake native membership vector and do not mark the external
result as native capture.

If the resolver is absent, throws, returns null, or returns a stale/foreign/
duplicated/illegal candidate after the L2A handle opens, record
INVALID_EXTERNAL_CANDIDATE with empty selection and false/false on that
current handle, then close the owned operation fail-closed. Native callback
count remains zero. Resolver absence and resolver exceptions use this same
typed terminal path rather than leaving TRACE_INCOMPLETE history.

- [ ] Step 2: Resolve external L2B atomically

For R >= 2, repeatedly:

~~~text
create one typed L2B request from the current remaining retained set
resolver chooses one SELECT_NEXT_TOP candidate
validate same parent/session/profile/step/itemId
record external CHOSEN false/false
apply the choice
~~~

For R=0 or R=1, do not call the resolver for L2B. Derive the unique result
when R=1.

On an external invalid candidate, record the typed
INVALID_EXTERNAL_CANDIDATE false/false terminal result where a handle exists,
close the parent, and do not invoke native fallback.

If the resolver is absent, throws, or returns null for an already-open L2B
handle, record the same INVALID_EXTERNAL_CANDIDATE result with empty selection
and false/false. Do not leave that handle as TRACE_INCOMPLETE.

The final apply operation produces L2B_COMPLETE only. It does not produce
PAIR_READY and does not detach the parent.

- [ ] Step 3: Validate all external Pair invariants before PAIR_READY

Before constructing engine collections, assert:

~~~text
G ∪ R = exact admitted snapshot identities
G ∩ R = empty
|G| + |R| = N
Pair.left sequence contains every retained exact Card once
Pair.left sequence is semantic TOP_FIRST
Pair.right contains every graveyard exact Card once
Pair.right uses original private snapshot order filtered to G
all itemIds map to exact parent Card identities
~~~

An invariant failure reaches no Pair and remains fail-closed. No partially
constructed Pair may reach Player.surveil.

- [ ] Step 4: Construct fresh mutable external Pair collections

Construct independent mutable CardCollection instances:

~~~java
final CardCollection pairLeft = new CardCollection();
pairLeft.addAll(finalRetainedCardsInTopFirstOrder);

final CardCollection pairRight = new CardCollection();
pairRight.addAll(graveyardCardsInPrivateSnapshotOrder);

return new ImmutablePair<>(pairLeft, pairRight);
~~~

Use exact native Card instances. Do not alias the snapshot, canonical
projection list, retainedItems list, remaining set, TOP_FIRST prefix, or
session-owned mutable list. Pair must be self-contained before the parent is
detached. The complete external success sequence is:

~~~text
L2B_COMPLETE
  → validate Pair invariants
  → construct fresh mutable Pair
  → accept Pair as authoritative
  → session.markPairReady()
  → detach parent
  → hand self-contained Pair to Player.surveil
~~~

Pair synthesis failure therefore occurs before PAIR_READY and remains a
fail-closed owned failure.

- [ ] Step 5: Split cleanup before and after PAIR_READY

Before PAIR_READY:

~~~text
external cleanup/bookkeeping failure
  → fail closed
  → no Pair reaches Player.surveil
  → native callback remains zero
~~~

After PAIR_READY:

~~~text
fully synthesized Pair remains authoritative
Pair contents remain unchanged
no native fallback
engine handoff continues
registry detachment remains mandatory
secondary cleanup failure is suppressed and diagnostic-only at gameplay seam
~~~

Implement close/detach so registry removal precedes secondary bookkeeping.
Do not turn post-PAIR_READY cleanup into a new gameplay failure.

- [ ] Step 6: Add external routing and Pair tests

Test:

~~~text
EXTERNAL owner selects before admission
arrangeForSurveil call count = 0
external L2A uses typed CARD_SELECTION, no fake native vector
external L2B uses typed ORDER and exact remaining candidates
invalid/stale/duplicate/null resolver result fails closed
resolver exception fails closed
external admission failure fails closed
resolver null/exception/absence after handle open records
  INVALID_EXTERNAL_CANDIDATE with empty selection and false/false
Pair synthesis checks exact partition and cardinality
Pair.left and Pair.right are fresh mutable non-aliasing collections
Pair.right follows private snapshot filter order
L2B_COMPLETE is distinct from PAIR_READY
Pair-synthesis failure occurs before PAIR_READY
post-PAIR_READY cleanup preserves Pair and continues handoff
no external failure invokes native fallback
~~~

- [ ] Step 7: Run external coordinator and integration tests

Run:

~~~text
mvn -pl forge-gui-desktop -am "-Dtest=SurveilRetainedTopOrderDecisionCoordinatorTest,SurveilRetainedTopOrderEngineIntegrationTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
~~~

Expected: EXTERNAL is permanently zero-native-call and a valid synthesized
Pair continues through the existing Player.surveil engine path.

## Task 6: Add typed V3 profile routing and fail-closed history validation

**Files:**

- Modify:
  forge-game/src/main/java/forge/game/decision/DecisionTraceRequestRecord.java
- Modify:
  forge-game/src/main/java/forge/game/decision/DeterminismTrace.java
- Modify:
  forge-game/src/main/java/forge/game/decision/DecisionTraceTrainingValidator.java
- Create:
  forge-gui-desktop/src/test/java/forge/game/decision/SurveilRetainedTopOrderTraceTest.java
- Keep existing shared L2A trace tests unchanged as regression inputs.

- [ ] Step 1: Add the L2B V3 profile without adding a trace version

Add Profile.SURVEIL_RETAINED_TOP_ORDER to DecisionTraceRequestRecord.
Serialize and parse it through the existing V3 profile field. Do not add a
direction field and do not create DECISION_TRACE_V4.

Add exact helpers:

~~~java
public boolean isSurveilRetainedTopOrderRequest();
public boolean isSurveilRetainedTopOrderBearing();
~~~

The bearing predicate is true if ANY condition is true:

~~~text
exact SURVEIL_RETAINED_TOP_ORDER request
profile = SURVEIL_RETAINED_TOP_ORDER
adapter/stage = SURVEIL_RETAINED_TOP_ORDER
~~~

Every bearing request must enter the typed L2B validation branch. A malformed
bearing request with missing or wrong V3 metadata must fail closed and never
fall through to generic ORDER history or BC validation.

- [ ] Step 2: Make isolated L2B traces V3-authoritative

Update DeterminismTrace.decisionTraceVersion so a trace is V3 when any request
is L2B-bearing, including a trace containing only an isolated L2B request.
Preserve V2 parsing and inference for existing non-Surveil history. V2 must
never infer SURVEIL_RETAINED_TOP_ORDER from stage text.

- [ ] Step 3: Add the narrow typed validator branches

In DecisionTraceTrainingValidator, order validation as:

~~~text
if request is L2B-bearing:
    require exact V3 DecisionType.ORDER/profile/stage
    validate only the L2B truth table
    reject malformed metadata
else if exact external SURVEIL_PARTITION/CARD_SELECTION:
    apply narrow external L2A history rule
else:
    preserve existing generic validation
~~~

Implement the exact L2B result table:

~~~text
CHOSEN native:
  legal, non-forced, true/true → history-valid

CHOSEN external:
  legal, non-forced, false/false → history-valid

MAPPING_FAILED:
  empty selection, true/true → history-valid defensive terminal

UNOBSERVED:
  empty selection, true/false → history-valid defensive terminal

INVALID_EXTERNAL_CANDIDATE:
  empty selection, false/false → history-valid external terminal

TRACE_INCOMPLETE:
  existing finalization rule only

NATIVE_CALLBACK_FAILURE:
  not valid for an L2B RequestHandle
~~~

For external L2A, split the two terminal result rules:

~~~text
CHOSEN:
  DecisionType = CARD_SELECTION
  exact V3 profile/stage = SURVEIL_PARTITION
  non-forced
  selected semantic key is legal
  nativeCallbackCompleted = false
  mappingAttempted = false

INVALID_EXTERNAL_CANDIDATE:
  DecisionType = CARD_SELECTION
  exact V3 profile/stage = SURVEIL_PARTITION
  empty selected semantic key
  nativeCallbackCompleted = false
  mappingAttempted = false
~~~

The invalid-candidate branch must not require a legal selected key. Do not
broaden generic CARD_SELECTION.

For BC validation, require exact typed metadata, history-valid, CHOSEN, and
BC_ELIGIBLE; all current Surveil profiles remain NOT_APPLICABLE and therefore
produce no BC samples.

- [ ] Step 4: Add trace tests for positive, negative, and bearing cases

Cover:

~~~text
native L2B CHOSEN true/true
external L2B CHOSEN false/false
MAPPING_FAILED true/true only
UNOBSERVED true/false only
INVALID_EXTERNAL_CANDIDATE false/false only
NATIVE_CALLBACK_FAILURE rejected for L2B handle
external L2A false/false exact typed acceptance
external L2A INVALID_EXTERNAL_CANDIDATE with empty selection
external L2A resolver null/exception/absence after handle opens uses the same
  INVALID_EXTERNAL_CANDIDATE terminal path
generic CARD_SELECTION false/false still rejected
malformed L2B-bearing wrong profile rejected before generic ORDER
malformed L2B-bearing wrong stage rejected before generic ORDER
profile-only bearing rejected when exact V3 metadata is incomplete
stage-only bearing rejected when exact V3 metadata is incomplete
isolated exact L2B request selects V3
V2 does not infer either Surveil profile from stage text
all Surveil BC samples remain false
TOP_FIRST is absent from serialized V3 fields
~~~

- [ ] Step 5: Run trace and compatibility tests

Run:

~~~text
mvn -pl forge-gui-desktop -am "-Dtest=SurveilRetainedTopOrderTraceTest,SurveilPartitionTraceTest,DecisionTraceV2Test,DecisionTraceV3Test,DeterminismTraceTest,DeterminismTraceV2Test" "-Dsurefire.failIfNoSpecifiedTests=false" test
~~~

Expected: typed L2B history is fail-closed, isolated L2B is V3, and existing
L1/L1C/V2 behavior remains unchanged.

## Task 7: Verify public information, canonicalization, and deferred Pair.right scope

**Files:**

- Extend:
  forge-gui-desktop/src/test/java/forge/game/decision/SurveilRetainedTopOrderSessionTest.java
- Extend:
  forge-gui-desktop/src/test/java/forge/game/decision/SurveilRetainedTopOrderDecisionCoordinatorTest.java
- Extend:
  forge-gui-desktop/src/test/java/forge/game/decision/SurveilRetainedTopOrderTraceTest.java
- Do not modify:
  forge-game/src/main/java/forge/game/player/Player.java
  forge-gui/src/main/java/forge/player/PlayerControllerHuman.java
  forge-ai/src/main/java/forge/ai/PlayerControllerAi.java

- [ ] Step 1: Lock canonical order and itemId continuity tests

Use exact Card identities with stable identity tuples and permute only the
private input snapshot order. Assert:

~~~text
visibleName uses Java String.compareTo
then cardId
then gameTimestamp
nativeOrdinal does not affect canonical order
same exact identity set → same public order
same exact identity set → same Card-to-itemId assignment
L2A and L2B use the same itemIds
itemId numeric magnitude is never used as rank or candidate order
~~~

Keep Pair.right tests separate: changing the ordered private snapshot may
change external Pair.right compatibility order without changing public
canonical order.

- [ ] Step 2: Assert the public information boundary

Use reflection and request inspection to prove no L2B context, candidate,
semantic key, diagnostics field, or trace field contains:

~~~text
raw Card/CardView/CardLKI
cardId or gameTimestamp
nativeOrdinal
library position or zone
Pair.left or Pair.right order
AI heuristic or RNG state
itemId magnitude as a policy feature
~~~

Duplicate visibleName cards remain separate legal candidates. Their distinct
itemIds permit exact private selection but do not create observation-parity or
teacher eligibility.

- [ ] Step 3: Assert the Pair.right deferred surface

Test that:

~~~text
Pair.right has no L2B request
Pair.right has no public semantic key
Pair.right has no teacher row
external v0 filtering uses private snapshot order
SURVEIL_GRAVEYARD_INSERTION_ORDER is not represented
external L2A + L2B is not reported as full Surveil ownership
~~~

- [ ] Step 4: Run the boundary tests

Run:

~~~text
mvn -pl forge-gui-desktop -am "-Dtest=SurveilRetainedTopOrderSessionTest,SurveilRetainedTopOrderDecisionCoordinatorTest,SurveilRetainedTopOrderTraceTest,SurveilPartitionPublicApiTest,SurveilPartitionItemIdTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
~~~

Expected: public canonicalization and private engine-handoff determinism stay
separate, with no hidden engine data crossing the typed policy boundary.

## Task 8: Add the canonical fresh-JVM L2B audit

**Files:**

- Create:
  forge-gui-desktop/src/test/java/forge/view/FRL02L2BSurveilRetainedTopOrderAuditTest.java
- Reuse without changing:
  forge-gui-desktop/src/test/java/forge/view/FRL02L2ASurveilPartitionAuditTest.java
- Reuse the existing test-only fresh-process helper:
  forge-gui-desktop/src/test/java/forge/view/ChildJvmSupport.java
- Modify only the new L2B audit output schema in the new test file.

- [ ] Step 1: Launch identical audit/control child JVMs

Use ProcessBuilder through ChildJvmSupport, not a shell and not the parent test
JVM. Both children must use:

~~~text
Izzet Guild Kit vs Dimir Guild Kit
10 games
seed 20260810
same classpath
same working directory
same deterministic-random audit setting
disjoint output roots
~~~

Only the audit child receives the L2B audit output property. Retain complete
child output on timeout, non-zero exit, assertion failure, malformed artifact,
or trace mismatch.

- [ ] Step 2: Measure the L2B-specific audit schema

The audit artifact must distinguish:

~~~text
MEASURED:
  Surveil sessions = 16
  N=1 = 6
  N=2 = 10
  retained=0 = 3
  retained=1 = 2
  retained=2 = 5
  retained>=3 = 0
  actual L2B request rows = 5
  assert actualL2BRequestRows == 5

DERIVED FROM VERIFIED SOURCE:
  meaningful L2B opportunities = 5

UNPROVEN:
  external routing, unless this audit explicitly exercises configured EXTERNAL
  external Pair synthesis live in engine, unless this audit explicitly exercises it

DEFERRED:
  full Pair.right ownership
~~~

The audit must fail if actualL2BRequestRows is not exactly 5. Do not label the
expected five L2B request rows MEASURED unless the child actually emits and
counts all five.

- [ ] Step 3: Compare audit and control determinism trees

Require exact equality of the decision trace trees for equivalent logical
inputs when audit instrumentation is disabled/enabled, except for the
explicitly isolated diagnostic artifact. Assert:

~~~text
L2A public request order unchanged
L2B request sequence deterministic
candidate-set hashes deterministic
R-1 request reconstruction exact
Pair.right compatibility order follows its separate private snapshot input
~~~

- [ ] Step 4: Run the L2B audit

Run:

~~~text
mvn -pl forge-gui-desktop -am "-Dtest=FRL02L2BSurveilRetainedTopOrderAuditTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
~~~

Expected: fresh child execution completes, actual L2B request counts are
measured, and no audit property changes policy or engine behavior.

## Task 9: Run focused, decision-regression, and full Maven verification

**Files:** No new files; verification only.

- [ ] Step 1: Run the complete focused L2A/L2B suite

Run:

~~~text
mvn -pl forge-gui-desktop -am "-Dtest=SurveilPartitionItemIdTest,SurveilPartitionDecisionEnvelopeTest,SurveilPartitionSessionTest,SurveilPartitionDecisionProviderTest,SurveilPartitionDecisionCoordinatorTest,SurveilPartitionPublicApiTest,SurveilPartitionTraceTest,SurveilPartitionEngineIntegrationTest,SurveilRetainedTopOrderPublicApiTest,SurveilRetainedTopOrderDecisionEnvelopeTest,SurveilRetainedTopOrderSessionTest,SurveilRetainedTopOrderDecisionCoordinatorTest,SurveilRetainedTopOrderTraceTest,SurveilRetainedTopOrderEngineIntegrationTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
~~~

Expected: all focused L2A and L2B tests pass with zero failures and zero
errors. Any skipped test is recorded with its exact reason.

- [ ] Step 2: Run the decision regression suite

Run:

~~~text
mvn -pl forge-gui-desktop -am "-Dtest=CardSelectionDecisionProviderTest,DecisionPublicApiReflectionTest,DecisionTraceV2Test,DecisionTraceV3Test,DeterminismTraceTest,DeterminismTraceV2Test,DeterminismTraceHasherTest,SimultaneousTriggerOrderPublicApiTest,SimultaneousTriggerOrderCoordinatorTest,SimultaneousTriggerOrderTraceTest,SimultaneousTriggerOrderEngineIntegrationTest,CopySpellResolveFirstOrderPublicApiTest,CopySpellResolveFirstOrderCoordinatorTest,CopySpellResolveFirstOrderTraceTest,CopySpellResolveFirstOrderEngineIntegrationTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
~~~

Expected: generic CARD_SELECTION, L1, L1C, V2, existing V3 behavior, and the
existing Simultaneous Trigger and Copy Spell ORDER coordinator/trace/engine
contracts remain green.

- [ ] Step 3: Run the canonical L2B audit

Run the Task 8 command again after the focused suite so the reported evidence
comes from the final integrated implementation.

- [ ] Step 4: Run the full required module suite

Run:

~~~text
mvn -pl forge-gui-desktop -am test
~~~

Expected:

~~~text
0 failures
0 errors
skips documented
~~~

If an unrelated pre-existing environment or port-binding failure occurs,
retain its complete output and do not classify it as an FRL-02L2B pass.

## Task 10: Perform final source-contract and repository verification

**Files:** No new files; verification only.

- [ ] Step 1: Audit public/private information separation

Run:

~~~text
rg -n "getCardId|getGameTimestamp|CardView|CardLKI|ownerId|controllerId|ZoneType|RNG|shuffle|arrangeForSurveil" forge-game/src/main/java/forge/game/decision/SurveilRetainedTopOrder* forge-game/src/main/java/forge/game/decision/SurveilPartition* forge-game/src/main/java/forge/game/player/Player.java
~~~

Review every hit manually. Private validation and the single native callback
are allowed. Public context, candidate payload, semantic key, trace fields,
and diagnostics must not contain raw engine identity or RNG data. Existing
native AI shuffle remains in PlayerControllerAi and is not invoked by the new
coordinator.

- [ ] Step 2: Check the locked acceptance matrix

Confirm evidence for:

~~~text
canonical snapshot permutation invariance
exact comparator and nativeOrdinal exclusion
public ties and duplicate-looking retained cards
L2A → L2B itemId continuity
R=0, R=1, R=2, and R>=3
canonical remaining candidate order
exact R-1 request reconstruction
single-open-handle lifecycle
native complete prevalidation before first handle
zero native rows on expected validation failure
exact native Pair pass-through
native callback count = 1
external callback count = 0
no external fallback
typed external L2A and L2B history
fresh mutable non-aliasing external Pair
Pair invariant validation
terminal registry detachment
all current Surveil teacher labels NOT_APPLICABLE
Pair.right remains deferred
~~~

- [ ] Step 3: Verify repository scope and whitespace

Run:

~~~text
git status
$newFiles = @(
  'docs/superpowers/specs/2026-08-14-frl-02l2b-surveil-retained-top-order-design.md',
  'docs/superpowers/plans/2026-08-14-frl-02l2b-surveil-retained-top-order-implementation.md',
  'forge-game/src/main/java/forge/game/decision/SurveilPartitionOwner.java',
  'forge-game/src/main/java/forge/game/decision/SurveilRetainedTopOrderProfile.java',
  'forge-game/src/main/java/forge/game/decision/SurveilRetainedTopOrderDirection.java',
  'forge-game/src/main/java/forge/game/decision/SurveilRetainedTopOrderCandidateKind.java',
  'forge-game/src/main/java/forge/game/decision/SurveilRetainedTopOrderContext.java',
  'forge-gui-desktop/src/test/java/forge/game/decision/SurveilRetainedTopOrderPublicApiTest.java',
  'forge-gui-desktop/src/test/java/forge/game/decision/SurveilRetainedTopOrderDecisionEnvelopeTest.java',
  'forge-gui-desktop/src/test/java/forge/game/decision/SurveilRetainedTopOrderSessionTest.java',
  'forge-gui-desktop/src/test/java/forge/game/decision/SurveilRetainedTopOrderDecisionCoordinatorTest.java',
  'forge-gui-desktop/src/test/java/forge/game/decision/SurveilRetainedTopOrderTraceTest.java',
  'forge-gui-desktop/src/test/java/forge/game/decision/SurveilRetainedTopOrderEngineIntegrationTest.java',
  'forge-gui-desktop/src/test/java/forge/view/FRL02L2BSurveilRetainedTopOrderAuditTest.java'
)
git add -N -- $newFiles
git diff --check
git diff --stat
git diff
git reset -- $newFiles
~~~

The intent-to-add index entries make the new implementation, test, and the two
named FRL-02L2B documentation files visible to the diff checks without staging
their contents. The final git reset removes only those temporary index entries
and leaves the new files untracked. This check must happen before claiming
scope or whitespace cleanliness. The unrestricted git diff is intentional: it
must also show modifications to existing files such as the coordinator, session,
and validator, not only the paths listed in newFiles.

The implementation branch may contain only the intended FRL-02L2B production,
test, diagnostic, and the two named FRL-02L2B documentation artifacts. No
unrelated source or documentation change may be included. Do not commit, push,
or open a pull request as part of this plan-writing task.

## Spec coverage self-review

| Spec area | Plan coverage |
| --- | --- |
| Shared semantic seam | Tasks 3, 4, and 5 |
| Parent lifecycle and handoff | Task 3 and Task 5 |
| Stage A typed L2A | Tasks 2, 4, and 5 |
| Stage B TOP_FIRST ORDER | Tasks 1, 2, and 3 |
| Atomic action and candidate legality | Tasks 2 and 3 |
| Native exact Pair authority | Task 4 |
| External Pair synthesis | Task 5 |
| Pair.right deferred gap | Task 7 |
| Typed V3 and isolated L2B routing | Task 6 |
| History truth tables and bearing predicate | Task 6 |
| Identity/public-information boundary | Tasks 1 and 7 |
| Teacher/BC boundary | Task 6 and Task 7 |
| Public vs engine-handoff determinism | Task 7 and Task 8 |
| Canonical workload/evidence classes | Task 8 |
| Acceptance/regression gate | Tasks 8, 9, and 10 |
| Performance and non-goals | Locked invariants and task boundaries |

## Plan self-review

- No step creates a second public Surveil parent or generic continuation
  framework.
- No step adds a new DecisionType, trace version, serialized direction field,
  Pair.right policy request, or teacher label.
- No step permits EXTERNAL to fall back to NATIVE.
- No step permits native capture to repair or replace the gameplay Pair.
- No step opens a native Surveil trace handle before complete capture-plan
  prevalidation.
- No step emits an L2B request for R=0 or R=1.
- No step emits a forced final L2B request.
- No step exposes raw Card identity, native order, graveyard order, AI state, or
  RNG state through public policy payload.
- Every named type used by a later task is introduced in the file map or an
  earlier task.
- Every task has an exact file set, concrete assertions, and a verification
  command.

Plan authoring is complete when this file is saved. Implementation execution
requires a separate explicit choice of execution workflow and must follow the
sub-skill named in the header.
