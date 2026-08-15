package com.careq.user.service;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.PublicAccessType;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Profile-picture storage.
 *
 * dual-mode. Locally and in Docker (no AZURE_STORAGE_CONNECTION_STRING)
 * pictures are stored on disk under {@code app.upload.profile-pictures-dir}
 * and served by GET /api/users/profile-pictures/{filename}. On Azure the
 * deploy job injects the storage account connection string, so uploads go to
 * the public-read {@code careq-uploads} container in Azure Blob Storage
 * (free 5 GB tier) and the profile carries the blob's public URL — pictures
 * now survive redeploys (previously they were ephemeral container storage).
 */
@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    private final Path uploadDir;
    private final String blobConnectionString;
    private final String blobContainerName;

    private BlobContainerClient blobContainerClient;

    public FileStorageService(
            @Value("${app.upload.profile-pictures-dir:uploads/profile-pictures}") String uploadDir,
            @Value("${AZURE_STORAGE_CONNECTION_STRING:}") String blobConnectionString,
            @Value("${app.storage.blob-container:careq-uploads}") String blobContainerName) {
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
        this.blobConnectionString = blobConnectionString;
        this.blobContainerName = blobContainerName;
    }

    private boolean blobEnabled() {
        return blobConnectionString != null && !blobConnectionString.isBlank();
    }

    @PostConstruct
    public void init() {
        if (blobEnabled()) {
            BlobServiceClient serviceClient = new BlobServiceClientBuilder()
                    .connectionString(blobConnectionString)
                    .buildClient();
            blobContainerClient = serviceClient.getBlobContainerClient(blobContainerName);
            if (!blobContainerClient.exists()) {
                // Bicep normally pre-creates careq-uploads (public-read). If it is
                // ever missing, create it PUBLIC so returned blob URLs load in
                // <img> tags without credentials.
                blobContainerClient.create();
                blobContainerClient.setAccessPolicy(PublicAccessType.CONTAINER, null);
            }
            log.info("Profile pictures stored in Azure Blob Storage container '{}'", blobContainerName);
        } else {
            try {
                Files.createDirectories(uploadDir);
            } catch (IOException e) {
                throw new RuntimeException("Could not create upload directory: " + uploadDir, e);
            }
            log.info("Profile pictures stored on local disk at {}", uploadDir);
        }
    }

    /**
     * Stores the uploaded file and returns its public URL.
     * Blob mode → https://&lt;account&gt;.blob.core.windows.net/careq-uploads/&lt;file&gt;
     * Local mode → the relative route served by this service.
     */
    public String storeFile(MultipartFile file, String userId) {
        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }

        // Create a unique filename: userId-uuid.ext
        String filename = userId + "-" + UUID.randomUUID() + extension;

        if (blobEnabled()) {
            try (InputStream in = file.getInputStream()) {
                BlobClient blobClient = blobContainerClient.getBlobClient(filename);
                blobClient.upload(in, file.getSize(), true);
                return blobClient.getBlobUrl();
            } catch (IOException e) {
                throw new RuntimeException("Could not store file " + filename + " in Blob Storage", e);
            }
        }

        try {
            Path targetLocation = uploadDir.resolve(filename);
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
            return "/api/users/profile-pictures/" + filename;
        } catch (IOException e) {
            throw new RuntimeException("Could not store file " + filename, e);
        }
    }

    /**
     * Returns the Path to a stored file by its filename (local mode only —
     * blob-mode URLs are absolute, so the serving route is never hit).
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
