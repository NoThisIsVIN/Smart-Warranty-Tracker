package org.example.service;

import org.example.model.Warranty;
import org.example.receipt.ReceiptTextExtractor;
import org.example.repo.WarrantyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Warranty persistence plus ownership of the files (receipt, product image) attached to each row. */
@Service
public class WarrantyService {
    private static final Logger log = LoggerFactory.getLogger(WarrantyService.class);

    private final WarrantyRepository repo;
    private final FileStorage files;
    private final ReceiptTextExtractor extractor;

    public WarrantyService(WarrantyRepository repo, FileStorage files, ReceiptTextExtractor extractor) {
        this.repo = repo;
        this.files = files;
        this.extractor = extractor;
    }

    public List<Warranty> findAll() {
        return repo.findAll();
    }

    public Optional<Warranty> findById(Long id) {
        return id == null ? Optional.empty() : repo.findById(id);
    }

    /**
     * Saves a new warranty. The receipt (if any) is copied into app storage only now, so cancelled dialogs leave
     * nothing behind. For a PDF receipt without a product image, the rendered first page becomes the image.
     */
    public Warranty create(Warranty draft, Path receiptSourceOrNull) throws IOException {
        if (receiptSourceOrNull != null) {
            draft.setReceiptImageUrl(files.importFile(receiptSourceOrNull, FileStorage.Kind.RECEIPT));
            if (isBlank(draft.getImageUrl()) && FileStorage.extension(receiptSourceOrNull.toString()).equals(".pdf")) {
                draft.setImageUrl(renderPreview(receiptSourceOrNull));
            }
        }
        try {
            return repo.save(draft);
        } catch (RuntimeException e) {
            files.deleteIfOwned(draft.getReceiptImageUrl());
            files.deleteIfOwned(draft.getImageUrl());
            throw e;
        }
    }

    /**
     * Saves edits. Resets {@code notified} when the purchase date or warranty length changed, and keeps an image
     * that was fetched in the background while the caller's copy was open.
     */
    public Warranty update(Warranty w) {
        findById(w.getId()).ifPresent(stored -> {
            boolean termsChanged = !Objects.equals(stored.getPurchaseDate(), w.getPurchaseDate())
                    || stored.getWarrantyMonths() != w.getWarrantyMonths();
            w.setNotified(!termsChanged && stored.isNotified());
            if (isBlank(w.getImageUrl()) && !isBlank(stored.getImageUrl())) w.setImageUrl(stored.getImageUrl());
        });
        return repo.save(w);
    }

    /** Imports {@code newReceipt}, saves, then deletes the previous receipt file if the app owns it. */
    public Warranty replaceReceipt(Warranty w, Path newReceipt) throws IOException {
        String previous = w.getReceiptImageUrl();
        String imported = files.importFile(newReceipt, FileStorage.Kind.RECEIPT);
        w.setReceiptImageUrl(imported);
        Warranty saved;
        try {
            saved = update(w);
        } catch (RuntimeException e) {
            w.setReceiptImageUrl(previous);
            files.deleteIfOwned(imported);
            throw e;
        }
        if (previous != null && !previous.equals(imported)) files.deleteIfOwned(previous);
        return saved;
    }

    /** Deletes the row and the image/receipt files the app owns for it. */
    public void delete(Warranty w) {
        Warranty stored = findById(w.getId()).orElse(w);
        if (w.getId() != null) repo.deleteById(w.getId());
        deleteFiles(stored.getImageUrl(), stored.getReceiptImageUrl(), w.getImageUrl(), w.getReceiptImageUrl());
    }

    private void deleteFiles(String... uris) {
        for (String uri : Arrays.stream(uris).filter(Objects::nonNull).distinct().toList()) {
            files.deleteIfOwned(uri);
        }
    }

    private String renderPreview(Path pdf) {
        return extractor.renderFirstPage(pdf).map(image -> {
            try {
                return files.savePng(image, FileStorage.Kind.IMAGE);
            } catch (IOException e) {
                log.warn("Could not save receipt preview image: {}", e.getMessage());
                return null;
            }
        }).orElse(null);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
