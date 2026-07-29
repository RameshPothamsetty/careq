package com.careq.user.service;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.Security;
import java.util.UUID;

@Service
public class FileStorageService {

    private final Path uploadDir;

    public FileStorageService(@Value("${app.upload.profile-pictures-dir:uploads/profile-pictures}") String uploadDir) {
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(uploadDir);
        } catch (IOException e) {
            throw new RuntimeException("Could not create upload directory: " + uploadDir, e);
        }
    }

    /**
     * Stores the uploaded file to disk and returns the URL path to access it.
     */
    public String storeFile(MultipartFile file, String userId) {
        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }

        // Create a unique filename: userId-uuid.ext
        String filename = userId + "-" + UUID.randomUUID().toString() + extension;

        try {
            Path targetLocation = uploadDir.resolve(filename);
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
            return "/api/users/profile-pictures/" + filename;
        } catch (IOException e) {
            throw new RuntimeException("Could not store file " + filename, e);
        }
    }

    /**
     * Returns the Path to a stored file by its filename.
     * Throws SecurityException if the resolved path escapes the upload directory.
     */
    public Path getFilePath(String filename) {
        Path filePath = uploadDir.resolve(filename).normalize();
        if (!filePath.startsWith(uploadDir)) {
            throw new SecurityException("Cannot access file outside upload directory");
        }
        return filePath;
    }
}
