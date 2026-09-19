package com.meridiantrust.sentinel.alerting.dto;

import com.meridiantrust.sentinel.alerting.model.Alert;

import com.meridiantrust.sentinel.alerting.model.AlertStatus;
import com.meridiantrust.sentinel.alerting.model.Disposition;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Alert status change.
 *
 * <p>The reason is not marked {@code @NotBlank} here because it is only
 * mandatory when closing. That conditional rule lives in the service, where the
 * current state is known — a bean-validation annotation cannot express "required
 * only for this transition", and pretending otherwise would either block legal
 * transitions or let an alert close without a reason.
 */
@Schema(description = "Alert status transition. Closing requires a disposition and reason (business rule 6).")
public record AlertTransitionRequest(
        @NotNull(message = "targetStatus is required")
        AlertStatus targetStatus,

        @Schema(description = "Required when targetStatus is CLOSED")
        Disposition disposition,

        @Size(max = 2000, message = "reason must be at most 2000 characters")
        @Schema(description = "Required when targetStatus is CLOSED", example = "Verified as salary payments")
        String reason) {
}
