package com.kate.dewtally.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests without Spring: JUnit provides a temporary folder that is deleted afterward.
 */
class LocalDocumentStorageTest {

    private static final byte[] CONTENT = "<Invoice>RE-2041</Invoice>".getBytes(StandardCharsets.UTF_8);

    @TempDir
    private Path tempDir;

    private LocalDocumentStorage storage;

    @BeforeEach
    void setUp() {
        storage = new LocalDocumentStorage(tempDir.toString());
    }

    @Test
    void loadsExactlyWhatWasStored() {
        String key = storage.store(UUID.randomUUID(), CONTENT);

        assertThat(storage.load(key)).isEqualTo(CONTENT);
    }

    @Test
    void storesDocumentsInTheTenantsFolder() {
        UUID tenantId = UUID.randomUUID();

        String key = storage.store(tenantId, CONTENT);

        assertThat(key).startsWith(tenantId + "/");
        assertThat(Files.exists(tempDir.resolve(key))).isTrue();
    }

    @Test
    void givesEveryStoredDocumentItsOwnKey() {
        UUID tenantId = UUID.randomUUID();

        String first = storage.store(tenantId, CONTENT);
        String second = storage.store(tenantId, CONTENT);

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void rejectsKeysPointingOutsideTheStorageFolder() {
        assertThatThrownBy(() -> storage.load("../outside.txt"))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("Invalid storage key");
    }

    @Test
    void failsClearlyWhenTheDocumentDoesNotExist() {
        assertThatThrownBy(() -> storage.load(UUID.randomUUID() + "/missing"))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("Document not found");
    }
}