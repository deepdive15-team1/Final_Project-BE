package com.highpass.runspot.session.service.dto.request;

import jakarta.validation.constraints.NotNull;

public record ParticipantActionRequest(@NotNull Action action) {
    public enum Action {
        APPROVE,
        REJECT
    }
}
