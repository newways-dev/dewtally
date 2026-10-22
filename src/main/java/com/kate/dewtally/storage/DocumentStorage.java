package com.kate.dewtally.storage;

import java.util.UUID;

/**
 * Stores original invoice files and reads them back.
 * The rest of the application only knows this interface, so the local
 * implementation can later be replaced by Azure Blob Storage without changes elsewhere.
 */
public interface DocumentStorage {

    /**
     * Stores the content and returns a key that identifies it.
     * The key is saved in the database (invoice_document.storage_key).
     */
    String store(UUID tenantId, byte[] content);

    /**
     * Returns the content previously stored under the given key.
     */
    byte[] load(String storageKey);
}