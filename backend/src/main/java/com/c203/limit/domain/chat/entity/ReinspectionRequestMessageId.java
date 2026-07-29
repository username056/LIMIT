package com.c203.limit.domain.chat.entity;

import java.io.Serializable;
import java.util.Objects;

public class ReinspectionRequestMessageId implements Serializable {
    private Long reinspectionRequestId;
    private String eventType;

    public ReinspectionRequestMessageId() {}

    public ReinspectionRequestMessageId(Long reinspectionRequestId, String eventType) {
        this.reinspectionRequestId = reinspectionRequestId;
        this.eventType = eventType;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof ReinspectionRequestMessageId that)) {
            return false;
        }
        return Objects.equals(reinspectionRequestId, that.reinspectionRequestId)
                && Objects.equals(eventType, that.eventType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(reinspectionRequestId, eventType);
    }
}
