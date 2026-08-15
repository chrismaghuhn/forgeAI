# FRL-02L2B — Surveil Retained-Top ORDER Design

## Status and authority

This document is the single unified normative specification for FRL-02L2B.
It reconstructs the final design from the complete attached design
conversation, including its reviews, corrections, and seven locked section
decisions.

The attached conversation is the architecture authority for FRL-02L2B.
The existing FRL-02L2A specification, FRL-02L2A implementation plan, and
repository source are terminology and compatibility references only. They do
not override a later locked FRL-02L2B decision.

The repository checkpoint inspected before authoring this document was:

- branch: master
- HEAD: d8820120482c31458304a8862292874bbdd1bd92
- worktree: clean

This is a design specification. It authorizes no production implementation,
test implementation, commit, push, pull request, or behavior change.

## Scope

FRL-02L2B completes the retained-top ordering seam of one Surveil operation.
It owns the ordering of the retained top cards after Surveil partitioning and
hands one native-compatible Pair to the existing Player.surveil engine path.

The operation has one parent session and two typed stages:

~~~text
Parent: SURVEIL

Stage A: SURVEIL_PARTITION
  DecisionType = CARD_SELECTION
  profile      = SURVEIL_PARTITION

Stage B: SURVEIL_RETAINED_TOP_ORDER
  DecisionType = ORDER
  adapter/stage = SURVEIL_RETAINED_TOP_ORDER
  profile       = SURVEIL_RETAINED_TOP_ORDER
  direction     = TOP_FIRST in typed context/profile semantics
~~~

The high-level seam is:

~~~text
actual top-N snapshot
        ↓
shared Surveil ownership boundary
        ├─ NATIVE
        │    → arrangeForSurveil(originalTopN) exactly once
        │    → validate native Pair
        │    → capture L2A
        │    → capture L2B if retained >= 2
        │
        └─ EXTERNAL
             → arrangeForSurveil zero times
             → externally resolve L2A
             → derive retained complement
             → externally resolve L2B if retained >= 2
             → synthesize native-compatible Pair
        ↓
existing Player.surveil engine path
~~~

FRL-02L2B does not replace the existing movement, replacement, event, trigger,
or Surveil-count behavior owned by Player.surveil.

## Non-goals

FRL-02L2B does not implement or approve:

- SURVEIL_GRAVEYARD_INSERTION_ORDER or full ordering of Pair.right;
- full zero-unsupported Surveil ownership;
- Behavior Cloning eligibility or observation-parity approval;
- RandomLegalPolicy integration unless separately scoped and approved;
- a generic ORDER, permutation, or continuation framework;
- a new DecisionType or a new decision-trace version;
- PPO, RL, or other downstream policy training;
- a second composite Surveil callback, second JVM, full game-state copy, or
  parallel full ORDER continuation materialization.

The external Pair.right rule in this milestone is a deterministic compatibility
fallback, not a policy feature and not a declaration of complete Surveil
ownership.

## Existing engine boundary

The existing engine path is the authority for the gameplay handoff:

1. SurveilEffect and its modifiers reach Player.surveil.
2. Player.surveil obtains the actual top-N cards from the library.
3. For a non-empty top-N snapshot, the shared Surveil boundary receives the
   original mutable top-N collection and the native callback
   PlayerController.arrangeForSurveil.
4. The boundary captures or resolves the two typed stages and produces a
   Pair.
5. Player.surveil consumes Pair.right for graveyard movement and reverses
   Pair.left before putting retained cards back on top.
6. Player.surveil continues to fire the existing Surveil event and trigger
   path.

The native callback implementations remain:

- PlayerController.arrangeForSurveil as the native seam;
- PlayerControllerHuman.arrangeForSurveil for Human interaction;
- PlayerControllerAi.arrangeForSurveil for AI classification and native
  retained-top shuffling.

The existing L2A authority types are reused: SurveilPartitionCard,
SurveilPartitionContext, SurveilPartitionItemId, SurveilPartitionSession,
SurveilPartitionDecisionProvider, and SurveilPartitionDecisionCoordinator. The
existing decision model and trace model are extended only with the narrow
typed L2B surface specified here.

DecisionRequest and LegalCandidate remain the typed request and candidate
boundary. FRL-02L2B adds narrow Surveil-retained-top semantics at that
boundary; it does not generalize a boolean callback or broaden unrelated
CARD_SELECTION and ORDER requests.

