package forge.game.decision;

import forge.game.card.Card;
import forge.game.player.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class SurveilPartitionDecisionProvider {
    private long nextSurveilSessionId = 1L;
    private long nextRequestId = 1L;
    private final Map<Long, SurveilPartitionSession> activeSessions = new HashMap<>();
    private SurveilPartitionOwner owner = SurveilPartitionOwner.NATIVE;
    private Resolver resolver;

    @FunctionalInterface
    public interface Resolver {
        LegalCandidate choose(DecisionRequest request);
    }

    public SurveilPartitionDecisionProvider() {
    }

    public synchronized void setOwner(final SurveilPartitionOwner owner0) {
        owner = Objects.requireNonNull(owner0, "owner");
    }

    public synchronized SurveilPartitionOwner getOwner() {
        return owner;
    }

    public synchronized void setResolver(final Resolver resolver0) {
        resolver = resolver0;
    }

    public synchronized Resolver getResolver() {
        return resolver;
    }

    public synchronized boolean hasResolver() {
        return resolver != null;
    }

    synchronized long nextSurveilSessionId() {
        return nextSurveilSessionId++;
    }

    synchronized long nextRequestId() {
        return nextRequestId++;
    }

    synchronized SurveilPartitionSession admit(final Player chooser, final List<Card> privateSnapshot) {
        return admit(chooser, privateSnapshot, owner);
    }

    synchronized SurveilPartitionSession admit(final Player chooser, final List<Card> privateSnapshot,
            final SurveilPartitionOwner selectedOwner) {
        Objects.requireNonNull(selectedOwner, "selectedOwner");
        if (privateSnapshot == null) {
            throw new SurveilPartitionAdmissionFailure(
                    SurveilPartitionAdmissionFailureReason.UNSUPPORTED_ADMISSION,
                    "private snapshot authority is unavailable");
        }
        final List<Card> immutableSnapshot = Collections.unmodifiableList(new ArrayList<>(privateSnapshot));
        final SurveilPartitionSession session = new SurveilPartitionSession(nextSurveilSessionId(), chooser,
                immutableSnapshot, selectedOwner);
        if (!session.isComplete()) {
            activeSessions.put(session.surveilSessionId(), session);
        }
        return session;
    }

    synchronized DecisionRequest createMembershipRequest(final SurveilPartitionSession session) {
        Objects.requireNonNull(session, "session");
        if (session.isClosed()) {
            throw new IllegalStateException("Surveil session is stale or not registered");
        }
        if (session.isEmptySnapshot() && session.isComplete()) {
            return null;
        }
        requireRegistered(session);
        if (!session.isIdentityStable()) {
            throw new IllegalStateException("Surveil session identity is stale");
        }
        if (session.isComplete()) {
            throw new IllegalStateException("Surveil session is complete");
        }
        if (session.hasOpenRequest()) {
            throw new IllegalStateException("Surveil session already has an open request");
        }
        return session.createMembershipRequest(nextRequestId());
    }

    synchronized DecisionRequest createRetainedTopOrderRequest(final SurveilPartitionSession session) {
        Objects.requireNonNull(session, "session");
        requireRegistered(session);
        if (!session.isIdentityStable()) {
            throw new IllegalStateException("Surveil session identity is stale");
        }
        return session.createRetainedTopOrderRequest(nextRequestId());
    }

    synchronized void applyMembershipCandidate(final SurveilPartitionSession session,
            final LegalCandidate candidate) {
        Objects.requireNonNull(session, "session");
        requireRegistered(session);
        if (!session.isIdentityStable()) {
            throw new IllegalStateException("Surveil session identity is stale");
        }
        session.applyMembershipCandidate(candidate);
    }

    synchronized void applyRetainedTopOrderCandidate(final SurveilPartitionSession session,
            final LegalCandidate candidate) {
        Objects.requireNonNull(session, "session");
        requireRegistered(session);
        if (!session.isIdentityStable()) {
            throw new IllegalStateException("Surveil session identity is stale");
        }
        session.applyRetainedTopOrderCandidate(candidate);
    }

    synchronized boolean isRetainedTopOrderComplete(final SurveilPartitionSession session) {
        return Objects.requireNonNull(session, "session").isRetainedTopOrderComplete();
    }

    synchronized List<Card> finalRetainedNativeOrder(final SurveilPartitionSession session) {
        Objects.requireNonNull(session, "session");
        requireRegistered(session);
        return session.finalRetainedNativeOrder();
    }

    synchronized void markPairReady(final SurveilPartitionSession session) {
        Objects.requireNonNull(session, "session");
        requireRegistered(session);
        session.markPairReady();
    }

    synchronized boolean isComplete(final SurveilPartitionSession session) {
        return Objects.requireNonNull(session, "session").isComplete();
    }

    synchronized boolean isCaptureMaterializationReady(final SurveilPartitionSession session) {
        return session != null
                && activeSessions.get(session.surveilSessionId()) == session
                && session.isCaptureMaterializationReady();
    }

    synchronized void closeSession(final SurveilPartitionSession session) {
        if (session == null) {
            return;
        }
        final SurveilPartitionSession registered = activeSessions.get(session.surveilSessionId());
        if (registered != session) {
            return;
        }
        synchronized (session) {
            if (activeSessions.get(session.surveilSessionId()) == session) {
                activeSessions.remove(session.surveilSessionId());
                session.markClosed("CLOSED");
            }
        }
    }

    synchronized int activeSessionCount() {
        return activeSessions.size();
    }

    private synchronized void requireRegistered(final SurveilPartitionSession session) {
        if (activeSessions.get(session.surveilSessionId()) != session || session.isClosed()) {
            throw new IllegalStateException("Surveil session is stale or not registered");
        }
    }
}
