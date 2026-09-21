package com.baakio.storage;

import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class LocalStorageAdapter implements DocumentStorage {
    private final Path root;

    public LocalStorageAdapter(@Value("${baakio.storage.local-directory}") String directory) {
        this.root = Path.of(directory).toAbsolutePath().normalize();
    }

    @Override
    public void put(String objectKey, byte[] content, String contentType) {
        Path target = resolve(objectKey);
        try {
            Files.createDirectories(target.getParent());
            Path temp = Files.createTempFile(target.getParent(), ".upload-", ".tmp");
            try {
                Files.write(temp, content);
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                Files.writeString(target.resolveSibling(target.getFileName() + ".type"), contentType);
            } finally {
                Files.deleteIfExists(temp);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Private local document storage is unavailable.", exception);
        }
    }

    @Override
    public StoredDocument get(String objectKey) {
        Path target = resolve(objectKey);
        try {
            if (!Files.isRegularFile(target)) throw new IllegalStateException("Stored document is missing.");
            String contentType = Files.readString(target.resolveSibling(target.getFileName() + ".type"));
            return new StoredDocument(Files.readAllBytes(target), contentType);
        } catch (IOException exception) {
            throw new IllegalStateException("Private local document storage is unavailable.", exception);
        }
    }

    @Override
    public void delete(String objectKey) {
        Path target = resolve(objectKey);
        try {
            Files.deleteIfExists(target);
            Files.deleteIfExists(target.resolveSibling(target.getFileName() + ".type"));
        } catch (IOException exception) {
            throw new IllegalStateException("Private local document storage is unavailable.", exception);
        }
    }

    private Path resolve(String objectKey) {
        Path target = root.resolve(objectKey).normalize();
        if (!target.startsWith(root)) throw new IllegalArgumentException("Invalid private object key.");
        return target;
    }
}
