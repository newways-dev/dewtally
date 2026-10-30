package com.kate.dewtally.ingestion;

import com.kate.dewtally.document.InvoiceDocument;
import com.kate.dewtally.document.InvoiceDocumentRepository;
import com.kate.dewtally.outbox.Outbox;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Saves a document and, in asynchronous mode, the "process it" message in ONE transaction:
 * either both exist or neither does. A separate bean on purpose: @Transactional only works
 * when called from another bean.
 */
@Service
class DocumentRecorder {

    private final InvoiceDocumentRepository documents;
    private final Outbox outbox;

    DocumentRecorder(InvoiceDocumentRepository documents, Outbox outbox) {
        this.documents = documents;
        this.outbox = outbox;
    }

    @Transactional
    public InvoiceDocument save(InvoiceDocument document, boolean queue) {
        // saveAndFlush: a duplicate upload hits the unique constraint here, before the message is written.
        InvoiceDocument saved = documents.saveAndFlush(document);
        if (queue) {
            outbox.add(ProcessingQueue.NAME, saved.getId().toString());
        }
        return saved;
    }

    // An earlier attempt left the document without an invoice: queue it again.
    @Transactional
    public InvoiceDocument requeue(InvoiceDocument document) {
        document.markReceived();
        InvoiceDocument saved = documents.save(document);
        outbox.add(ProcessingQueue.NAME, saved.getId().toString());
        return saved;
    }
}