## Semantic ownership and shared seam

The shared boundary makes the owner decision once for the admitted non-empty
snapshot. It is the semantic owner of the complete two-stage operation, not an
isolated ORDER widget.

Owner selection has the following order:

~~~text
SNAPSHOT_READY
    ↓
OWNER_SELECTED
    ↓
fallible parent admission
    ↓
owner-specific L2A and, when needed, L2B
~~~

OWNER_SELECTED must occur before fallible parent admission. Owner selection is
deterministic for the same configured ownership and has no gameplay effect, no
request side effect, no policy call, and no native callback side effect.

Once the owner is selected:

- NATIVE may never become EXTERNAL.
- EXTERNAL may never fall back to NATIVE.
- An owner-specific failure remains in that owner’s failure plane.
- Cleanup cannot change the owner or resurrect a failed session.

The boundary preserves the distinction between:

- L2A membership: which snapshot cards are classified to graveyard or
  retained top;
- L2B order: the TOP_FIRST order of retained cards in Pair.left;
- Pair.right order: a separate graveyard insertion-order decision surface,
  deferred by this milestone.

Therefore, external L2A plus external L2B is not full zero-unsupported Surveil
ownership. Pair.right remains a compatibility fallback until a later
SURVEIL_GRAVEYARD_INSERTION_ORDER stage is separately designed and approved.

## Parent-session lifecycle

Every admitted non-empty operation has one parent session:

~~~text
parent = SURVEIL
surveilSessionId = one opaque parent-session identifier
itemIds = one immutable parent-session mapping
stages = SURVEIL_PARTITION → optional SURVEIL_RETAINED_TOP_ORDER
~~~

The same surveilSessionId and the same itemIds are used by L2A and L2B. The
L2A item identity is handed into L2B unchanged. L2B must not create a second
public parent session or remap L2A items.

A private L2B child state may hold only ordering progress, such as the current
remaining retained set, the next decision step, and the accumulated
TOP_FIRST prefix. It does not become a second public parent.

The parent owns, or immutably captures, the following:

- the opaque session identifier;
- the choosing player and game authority;
- the immutable private original top-N snapshot;
- the private identity mapping from exact Card instances to Surveil items;
- the canonical public item order;
- the parent-session-local itemId assignment;
- the selected owner plane;
- the L2A membership vector once completed;
- the retained complement and private native-order data;
- the final L2B order once completed.

The parent remains routable while an L2A or L2B RequestHandle is open and while
Pair synthesis is pending. At PAIR_READY, or at any terminal failure, the
parent is detached and closed.

### Empty snapshots

If the actual top-N snapshot is empty:

- no owner is selected;
- no parent is admitted;
- no L2A request is emitted;
- no L2B state or request exists;
- the existing Player.surveil behavior continues unchanged.

There is no empty-session placeholder.

### Admission and handoff

For a non-empty snapshot, parent admission occurs after OWNER_SELECTED and
before owner-specific resolution. Admission failure is terminal in the
selected owner plane.

The parent must not be detached between L2A and L2B. The transition to L2B
uses the exact retained itemIds from the completed L2A parent state. At
PAIR_READY the Pair is self-contained; it must not need the parent session,
an open RequestHandle, or a resolver to be consumed by Player.surveil.

## Owner routing

### NATIVE

NATIVE means that native gameflow remains authoritative. On a non-empty
snapshot:

- the original mutable top-N collection is passed to
  arrangeForSurveil exactly once;
- if parent admission fails, no capture parent exists and exactly one native
  callback still occurs;
- successful callback completion is followed by complete capture-plan
  prevalidation;
- only successful complete prevalidation may open the first Surveil trace
  handle;
- L2A is captured from native Pair membership;
- L2B is captured from native Pair.left order when retained cards number at
  least two.

The native callback is never retried. If it throws, the exact original
exception is rethrown unchanged. No Pair is synthesized and no second native
callback is attempted.

### EXTERNAL

EXTERNAL means that the external resolver owns the approved L2A and L2B
surfaces:

- arrangeForSurveil is called zero times;
- L2A is resolved directly with typed
  SURVEIL_PARTITION/CARD_SELECTION semantics;
- the retained complement is derived after L2A;
- L2B is resolved externally if retained cards number at least two;
- a native-compatible Pair is synthesized only after complete invariant
  validation.

