package forge.game.decision;

enum SurveilPartitionAdmissionFailureReason {
    SESSION_INTEGRITY_FAILURE,
    UNSUPPORTED_ADMISSION,
    UNKNOWN
}

final class SurveilPartitionAdmissionFailure extends RuntimeException {
    private final SurveilPartitionAdmissionFailureReason reason;

    SurveilPartitionAdmissionFailure(final SurveilPartitionAdmissionFailureReason reason,
            final String message) {
        super(message);
        this.reason = reason;
    }

    SurveilPartitionAdmissionFailure(final SurveilPartitionAdmissionFailureReason reason,
            final String message, final Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    SurveilPartitionAdmissionFailureReason reason() {
        return reason;
    }
}
