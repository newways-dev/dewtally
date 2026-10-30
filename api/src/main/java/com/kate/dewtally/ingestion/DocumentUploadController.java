package com.kate.dewtally.ingestion;

import com.kate.dewtally.document.ProcessingStatus;
import com.kate.dewtally.invoice.PageResponse;
import com.kate.dewtally.security.CurrentUser;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * HTTP entry point for uploading invoice files, checking their processing and reading the originals.
 * The controller only translates HTTP to a service call and back; the logic lives in the services.
 * The company always comes from the user's token.
 */
@RestController
@RequestMapping("/api/documents")
public class DocumentUploadController {

    private final DocumentUploadService uploadService;
    private final DocumentQueryService queries;

    public DocumentUploadController(DocumentUploadService uploadService, DocumentQueryService queries) {
        this.uploadService = uploadService;
        this.queries = queries;
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

    @GetMapping
    public PageResponse<DocumentResponse> list(CurrentUser user,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return queries.list(user.tenantId(), page, size);
    }

    @GetMapping("/{id}")
    public DocumentResponse get(CurrentUser user, @PathVariable UUID id) {
        return uploadService.get(user.tenantId(), id);
    }

    /**
     * The original file for display. Uploaded files are untrusted: the browser may show them,
     * but the sandbox policy never lets it run scripts from them.
     */
    @GetMapping("/{id}/content")
    public ResponseEntity<byte[]> content(CurrentUser user, @PathVariable UUID id) {
        DocumentQueryService.Content content = queries.content(user.tenantId(), id);
        return ResponseEntity.ok()
                .contentType(content.mediaType())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(content.fileName(), StandardCharsets.UTF_8).build().toString())
                .header("Content-Security-Policy", "sandbox")
                // Invoices are confidential: no copy in browser or proxy caches
                .cacheControl(CacheControl.noStore())
                .body(content.bytes());
    }
}