External ownership is permanent for the parent. Admission failure, resolver
failure, invalid or stale candidates, Pair synthesis failure, and cleanup
issues never cause a native callback or native fallback. Owned failure is
fail-closed.

## Stage A — SURVEIL_PARTITION

Stage A classifies each canonical public item as either graveyard or retained
top. It reuses the existing L2A semantic contract:

~~~text
DecisionType = CARD_SELECTION
profile = SURVEIL_PARTITION
adapter/stage = SURVEIL_PARTITION
candidate semantics = CLASSIFY_GRAVEYARD or CLASSIFY_RETAIN
~~~

The canonical public projection uses SurveilPartitionCard exactly. Its public
payload is only:

~~~text
itemId
visibleName
~~~

Native L2A derives the membership vector from the exact native Pair after
complete validation. External L2A directly drives the typed
CARD_SELECTION semantics. It must not pre-seed or fabricate a native
membership vector and must not pretend that an external decision is a native
callback result.

The completed L2A labels are the authoritative membership vector for the
parent. The retained complement is computed from the exact admitted snapshot
identities, not from visible-name matching.

L2A candidate presentation is canonical public order. Native input order,
native AI shuffle order, Pair.left order, and Pair.right order are not public
L2A candidate order.

## Stage B — SURVEIL_RETAINED_TOP_ORDER

Stage B orders only the retained complement from L2A. It uses the exact typed
ORDER identity:

~~~text
DecisionType = ORDER
adapter/stage = SURVEIL_RETAINED_TOP_ORDER
profile = SURVEIL_RETAINED_TOP_ORDER
semantic direction = TOP_FIRST
~~~

TOP_FIRST is profile/context semantics. It is not a new serialized trace
field.

Let R be the number of retained items after L2A:

| Retained count | L2B state | L2B requests | Result |
| --- | --- | --- | --- |
| R = 0 | none | none | Pair.left is empty |
| R = 1 | none | none | the only retained item is derived |
| R >= 2 | private ordering child state | exactly R - 1 | the final item is derived |

Each L2B action chooses the next TOP_FIRST item from the current remaining
retained set. After each choice, that item is removed from the remaining set
and appended to the TOP_FIRST prefix. When two items remain, one request
chooses the first of those two; the last item is derived internally.

L2B therefore has:

- no DONE candidate;
- no whole-permutation candidate;
- no pairwise ordering protocol;
- no artificial forced request for the final item;
- no second composite Surveil callback.

The L2B context and candidate set continue the parent session:

- the same surveilSessionId is used for opaque correlation;
- the same parent-session-local itemIds are used;
- retainedItems are presented in canonical public order;
- the current remaining candidate set is the legal domain;
- each request has a request-local candidateId range
  0 through remainingCount minus 1;
- candidateId is not an itemId and has no cross-request identity.

The native L2B order is captured from the validated Pair.left sequence. The
external L2B order is the result of the typed external ORDER requests.

## Atomic ORDER protocol

Every L2B request is an atomic action over one current remaining set:

1. expose the remaining retained candidates in canonical presentation order;
2. accept one legal candidate from that exact set;
3. append its parent itemId to the TOP_FIRST prefix;
4. remove it from the remaining set;
5. advance the stage step;
6. keep the terminal last item derived rather than requested.

At most one RequestHandle may be open for the parent. Requests are strictly
serialized after L2A. A stale, foreign, duplicated, or otherwise illegal
candidate cannot mutate the session and is terminal in the external owner
plane.

Candidate order is presentation and identity plumbing, not semantic strength,
rank, or position. A canonical candidate index does not authorize a caller to
interpret its numeric value.

## Candidate and context contract

The public L2B context is intentionally narrow. It may expose:

- the typed profile and stage;
- the opaque surveilSessionId;
- decisionStepIndex;
- choosingPlayerId;
- retainedItemCount;
- the canonical retainedItems projection;
- the current remaining candidate set.

It must not expose raw Card, CardView, CardLKI, cardId, gameTimestamp,
snapshot ordinal, library position, zone state, native Pair collections,
native order, graveyard insertion order, AI heuristic data, or RNG state.

The legal candidate domain is supplied by the callback/request context. AI
heuristics must not enlarge, shrink, or replace that domain.

L2A uses the existing typed two-label candidate semantics. L2B uses the
typed retained-top selection action semantics. Neither stage is implemented by
turning a generic boolean callback into a Surveil protocol.

## Identity and public-information boundary

FRL-02L2B preserves three distinct identity planes:

