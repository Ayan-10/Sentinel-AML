package com.meridiantrust.sentinel.ingestion.controller;

import com.meridiantrust.sentinel.common.error.ApiException;
import com.meridiantrust.sentinel.detection.model.DetectionResult;
import com.meridiantrust.sentinel.ingestion.dto.IngestionDtos;
import com.meridiantrust.sentinel.ingestion.model.*;
import com.meridiantrust.sentinel.ingestion.port.*;
import com.meridiantrust.sentinel.ingestion.service.*;
import com.meridiantrust.sentinel.ingestion.util.*;
import com.meridiantrust.sentinel.ingestion.validation.*;
import com.meridiantrust.sentinel.ingestion.model.IngestionBatch;
import com.meridiantrust.sentinel.ingestion.mapper.CsvRowMappers;
import com.meridiantrust.sentinel.ingestion.util.CsvSupport;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Ingestion endpoints — restricted to COMPLIANCE_ADMIN.
 *
 * <p>Each adapter converts its own wire format into the shape the
 * {@link TransactionIngestPort} accepts and then delegates. The controller
 * holds no ingestion logic of its own, which is what keeps adding a new source
 * (a Kafka listener, an SFTP poller) a matter of adding an adapter rather than
 * editing this class.
 */
@RestController
@RequestMapping("/api/v1/ingestion")
@Tag(name = "Ingestion", description = "Bulk CSV and real-time streaming data ingestion (COMPLIANCE_ADMIN)")
public class IngestionController {

    private final IngestionService ingestionService;
    private final MasterDataIngestionService masterDataService;

    public IngestionController(IngestionService ingestionService,
                               MasterDataIngestionService masterDataService) {
        this.ingestionService = ingestionService;
        this.masterDataService = masterDataService;
    }

    @PostMapping(value = "/customers/csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Bulk-load customer KYC records from CSV",
            description = "Columns follow docs/customers.csv. Malformed rows are rejected individually.")
    public IngestionDtos.IngestionResponse ingestCustomers(@RequestParam("file") MultipartFile file) {
        return toResponse(masterDataService.ingestCustomers(readCsv(file), file.getOriginalFilename()));
    }

    @PostMapping(value = "/accounts/csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Bulk-load accounts from CSV",
            description = "Rows whose customer_id does not exist are rejected — load customers first.")
    public IngestionDtos.IngestionResponse ingestAccounts(@RequestParam("file") MultipartFile file) {
        return toResponse(masterDataService.ingestAccounts(readCsv(file), file.getOriginalFilename()));
    }

    @PostMapping(value = "/transactions/csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Bulk-load transactions and run detection",
            description = """
                    Validates, normalises to base currency (business rule 9), persists,
                    then evaluates every enabled rule over the accepted records.
                    The response reports wall-clock duration so the bulk performance
                    target is demonstrable rather than asserted.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Batch processed; see rejections for refused rows"),
            @ApiResponse(responseCode = "400", description = "File missing or unreadable")
    })
    public IngestionDtos.IngestionResponse ingestTransactions(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "true") boolean runDetection) {

        List<RawTransaction> records = readCsv(file).stream()
                .map(CsvRowMappers::toRawTransaction)
                .toList();
        return toResponse(ingestionService.ingest(records, file.getOriginalFilename(), runDetection));
    }

    @PostMapping("/transactions/batch")
    @Operation(summary = "Ingest a JSON batch of transactions")
    public IngestionDtos.IngestionResponse ingestBatch(
            @Valid @RequestBody List<IngestionDtos.SingleTransactionRequest> requests,
            @RequestParam(defaultValue = "true") boolean runDetection) {

        List<RawTransaction> records = requests.stream().map(this::toRaw).toList();
        return toResponse(ingestionService.ingest(records, "rest-batch", runDetection));
    }

    @PostMapping("/transactions")
    @Operation(summary = "Ingest a single transaction in real time",
            description = """
                    The streaming path. Validates, persists and evaluates detection rules
                    synchronously, so the response states whether the transaction alerted
                    and how long evaluation took — sub-second under normal conditions.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Accepted and evaluated"),
            @ApiResponse(responseCode = "400", description = "Validation failed — see problem detail")
    })
    public ResponseEntity<IngestionDtos.StreamingResponse> ingestSingle(
            @Valid @RequestBody IngestionDtos.SingleTransactionRequest request) {

        DetectionResult result = ingestionService.ingestStreaming(toRaw(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(
                new IngestionDtos.StreamingResponse(
                        request.transactionId(), true, result.hitsFound(),
                        result.alertsCreated(), result.durationMs(), Instant.now()));
    }

    @GetMapping("/batches/{batchRef}")
    @Operation(summary = "Batch summary with every rejected row and its reason")
    public IngestionDtos.BatchSummary batch(@PathVariable String batchRef) {
        IngestionBatch batch = ingestionService.requireBatch(batchRef);
        List<IngestionDtos.RejectionDto> rejections = ingestionService.errorsFor(batch.getId()).stream()
                .map(e -> new IngestionDtos.RejectionDto(e.getRowNumber(), e.getRecordKey(),
                        e.getErrorType(), e.getErrorMessage()))
                .toList();
        return new IngestionDtos.BatchSummary(
                batch.getBatchRef(), batch.getEntityType(), batch.getSource(),
                batch.getTotalRecords(), batch.getAcceptedRecords(), batch.getRejectedRecords(),
                batch.getAlertsGenerated(), batch.getStatus().name(), batch.getStartedAt(),
                batch.getFinishedAt(), batch.getDurationMs(), batch.getInitiatedBy(), rejections);
    }

    // --- adapters ----------------------------------------------------------

    private List<Map<String, String>> readCsv(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException.Validation("A non-empty CSV file is required under the 'file' part.");
        }
        try {
            return CsvSupport.readAll(file.getInputStream());
        } catch (IOException ex) {
            throw new ApiException.Validation("Could not read uploaded file: " + ex.getMessage());
        }
    }

    private RawTransaction toRaw(IngestionDtos.SingleTransactionRequest r) {
        return new RawTransaction(
                r.transactionId(), r.accountId(), r.customerId(), r.txnTimestamp(),
                r.direction(), r.amount() == null ? null : r.amount().toPlainString(),
                r.currency(), r.channel(), r.txnType(), r.counterpartyName(),
                r.counterpartyAccount(), r.counterpartyBank(), r.counterpartyCountry(),
                r.description(), "POSTED", r.toString());
    }

    private IngestionDtos.IngestionResponse toResponse(IngestionResult result) {
        return new IngestionDtos.IngestionResponse(
                result.batchRef(), result.entityType(), result.totalRecords(),
                result.acceptedRecords(), result.rejectedRecords(), result.alertsGenerated(),
                result.durationMs(),
                result.rejections().stream()
                        .map(r -> new IngestionDtos.RejectionDto(
                                r.rowNumber(), r.recordKey(), r.errorType(), r.message()))
                        .toList());
    }
}
