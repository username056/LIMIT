package com.c203.limit.domain.inspection.checklist;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "limit.ai.checklist",
        name = "enabled",
        havingValue = "false",
        matchIfMissing = true)
public class DisabledChecklistSupplementClient implements ChecklistSupplementClient {

    @Override
    public ChecklistSupplementResult suggest(ChecklistGenerationContext context) {
        return ChecklistSupplementResult.unavailable();
    }
}