### Public policy identity

The public policy sees SurveilPartitionCard projections and opaque itemIds.
The visibleName is presentation data, not exact engine identity.

### Private session identity

The parent session maps each itemId to the exact admitted Card identity and
retains the immutable private snapshot and membership/order state needed for
the handoff.

### Engine identity

The engine continues to use exact Card objects and engine identity such as
cardId, gameTimestamp, zone state, and collection ownership.

Only the approved public projection crosses the policy boundary. Routing and
trace metadata are not card payload.

An itemId is:

- parent-session-local;
- opaque;
- allocated and owned by the SURVEIL parent;
- stage-stable across L2A and L2B;
- unchanged during L2A-to-L2B handoff.

ItemId may be used for equality, candidate identity, action selection, and
L2A-to-L2B correlation. It must not be used as a ranking, sorting key,
distance, scalar magnitude, position, strength, embedding, or cross-session
identity.

A request-local candidateId is distinct from a parent-session-local itemId.
CandidateId is valid only in its request presentation and must never be
persisted as the Surveil identity.

Cards with equal visibleName remain distinct legal candidates when their
itemIds and exact private identities differ. A duplicate-looking public pair
does not authorize visible-name collapse and does not prove observation parity.

## Native capture mode

Native capture receives the original top-N collection and calls the native
callback exactly once when the snapshot is non-empty. It must not create an
extra Human prompt, an extra AI shuffle, or any extra native RNG draw.

The successful native callback result is gameplay-authoritative. Capture must
not replace, clone, normalize, repair, reorder, or synthesize a replacement
native Pair. Private normalized copies may be used for validation only. The
exact original Pair remains the Pair that native gameflow consumes, even when
capture validation fails.

Before the first Surveil trace handle is opened, native capture must
prevalidate the complete capture plan:

- Pair.left and Pair.right contain exact identities from the admitted
  snapshot;
- every snapshot identity occurs in exactly one side;
- the side union equals the snapshot and the sides are disjoint;
- the graveyard membership vector is complete;
- the complete Pair.left sequence maps to the parent itemIds;
- the retained cardinality and all item identities are consistent;
- the native retained sequence is a valid TOP_FIRST permutation;
- the L2A plan and, when R >= 2, the L2B plan are internally consistent.

If this expected native validation or mapping fails before the first Surveil
trace handle, capture emits zero L2A rows and zero L2B rows. The exact native
Pair remains authoritative and native gameflow continues.

If the callback itself throws, the exact original exception is rethrown
unchanged. That is not a captured L2B request and does not produce a
synthesized Pair, retry, or substituted result.

If an unexpected mapping or instrumentation failure occurs after a handle
has already been opened, the defensive trace terminalization rules in the
typed trace contract apply. Previously finalized terminal rows are immutable,
and no second callback or replacement Pair is allowed.

## External ownership mode

External mode never invokes arrangeForSurveil. It resolves L2A directly as a
typed external SURVEIL_PARTITION/CARD_SELECTION operation, records the
completed external membership labels, and derives the retained complement
from exact private identities.

If R >= 2, external mode opens the typed L2B ORDER requests one at a time.
Each request uses the exact current remaining retained set. The final
retained item is derived without a request.

External failures before PAIR_READY are terminal and fail-closed:

- parent admission failure;
- resolver exception;
- invalid, stale, foreign, or duplicated candidate;
- inconsistent external history;
- Pair invariant failure;
- Pair construction failure;
- cleanup or bookkeeping failure before PAIR_READY.

For an external cleanup or bookkeeping failure after PAIR_READY:

- the fully synthesized Pair remains authoritative;
- Pair contents remain unchanged;
- no native fallback occurs;
- engine handoff continues;
- active-registry detachment remains mandatory;
- the secondary cleanup failure is suppressed and diagnostic-only at the
  gameplay seam.

None of these failure paths may invoke the native callback or fall back to
native gameflow after EXTERNAL was selected.

## Native Pair authority

Native Pair authority is deliberately asymmetric with external synthesis:

- the successful native callback result is the gameplay result;
- capture validates privately and records only valid observations;
- capture never mutates or repairs the native Pair;
- the exact Pair remains authoritative on expected capture-validation
  failure;
- native gameflow proceeds with that exact Pair.

This rule prevents trace capture from becoming a second gameplay authority.

## External Pair synthesis

