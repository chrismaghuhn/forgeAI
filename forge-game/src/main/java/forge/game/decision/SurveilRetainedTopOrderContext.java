package forge.game.decision;

import java.util.List;
import java.util.Objects;

/** Immutable context for one atomic Surveil retained-top ORDER step. */
public final class SurveilRetainedTopOrderContext {
    private final SurveilRetainedTopOrderProfile profile;
    private final SurveilRetainedTopOrderDirection direction;
    private final long surveilSessionId;
    private final int decisionStepIndex;
    private final int choosingPlayerId;
    private final int retainedItemCount;
    private final List<SurveilPartitionCard> retainedItems;

    SurveilRetainedTopOrderContext(final SurveilRetainedTopOrderProfile profile,
            final SurveilRetainedTopOrderDirection direction, final long surveilSessionId,
            final int decisionStepIndex, final int choosingPlayerId, final int retainedItemCount,
            final List<SurveilPartitionCard> retainedItems) {
        this.profile = Objects.requireNonNull(profile, "profile");
        this.direction = Objects.requireNonNull(direction, "direction");
        if (decisionStepIndex < 0 || retainedItemCount < 0) {
            throw new IllegalArgumentException(
                    "decisionStepIndex and retainedItemCount must not be negative");
        }
        this.surveilSessionId = surveilSessionId;
        this.decisionStepIndex = decisionStepIndex;
        this.choosingPlayerId = choosingPlayerId;
        this.retainedItemCount = retainedItemCount;
        final List<SurveilPartitionCard> copiedItems =
                List.copyOf(Objects.requireNonNull(retainedItems, "retainedItems"));
        if (copiedItems.size() != retainedItemCount) {
            throw new IllegalArgumentException("retainedItems size must match retainedItemCount");
        }
        this.retainedItems = copiedItems;
    }

    public SurveilRetainedTopOrderProfile getProfile() {
        return profile;
    }

    public SurveilRetainedTopOrderDirection getDirection() {
        return direction;
    }

    public long getSurveilSessionId() {
        return surveilSessionId;
    }

    public int getDecisionStepIndex() {
        return decisionStepIndex;
    }

    public int getChoosingPlayerId() {
        return choosingPlayerId;
    }

    public int getRetainedItemCount() {
        return retainedItemCount;
    }

    public List<SurveilPartitionCard> getRetainedItems() {
        return retainedItems;
    }
}
