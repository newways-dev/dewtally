package com.kate.dewtally.ingestion;

import com.kate.dewtally.document.InvoiceDocument;
import com.kate.dewtally.document.InvoiceDocumentRepository;
import com.kate.dewtally.invoice.InvoiceService;
import com.kate.dewtally.invoice.PageResponse;
import com.kate.dewtally.storage.DocumentStorage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * Read side of uploaded documents: the list of recent uploads and the original files.
 * Not @Transactional: reading a file from storage should not hold a database connection.
 */
@Service
public class DocumentQueryService {

    static final int MAX_PAGE_SIZE = 100;

    private final InvoiceDocumentRepository documents;
    private final DocumentStorage storage;
    private final InvoiceService invoiceService;

    public DocumentQueryService(InvoiceDocumentRepository documents,
                                DocumentStorage storage,
                                InvoiceService invoiceService) {
        this.documents = documents;
        this.storage = storage;
        this.invoiceService = invoiceService;
    }

    // Decided by what the file really is, never by the content type the uploader claimed:
    // an "invoice" sent as text/html could otherwise run scripts in the user's browser.
    private static MediaType mediaType(InvoiceDocument document) {
        return switch (document.getSourceFormat()) {
            case PDF, ZUGFERD -> MediaType.APPLICATION_PDF;
            case UBL, CII -> MediaType.APPLICATION_XML;
            default -> MediaType.APPLICATION_OCTET_STREAM;
        };
    }

    /**
     * Recent uploads of the tenant, newest first, each with its processing status and invoice.
     */
    PageResponse<DocumentResponse> list(UUID tenantId, int page, int size) {
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.clamp(size, 1, MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "uploadedAt"));
        Page<InvoiceDocument> result = documents.findByTenantId(tenantId, pageable);
        return PageResponse.from(result, document -> DocumentResponse.from(
                document, invoiceService.findIdByDocument(tenantId, document.getId()).orElse(null)));
    }

    /**
     * The original file, exactly as uploaded, for display in the browser.
     */
    Content content(UUID tenantId, UUID documentId) {
        InvoiceDocument document = documents.findByIdAndTenantId(documentId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
        byte[] bytes = storage.load(document.getStorageKey());
        return new Content(document.getFileName(), mediaType(document), bytes);
    }

    record Content(String fileName, MediaType mediaType, byte[] bytes) {
    }
}