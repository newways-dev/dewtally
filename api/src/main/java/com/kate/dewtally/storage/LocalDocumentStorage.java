package com.kate.dewtally.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

/**
 * Stores documents as files in a local folder: {root}/{tenantId}/{randomId}.
 * Used for local development; production will use Azure Blob Storage.
 */
@Component
public class LocalDocumentStorage implements DocumentStorage {

    private final Path root;

    public LocalDocumentStorage(@Value("${dewtally.storage.local-root:data/documents}") String root) {
        this.root = Path.of(root).toAbsolutePath().normalize();
    }

    @Override
    public String store(UUID tenantId, byte[] content) {
        // One folder per tenant keeps documents of different customers apart.
        String storageKey = tenantId + "/" + UUID.randomUUID();
        Path target = resolve(storageKey);

        try {
            Files.createDirectories(target.getParent());
            // CREATE_NEW: never overwrite an existing original document.
            Files.write(target, content, StandardOpenOption.CREATE_NEW);
            return storageKey;
        } catch (IOException e) {
            throw new StorageException("Could not store document " + storageKey, e);
        }
    }

    @Override
    public byte[] load(String storageKey) {
        Path source = resolve(storageKey);

        try {
            return Files.readAllBytes(source);
        } catch (NoSuchFileException e) {
            throw new StorageException("Document not found: " + storageKey, e);
        } catch (IOException e) {
            throw new StorageException("Could not read document " + storageKey, e);
        }
    }

    // Protects against path traversal: a key like "../../secret.txt"
    // must never point to a file outside the storage folder.
    private Path resolve(String storageKey) {
        Path path = root.resolve(storageKey).normalize();
        if (!path.startsWith(root)) {
            throw new StorageException("Invalid storage key: " + storageKey);
        }
        return path;
    }
}