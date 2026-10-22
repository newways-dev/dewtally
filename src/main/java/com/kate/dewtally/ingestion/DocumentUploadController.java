package com.kate.dewtally.ingestion;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.util.UUID;

/**
 * HTTP entry point for uploading invoice files.
 * The controller only translates HTTP to a service call and back; the logic lives in the service.
 */
@RestController
@RequestMapping("/api/documents")
public class DocumentUploadController {

    static final String TENANT_HEADER = "X-Tenant-Id";

    private final DocumentUploadService uploadService;

    public DocumentUploadController(DocumentUploadService uploadService) {
        this.uploadService = uploadService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentResponse> upload(@RequestHeader(TENANT_HEADER) UUID tenantId,
                                                   @RequestParam("file") MultipartFile file) {
        UploadResult result = uploadService.upload(tenantId, file);
        DocumentResponse body = DocumentResponse.from(result.document());

        if (!result.created()) {
            // Same file again: nothing new was created.
            return ResponseEntity.ok(body);
        }
        return ResponseEntity.created(URI.create("/api/documents/" + body.id())).body(body);
    }
}