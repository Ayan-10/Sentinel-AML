package com.meridiantrust.sentinel.sar.controller;

import com.meridiantrust.sentinel.sar.model.SarDraft;
import com.meridiantrust.sentinel.sar.service.SarDraftService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.format.DateTimeFormatter;

/**
 * Suspicious Activity Report drafting.
 *
 * <p>Sits under the case it reports on, because a SAR has no meaning without the
 * investigation behind it. Read-only: generating a draft changes no alert, case
 * or disposition.
 */
@RestController
@RequestMapping("/api/v1/cases/{caseRef}/sar-draft")
@Tag(name = "SAR", description = "Suspicious Activity Report drafting (SENIOR_ANALYST only)")
public class SarController {

    /**
     * Schedule timestamps. {@code LocalDateTime.toString()} emits nanosecond
     * precision — 26 characters of "2026-09-17T16:05:10.825346" — which both
     * overruns the column and puts spurious precision into a document a
     * regulator reads. Seconds are not meaningful here; minutes are.
     */
    private static final DateTimeFormatter SCHEDULE_TIME =
            DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");

    private final SarDraftService sarDraftService;

    public SarController(SarDraftService sarDraftService) {
        this.sarDraftService = sarDraftService;
    }

    @GetMapping
    @Operation(summary = "Generate a draft SAR for a case",
            description = """
                    Assembles a filing-ready draft from the case's alerts: subject details,
                    accounts involved, the transaction schedule, and a narrative composed from
                    each alert's own explanation — so every sentence is traceable to the
                    detection that produced it.

                    **Restricted to SENIOR_ANALYST.** A SAR necessarily carries *unmasked*
                    subject PII — one that masks its subject identifies nobody and is of no use
                    to a Financial Intelligence Unit. Generating a draft is written to the
                    audit trail for that reason.

                    The draft is never filed automatically; a compliance officer reviews, edits
                    and files it. Where the case was dispositioned as a false positive, the
                    recommended action says so explicitly.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Draft generated"),
            @ApiResponse(responseCode = "400", description = "The case has no alerts to report on"),
            @ApiResponse(responseCode = "403", description = "Requires SENIOR_ANALYST"),
            @ApiResponse(responseCode = "404", description = "No such case")
    })
    public SarDraft draft(
            @Parameter(description = "Case reference", example = "CASE-20260919-1F5E98")
            @PathVariable String caseRef) {
        return sarDraftService.generate(caseRef);
    }

    @GetMapping(value = "/text", produces = MediaType.TEXT_PLAIN_VALUE)
    @Operation(summary = "The same draft as plain text",
            description = "A printable rendering for pasting into a filing system or attaching "
                        + "to a case file. Same content, same authorisation, same audit entry.")
    public ResponseEntity<String> draftAsText(@PathVariable String caseRef) {
        return ResponseEntity.ok(render(sarDraftService.generate(caseRef)));
    }

    /**
     * Plain-text rendering. Kept in the controller because it is a presentation
     * concern — the service returns structured data and stays free of formatting.
     */
    private String render(SarDraft d) {
        StringBuilder out = new StringBuilder();
        out.append("SUSPICIOUS ACTIVITY REPORT — DRAFT\n");
        out.append("=".repeat(78)).append('\n');
        out.append("Draft reference : ").append(d.draftRef()).append('\n');
        out.append("Case reference  : ").append(d.caseRef()).append('\n');
        out.append("Institution     : ").append(d.filingInstitution()).append('\n');
        out.append("Prepared by     : ").append(d.generatedBy()).append('\n');
        out.append("Prepared at     : ").append(d.generatedAt()).append('\n');
        out.append("Typologies      : ").append(String.join(", ", d.typologies())).append('\n');
        out.append("=".repeat(78)).append("\n\n");

        out.append("NARRATIVE\n").append("-".repeat(78)).append('\n');
        out.append(d.narrative()).append("\n\n");

        out.append("TRANSACTION SCHEDULE (").append(d.transactions().size()).append(" line(s))\n");
        out.append("-".repeat(78)).append('\n');
        out.append(String.format("%-22s %-17s %-7s %16s  %s%n",
                "TRANSACTION", "DATE/TIME", "DR/CR", "AMOUNT (" + d.activity().baseCurrency() + ")",
                "COUNTERPARTY"));
        for (SarDraft.TransactionLine t : d.transactions()) {
            out.append(String.format("%-22s %-17s %-7s %16s  %s%n",
                    t.transactionId(),
                    t.timestamp() == null ? "—" : SCHEDULE_TIME.format(t.timestamp()),
                    t.direction(),
                    t.amountBase().toPlainString(),
                    (t.counterpartyName() == null ? "—" : t.counterpartyName())
                        + (t.counterpartyCountry() == null ? "" : " (" + t.counterpartyCountry() + ")")));
        }

        out.append('\n').append("RECOMMENDED ACTION\n").append("-".repeat(78)).append('\n');
        out.append(d.recommendedAction()).append('\n');
        out.append('\n').append("Source alerts: ").append(String.join(", ", d.sourceAlerts())).append('\n');
        return out.toString();
    }
}