External Pair construction is allowed only after complete invariant
validation. Let G be the exact graveyard identity set and R be the exact
retained identity set. The synthesized Pair must satisfy:

~~~text
G ∪ R = exact admitted snapshot identities
G ∩ R = empty
|G| + |R| = N

Pair.left
  = fresh mutable CardCollection
  = exact retained Card identities
  = semantic TOP_FIRST order

Pair.right
  = fresh mutable CardCollection
  = exact graveyard Card identities
  = private original-snapshot filtered order
~~~

The two engine-consumed collections must not alias:

- the private original snapshot collection;
- canonical public projection lists;
- the retainedItems or remaining-candidate lists;
- a private ordering prefix or session-owned mutable collection;
- any other authoritative session-owned collection.

This non-aliasing requirement is mandatory because Player.surveil mutates the
engine handoff, including reversing Pair.left before library insertion.

After PAIR_READY, the Pair is self-contained. The parent may be detached and
closed; Pair consumption must not require an open handle, active registry
entry, resolver, or mutable session list.

## Pair.right compatibility fallback

Native Human Pair.right order is a genuine graveyard insertion-order decision
surface. The Human flow chooses a graveyard subset and its insertion order
through a native interaction. L2A owns the graveyard membership, but neither
L2A nor L2B owns that order.

Native AI Pair.right order is native AI behavior and is not a teacher label.
External v0 uses the only approved compatibility fallback:

~~~text
Pair.right =
the private original top-N snapshot order
filtered to exact CLASSIFY_GRAVEYARD members
~~~

This fallback is private, deterministic, and compatibility-only. It is not a
DecisionRequest, not a policy feature, and not a teacher label. It must not be
presented as full zero-unsupported Surveil ownership.

Full ownership requires a later separate
SURVEIL_GRAVEYARD_INSERTION_ORDER stage. That deferred stage is outside
FRL-02L2B.

## Failure planes

The following failure routing is normative:

| Condition | Native owner | External owner |
| --- | --- | --- |
| empty actual top-N | no routing, existing Player.surveil behavior | no routing, existing Player.surveil behavior |
| parent admission failure | callback exactly once; native gameflow authoritative | terminal fail-closed; callback zero |
| callback throws | rethrow exact original exception; no retry or synthesized Pair | not applicable; callback is never invoked |
| expected native pre-handle validation failure | zero L2A/L2B rows; exact Pair preserved | not applicable |
| resolver or candidate failure | not applicable | terminal fail-closed; no native fallback |
| Pair synthesis/invariant failure | not applicable | terminal fail-closed; no native fallback |
| cleanup/bookkeeping failure before PAIR_READY | no fallback and no Pair mutation | terminal fail-closed; no Pair reaches Player.surveil; callback zero |
| cleanup/bookkeeping failure after PAIR_READY | native Pair remains authoritative; no Pair mutation | synthesized Pair remains authoritative and unchanged; no native fallback; engine handoff continues; detachment mandatory; secondary failure suppressed/diagnostic-only |

Owner selection is not retried after a failure. Failure cleanup must not
change Pair contents, reopen a request, resurrect the parent, or create a
second owner path.

## Session cleanup

Every admitted parent must eventually:

- detach from the active registry;
- become unroutable;
- become unreusable;
- own no open RequestHandle.

Terminal detach/close is idempotent and non-throwing at the gameplay seam.
Registry detachment occurs before secondary bookkeeping so that a terminal
parent cannot be routed while cleanup is still recording diagnostics.

Cleanup is required after successful Pair handoff and after every terminal
failure. Cleanup must not invoke arrangeForSurveil, synthesize a new Pair,
change either Pair collection, or resurrect ownership.

## Trace V3 contract

FRL-02L2B extends the existing typed V3 trace surface narrowly. The contract
is carried by DecisionTraceRequestRecord, DecisionTraceResultRecord,
DecisionTraceTrainingValidator, and DeterminismTrace:

| Stage | DecisionType | profile | adapter/stage |
| --- | --- | --- | --- |
| L2A | CARD_SELECTION | SURVEIL_PARTITION | SURVEIL_PARTITION |
| L2B | ORDER | SURVEIL_RETAINED_TOP_ORDER | SURVEIL_RETAINED_TOP_ORDER |

TOP_FIRST is implied by the typed L2B profile/context. It is not serialized as
a new direction field in Decision Trace V3. No new trace version is created.

