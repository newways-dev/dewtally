package com.kate.dewtally.ingestion;

import com.kate.dewtally.document.ProcessingStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.util.UUID;

/**
 * HTTP entry point for uploading invoice files and checking their processing.
 * The controller only translates HTTP to a service call and back; the logic lives in the service.
 */
@RestController
@RequestMapping("/api/documents")
public class DocumentUploadController {

    // Temporary until authentication (layer 5): the tenant is passed as a header.
    // Later it will come from the logged-in user's token, and this header will be removed.
    static final String TENANT_HEADER = "X-Tenant-Id";

    private final DocumentUploadService uploadService;

    public DocumentUploadController(DocumentUploadService uploadService) {
        this.uploadService = uploadService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentResponse> upload(@RequestHeader(TENANT_HEADER) UUID tenantId,
                                                   @RequestParam("file") MultipartFile file) {
        UploadResult result = uploadService.upload(tenantId, file);
        DocumentResponse body = DocumentResponse.from(result.document(), result.invoiceId());

        if (!result.created()) {
            // Same file again: nothing new was created.
            return ResponseEntity.ok(body);
        }
        URI location = URI.create("/api/documents/" + body.id());
        if (body.processingStatus() == ProcessingStatus.RECEIVED) {
            // Accepted, but processed in the background: the Location tells where to check the status.
            return ResponseEntity.accepted().location(location).body(body);
        }
        return ResponseEntity.created(location).body(body);
    }

    @GetMapping("/{id}")
    public DocumentResponse get(@RequestHeader(TENANT_HEADER) UUID tenantId, @PathVariable UUID id) {
        return uploadService.get(tenantId, id);
    }
}