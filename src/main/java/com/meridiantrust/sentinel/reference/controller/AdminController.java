package com.meridiantrust.sentinel.reference.controller;

import com.meridiantrust.sentinel.common.model.Severity;
import com.meridiantrust.sentinel.reference.service.RuleAdminService;
import com.meridiantrust.sentinel.reference.model.*;
import com.meridiantrust.sentinel.reference.repository.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Compliance administration — rule thresholds, FX rates and sanctions lists.
 *
 * <p>This controller is the concrete answer to "rules must be configurable
 * without a code redeployment". Restricted to COMPLIANCE_ADMIN and fully
 * audited: every change records who made it and what it was before.
 */
@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Administration",
     description = "Runtime rule tuning and reference data (COMPLIANCE_ADMIN only)")
public class AdminController {

    private final RuleAdminService adminService;

    public AdminController(RuleAdminService adminService) {
        this.adminService = adminService;
    }

    // --- Rules -------------------------------------------------------------

    @GetMapping("/rules")
    @Operation(summary = "List all detection rules with their current configuration")
    public List<RuleDto> rules() {
        return adminService.allRules().stream().map(RuleDto::from).toList();
    }

    @GetMapping("/rules/{ruleCode}")
    @Operation(summary = "Get one rule's configuration")
    public RuleDto rule(@PathVariable String ruleCode) {
        return RuleDto.from(adminService.requireRule(ruleCode));
    }

    @PatchMapping("/rules/{ruleCode}")
    @Operation(summary = "Tune a rule at runtime",
            description = """
                    Changes take effect on the next detection run — no restart, no
                    redeployment. Parameters are validated before they are accepted,
                    so a configuration that could never fire is rejected rather than
                    silently disabling a control.

                    Example — loosen structuring to catch pairs rather than triples:
                    `{"params": {"minCount": 2, "windowHours": 24,
                                 "lowerBound": 9000, "upperBound": 9999.99}}`
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Rule updated and cache refreshed"),
            @ApiResponse(responseCode = "400", description = "Parameters failed validation"),
            @ApiResponse(responseCode = "403", description = "Requires COMPLIANCE_ADMIN"),
            @ApiResponse(responseCode = "404", description = "No such rule")
    })
    public RuleDto updateRule(@PathVariable String ruleCode,
                              @Valid @RequestBody RuleUpdateRequest request) {
        return RuleDto.from(adminService.updateRule(ruleCode, request.enabled(),
                request.weight(), request.severity(), request.params()));
    }

    // --- FX rates (business rule 9) ----------------------------------------

    @GetMapping("/fx-rates")
    @Operation(summary = "Exchange-rate table used for base-currency normalisation")
    public List<FxRate> fxRates() {
        return adminService.fxRates();
    }

    @PutMapping("/fx-rates/{currency}")
    @Operation(summary = "Set an exchange rate",
            description = "Applies to transactions ingested from this point on; historical "
                        + "amounts keep the rate they were normalised with.")
    public FxRate updateFxRate(@PathVariable String currency,
                               @Valid @RequestBody FxRateRequest request) {
        return adminService.updateFxRate(currency, request.rateToBase());
    }

    // --- Jurisdictions and watchlist (business rule 4) ---------------------

    @GetMapping("/jurisdictions")
    @Operation(summary = "High-risk and sanctioned jurisdiction list")
    public List<HighRiskJurisdiction> jurisdictions() {
        return adminService.jurisdictions();
    }

    @PostMapping("/jurisdictions")
    @Operation(summary = "Add or update a jurisdiction listing")
    public ResponseEntity<HighRiskJurisdiction> upsertJurisdiction(
            @RequestBody HighRiskJurisdiction jurisdiction) {
        return ResponseEntity.ok(adminService.upsertJurisdiction(jurisdiction));
    }

    @DeleteMapping("/jurisdictions/{countryCode}")
    @Operation(summary = "Deactivate a jurisdiction listing",
            description = "Deactivates rather than deletes — historical alerts citing this "
                        + "listing must remain explicable.")
    public ResponseEntity<Void> deactivateJurisdiction(@PathVariable String countryCode) {
        adminService.deactivateJurisdiction(countryCode);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/watchlist")
    @Operation(summary = "Named counterparty watchlist")
    public List<WatchlistCounterparty> watchlist() {
        return adminService.watchlist();
    }

    @PostMapping("/watchlist")
    @Operation(summary = "Add a counterparty to the watchlist")
    public ResponseEntity<WatchlistCounterparty> addWatchlist(
            @RequestBody WatchlistCounterparty entry) {
        return ResponseEntity.ok(adminService.addWatchlistEntry(entry));
    }

    // --- DTOs --------------------------------------------------------------

    @Schema(description = "Partial rule update — omitted fields are left unchanged.")
    public record RuleUpdateRequest(
            Boolean enabled,
            @Schema(description = "Base contribution to the risk score, 0-100") Integer weight,
            Severity severity,
            @Schema(description = "Rule-specific thresholds and time windows")
            Map<String, Object> params) {}

    public record FxRateRequest(
            @NotNull(message = "rateToBase is required")
            @DecimalMin(value = "0.00000001", message = "rateToBase must be greater than zero")
            BigDecimal rateToBase) {}

    public record RuleDto(String ruleCode, String name, String typology, boolean enabled,
                          int weight, String severity, String params, String description,
                          String updatedBy, Instant updatedAt) {

        static RuleDto from(RuleConfig c) {
            return new RuleDto(c.getRuleCode(), c.getName(), c.getTypology(), c.isEnabled(),
                    c.getWeight(), c.getSeverity().name(), c.getParams(), c.getDescription(),
                    c.getUpdatedBy(), c.getUpdatedAt());
        }
    }
}