The V3 router must treat an isolated L2B-bearing request with the exact typed
L2B metadata as V3-authoritative. A trace is V3 whenever it contains L2B,
even if the request is inspected without a preceding L2A row.

V2 must never infer either Surveil profile from stage text. V2 records cannot
be upgraded by guessing a profile or direction from an adapter string.

Trace rows preserve the existing atomic request/result relationship. A
derived final retained item has no artificial decision row. Each L2B request
is non-forced. Terminal native, external, and defensive result states are
typed as specified below.

## History validation

History validation is fail-closed and typed. These Surveil exceptions are
narrow additions; they must not broaden unrelated CARD_SELECTION or ORDER
validation.

### External L2A history

An external L2A CHOSEN result is history-valid only when all of the following
are exact:

~~~text
DecisionType = CARD_SELECTION
profile = SURVEIL_PARTITION
adapter/stage = SURVEIL_PARTITION
trace version = V3
forced = false
selected semantic key is legal for that request
nativeCallbackCompleted = false
mappingAttempted = false
~~~

This is a typed SURVEIL_PARTITION exception. Generic CARD_SELECTION history
must not be changed globally.

An external L2A INVALID_EXTERNAL_CANDIDATE result is valid only for the exact
approved typed SURVEIL_PARTITION/CARD_SELECTION history contract. It must not
be accepted merely because a request has generic CARD_SELECTION type.

### Typed L2B branch

A request is SURVEIL_RETAINED_TOP_ORDER-bearing if any of the following is
true:

- it is an exact SURVEIL_RETAINED_TOP_ORDER request;
- profile = SURVEIL_RETAINED_TOP_ORDER;
- adapter/stage = SURVEIL_RETAINED_TOP_ORDER.

Every bearing request enters the typed L2B validation branch before generic
ORDER handling. If exact typed V3 L2B metadata is not satisfied, validation
fails closed. A malformed L2B-bearing request must never fall through to
generic ORDER history or BC validation.

The L2B validator branch runs before generic ORDER fallback and requires the
exact V3 typed L2B metadata. Generic ORDER validation is not allowed to
bypass it.

| L2B result | selected value | nativeCallbackCompleted | mappingAttempted | history result |
| --- | --- | --- | --- | --- |
| CHOSEN, native | legal item | true | true | valid when non-forced and typed |
| CHOSEN, external | legal item | false | false | valid when non-forced and typed |
| MAPPING_FAILED, defensive | empty | true | true | valid defensive terminal state |
| UNOBSERVED, defensive | empty | true | false | valid defensive terminal state |
| INVALID_EXTERNAL_CANDIDATE | empty | false | false | valid external terminal state |
| TRACE_INCOMPLETE | according to existing finalization rule | according to finalized state | according to finalized state | valid only when correctly finalized |
| NATIVE_CALLBACK_FAILURE | none | not applicable | not applicable | not valid for an L2B RequestHandle |

NATIVE_CALLBACK_FAILURE cannot be an L2B RequestHandle result because a native
callback exception occurs before L2B handle creation.

For defensive failures after a handle exists, previously recorded terminal
rows remain immutable. The current handle receives at most one terminal
defensive result, and no later request is opened.

### Teacher validation

Typed BC validation first requires exact typed metadata and a valid typed
history result. It then requires CHOSEN and BC_ELIGIBLE. All current Surveil
profiles are NOT_APPLICABLE, so no current Surveil row is BC-eligible.

The validator must fail closed for Surveil profiles until a later separately
approved milestone changes the eligibility contract.

## Teacher / BC contract

Every current Surveil source is explicitly not a native teacher source:

| Source | Stage | Teacher label |
| --- | --- | --- |
| Native Human | L2A | NOT_APPLICABLE |
| Native Human | L2B | NOT_APPLICABLE |
| Native AI | L2A | NOT_APPLICABLE |
| Native AI | L2B | NOT_APPLICABLE |
| External | L2A | NOT_APPLICABLE |
| External | L2B | NOT_APPLICABLE |

Native Human final outcomes may be reconstructable from a final Pair, but that
does not prove observation parity or preserve the original click history.
Native AI retained order is shuffle/RNG-derived and is not strategic teacher
behavior. External history may later support RL, self-play, or trajectory
analysis, but it is not a native teacher label.

Pair.right compatibility order produces no public candidate, semantic key,
trace request, or teacher row.

## Determinism and canonicalization

FRL-02L2B has two intentionally distinct determinism contracts.

### Public/policy determinism

