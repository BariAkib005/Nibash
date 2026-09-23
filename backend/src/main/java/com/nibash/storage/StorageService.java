package com.nibash.storage;

import com.nibash.common.ApiException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Local-disk file storage behind a single seam (plan §0.4). Everything the app stores goes through
 * {@link #store}, so swapping in Azure Blob or S3 later means reimplementing this class and nothing
 * else — no controller knows where bytes actually live.
 *
 * <p>Returned paths are always relative and forward-slashed (e.g. {@code tickets/12/leak.jpg}), so
 * they work unchanged as URL suffixes under {@code /media/} regardless of host OS.
 */
@Service
public class StorageService {

    private final Path root;

    public StorageService(@Value("${nibash.media-dir}") String mediaDir) {
        this.root = Paths.get(mediaDir).toAbsolutePath().normalize();
    }

    /**
     * Writes {@code file} under {@code folder} and returns its relative path.
     *
     * <p>Filenames collide constantly in practice (every phone calls it {@code IMG_0001.jpg}), so a
     * numeric suffix is appended until the name is free rather than overwriting a previous upload.
     */
    public String store(MultipartFile file, String folder) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("file is required");
        }
        String original = sanitize(file.getOriginalFilename());
        Path directory = root.resolve(folder).normalize();

        if (!directory.startsWith(root)) {
            throw ApiException.badRequest("Invalid upload location.");
        }

        try {
            Files.createDirectories(directory);
            Path target = deduplicate(directory, original);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return folder + "/" + target.getFileName();
        } catch (IOException e) {
            throw new IllegalStateException("Unable to store upload", e);
        }
    }

    /** Rejects anything larger than {@code maxBytes}, using the contract's own message. */
    public void requireAtMost(MultipartFile file, long maxBytes, String message) {
        if (file != null && file.getSize() > maxBytes) {
            throw ApiException.badRequest(message);
        }
    }

    /** Photos: ticket images, and the image half of receipts. */
    public static final Set<String> IMAGE_TYPES = Set.of("jpg", "jpeg", "png", "gif", "webp", "heic");

    /** Receipts are photographed or scanned. */
    public static final Set<String> RECEIPT_TYPES = union(IMAGE_TYPES, Set.of("pdf"));

    /** The document repository: office formats, PDFs, plain text and images. */
    public static final Set<String> DOCUMENT_TYPES = union(IMAGE_TYPES, Set.of(
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "odp", "txt", "csv", "rtf"));

    /**
     * Rejects a file whose extension is not in {@code allowed}.
     *
     * <p>Uploads are served back from this app's own origin under {@code /media/}, so an
     * {@code .html} or {@code .svg} "photo" would run script in every viewer's session. The
     * allowlist is the first line of defence; the media handler's {@code nosniff} and
     * {@code Content-Security-Policy} headers are the second.
     */
    public void requireType(MultipartFile file, Set<String> allowed) {
        if (file == null || file.isEmpty()) {
            return;
        }
        String name = sanitize(file.getOriginalFilename());
        int dot = name.lastIndexOf('.');
        String extension = dot > 0 ? name.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
        if (!allowed.contains(extension)) {
            throw ApiException.badRequest("Unsupported file type. Allowed: " + String.join(", ", new java.util.TreeSet<>(allowed)) + ".");
        }
    }

    private static Set<String> union(Set<String> a, Set<String> b) {
        Set<String> all = new java.util.HashSet<>(a);
        all.addAll(b);
        return Set.copyOf(all);
    }

    public Path resolve(String relativePath) {
        return root.resolve(relativePath).normalize();
    }

    /** Strips directory components and anything that could escape the media root. */
    private String sanitize(String filename) {
        if (filename == null || filename.isBlank()) {
            return "upload";
        }
        String name = Paths.get(filename).getFileName().toString();
        name = name.replaceAll("[^A-Za-z0-9._-]", "_");
        return name.isBlank() || name.equals(".") || name.equals("..") ? "upload" : name;
    }

    private Path deduplicate(Path directory, String filename) {
        Path candidate = directory.resolve(filename);
        if (!Files.exists(candidate)) {
            return candidate;
        }
        int dot = filename.lastIndexOf('.');
        String stem = dot > 0 ? filename.substring(0, dot) : filename;
        String extension = dot > 0 ? filename.substring(dot).toLowerCase(Locale.ROOT) : "";

        for (int suffix = 1; suffix < 10_000; suffix++) {
            candidate = directory.resolve(stem + "_" + suffix + extension);
            if (!Files.exists(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Too many files named " + filename);
    }
}
