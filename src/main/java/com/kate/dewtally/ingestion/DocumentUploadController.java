package com.kate.dewtally.ingestion;

import com.kate.dewtally.document.ProcessingStatus;
import com.kate.dewtally.security.CurrentUser;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.util.UUID;

/**
 * HTTP entry point for uploading invoice files and checking their processing.
 * The controller only translates HTTP to a service call and back; the logic lives in the service.
 * The company always comes from the user's token.
 */
@RestController
@RequestMapping("/api/documents")
public class DocumentUploadController {

    private final DocumentUploadService uploadService;

    public DocumentUploadController(DocumentUploadService uploadService) {
        this.uploadService = uploadService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentResponse> upload(CurrentUser user, @RequestParam("file") MultipartFile file) {
        UploadResult result = uploadService.upload(user.tenantId(), file);
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
    public DocumentResponse get(CurrentUser user, @PathVariable UUID id) {
        return uploadService.get(user.tenantId(), id);
    }
}