For the same exact admitted Card identity set and the same stable identity
data, any permutation of the input snapshot must produce:

- the same canonical public order;
- the same Card-to-itemId assignment;
- the same L2A candidate presentation;
- the same L2B retainedItems presentation;
- the same L2B remaining-candidate presentation;
- the same semantic L2B result for the same action sequence.

The exact canonical comparator is:

~~~text
visibleName using Java String.compareTo
→ cardId
→ gameTimestamp
~~~

nativeOrdinal is private provenance only and does not participate in this
comparator. The private tie-break fields are not public policy payload.

Public canonical order determines the SurveilPartitionCard projection list,
itemId assignment, L2A presentation, L2B retainedItems presentation, and
each remaining candidate presentation.

It must not be confused with native snapshot order, Pair.left order,
Pair.right order, AI shuffle order, or semantic strength/rank.

### Engine-handoff determinism

External Pair.right intentionally depends on the ordered private original
top-N snapshot. Therefore, changing the snapshot order may change the
compatibility Pair.right order without changing public policy canonicalization
or itemId assignment.

This is intentional. Public canonicalization and Pair.right snapshot ordering
are separate determinism contracts, not contradictory definitions.

For a fixed ordered private snapshot, fixed external membership, and fixed
L2B action sequence, external Pair.left and Pair.right are deterministic.

## Acceptance matrix

The implementation may be accepted only when evidence covers all of the
following:

| Area | Required acceptance |
| --- | --- |
| canonicalization | arbitrary snapshot permutation invariance; exact Java String.compareTo comparator; cardId and gameTimestamp tie-breaks; nativeOrdinal excluded |
| public identity | public ties remain distinct; duplicate-looking retained cards remain distinct; itemId is opaque, parent-local, and stage-stable |
| handoff identity | exact L2A itemIds continue into L2B with no remap; candidateId remains request-local |
| cardinality | R=0 emits no L2B state/request; R=1 emits no L2B state/request and derives order; R=2 emits one request; R>=3 emits exactly R-1 requests |
| protocol | TOP_FIRST chooses from the current remaining set; last item is derived; no DONE, whole permutation, pairwise protocol, or forced final request |
| candidate presentation | L2B retainedItems and every remaining candidate set are canonical and legally scoped |
| request reconstruction | R-1 request rows reconstruct the exact retained order; no row exists for the derived final item |
| lifecycle | at most one RequestHandle is open; L2A precedes L2B; terminal rows are immutable; parent detaches and becomes unroutable |
| native capture | complete capture-plan prevalidation occurs before the first Surveil handle; expected validation failure emits zero L2A/L2B rows; exact native Pair passes through |
| native callback | callback count is exactly one for non-empty NATIVE operations; callback exceptions are rethrown unchanged; no retry, extra prompt, shuffle, or RNG draw |
| external routing | callback count is zero; EXTERNAL never falls back to NATIVE after any owned failure |
| external history | exact typed external SURVEIL_PARTITION history is accepted without broadening generic CARD_SELECTION |
| L2B history | exact typed L2B truth table is enforced before generic ORDER fallback; isolated L2B-bearing requests are V3-authoritative |
| Pair construction | external Pair invariants are fully validated; Pair.left and Pair.right are fresh mutable collections with exact Card identities and no authoritative aliasing |
| Pair authority | native Pair is never repaired, cloned, normalized, reordered, or replaced |
| cleanup | terminal registry detachment is idempotent, non-throwing at the gameplay seam, and occurs before secondary bookkeeping |
| teacher boundary | every current Surveil source remains NOT_APPLICABLE; typed BC validation fails closed |
| deferred gap | Pair.right remains a compatibility fallback; full graveyard insertion-order ownership is not claimed |

The overall regression gate is:

~~~text
Focused L2A + L2B tests       PASS
Decision regression suite     PASS
L1/L1C audits                 PASS
L2A canonical audit           PASS
L2B canonical audit           PASS
Full Maven suite              0 failures, 0 errors
Skipped tests                 documented
git diff --check               PASS
~~~

## Canonical runtime workload

The initial canonical workload for evidence is:

~~~text
fresh JVM
Izzet Guild Kit vs Dimir Guild Kit
10 games
seed 20260810
~~~

The previously established baseline is:

