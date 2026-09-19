package com.meridiantrust.sentinel.customer.controller;

import com.meridiantrust.sentinel.customer.model.Customer;
import com.meridiantrust.sentinel.transaction.model.Transaction;

import com.meridiantrust.sentinel.customer.dto.CustomerDtos;
import com.meridiantrust.sentinel.customer.service.CustomerQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/customers")
@Tag(name = "Customers", description = "KYC records and transaction timeline")
public class CustomerController {

    private final CustomerQueryService queryService;

    public CustomerController(CustomerQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/{customerId}")
    @Operation(summary = "Customer (PII masked)",
            description = "Business rule 8: names and identifiers are masked. Any analyst may call this.")
    public CustomerDtos.Masked masked(@PathVariable String customerId) {
        return queryService.masked(customerId);
    }

    @GetMapping("/{customerId}/full")
    @Operation(summary = "Customer with full PII",
            description = """
                    Business rule 8: unmasked KYC detail, restricted to SENIOR_ANALYST
                    and above. Enforced by both the URL rule and a method-level check —
                    the UI is not part of the decision.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Full record returned"),
            @ApiResponse(responseCode = "403", description = "Requires SENIOR_ANALYST"),
            @ApiResponse(responseCode = "404", description = "No such customer")
    })
    public CustomerDtos.Full full(@PathVariable String customerId) {
        return queryService.full(customerId);
    }

    @GetMapping("/{customerId}/timeline")
    @Operation(summary = "Transaction timeline",
            description = "Chronological activity, annotating each transaction with the alerts it evidences.")
    public List<CustomerDtos.TimelineEntry> timeline(
            @PathVariable String customerId,
            @RequestParam(defaultValue = "100") int limit) {
        return queryService.timeline(customerId, limit);
    }
}