| Observation | Evidence class | Value |
| --- | --- | --- |
| Surveil sessions | MEASURED | 16 |
| N=1 | MEASURED | 6 |
| N=2 | MEASURED | 10 |
| retained=0 | MEASURED | 3 |
| retained=1 | MEASURED | 2 |
| retained=2 | MEASURED | 5 |
| retained>=3 | MEASURED | 0 |
| meaningful L2B opportunities | DERIVED FROM VERIFIED SOURCE | 5 |
| actual L2B request rows | UNPROVEN | expected value if implementation conforms: 5 |
| external routing | UNPROVEN | not established |
| external Pair synthesis live in engine | UNPROVEN | not established |
| full Pair.right ownership | DEFERRED | requires a later graveyard-order stage |

The expected value of five L2B request rows is not a measured runtime result.
No implementation claim may promote it to MEASURED.

## Evidence classification

Only these evidence classes are normative for this design:

- MEASURED: directly observed in the canonical workload or a specified
  verification run;
- DERIVED FROM VERIFIED SOURCE: logically derived from repository behavior
  that was read and verified;
- UNPROVEN: required but not yet demonstrated by runtime evidence;
- DEFERRED: intentionally outside this milestone and assigned to later
  design work.

The specification must not use an unproven external path, an inferred
teacher label, or the Pair.right fallback as evidence of full Surveil
ownership.

## Performance and scope constraints

For retained count R, L2B emits R - 1 requests with candidate sizes:

~~~text
R, R-1, ..., 2
~~~

The total number of candidate occurrences is O(R²). The design must not
enumerate N! whole permutations.

The milestone rejects:

- a second composite Surveil callback;
- a generic continuation or permutation engine;
- a second JVM;
- a full game-state copy;
- parallel materialization of every ORDER continuation;
- an extra native Human prompt;
- an extra native AI shuffle or RNG draw;
- a generic framework change made solely to host this one typed profile.

The narrow typed seam is preferred over a generic engine abstraction.

## Deferred decision surfaces

The following boundary is explicit:

~~~text
L2A + L2B external ownership
    ≠
full zero-unsupported Surveil ownership
~~~

Pair.right graveyard insertion order is a genuine Human decision surface.
FRL-02L2B does not resolve, expose, trace, train, or claim ownership of it.
The external-v0 original-snapshot-filtered Pair.right rule is only the
compatibility fallback.

A future SURVEIL_GRAVEYARD_INSERTION_ORDER stage may separately define that
surface, its observation boundary, its trace contract, and its teacher
eligibility. That future work must not be silently folded into FRL-02L2B.

## Locked decisions

The following decisions are final and normative:

1. SECTION_1_SEMANTIC_OWNERSHIP_SHARED_SEAM_LOCKED: one shared Surveil
   ownership boundary owns L2A and L2B; external ownership never falls back to
   native.
2. SECTION_2_PARENT_SESSION_LIFECYCLE_HANDOFF_LOCKED: OWNER_SELECTED occurs
   before fallible parent admission; one parent session and one stable itemId
   mapping span both stages and terminate at Pair handoff or terminal failure.
3. SECTION_3_STAGE_CONTRACTS_TOP_FIRST_ORDER_LOCKED: L2B is typed
   SURVEIL_RETAINED_TOP_ORDER with TOP_FIRST semantics, R-1 atomic requests,
   and a derived final item.
4. SECTION_4_ATOMIC_ACTION_AND_TRACE_CONTRACT_LOCKED: L2A remains typed
   CARD_SELECTION, L2B is typed ORDER, V3 is required, and typed history
   validation is fail-closed and narrow.
5. SECTION_5_FAILURE_PAIR_SYNTHESIS_OWNER_ROUTING_LOCKED: native Pair
   authority is exact pass-through; external Pair construction is validated,
   fresh, mutable, and non-aliasing; callback counts are one native and zero
   external.
6. SECTION_6_PUBLIC_INFORMATION_IDENTITY_TEACHER_BOUNDARY_LOCKED:
   SurveilPartitionCard is reused, itemId is parent-session-local and
   stage-stable, raw engine information stays private, and all current
   Surveil teacher labels are NOT_APPLICABLE.
7. SECTION_7_DETERMINISM_CANONICALIZATION_ACCEPTANCE_EVIDENCE_LOCKED:
   public canonicalization is distinct from Pair.right snapshot ordering,
   TOP_FIRST is not a serialized direction field, and the exact evidence and
   acceptance gates above are required.

No earlier draft, superseded wording, or generic simplification may override
these locked decisions.
