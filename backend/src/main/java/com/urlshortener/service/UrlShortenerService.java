package com.urlshortener.service;

import com.urlshortener.exception.AliasGenerationException;
import com.urlshortener.model.ShortenUrlRequest;
import com.urlshortener.model.ShortenUrlResponse;
import com.urlshortener.model.UrlPage;
import com.urlshortener.repository.ShortenedUrlRepository;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class UrlShortenerService {

    private static final String ALIAS_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int GENERATED_ALIAS_LENGTH = 7;
    private static final int MAX_ALIAS_GENERATION_ATTEMPTS = 10;

    private final ShortenedUrlRepository repository;

    public UrlShortenerService(ShortenedUrlRepository repository) {
        this.repository = repository;
    }

    public ShortenUrlResponse shorten(ShortenUrlRequest request, String baseUrl) {
        var fullUrl = normalizeUrl(request.getFullUrl());
        var customAlias = request.getCustomAlias();
        var generate = customAlias == null || customAlias.isBlank();

        if (!generate) {
            customAlias = customAlias.trim();
            if (!isValidAlias(customAlias)) {
                throw new IllegalArgumentException("Alias may only contain letters, numbers, and hyphens (2–64 characters).");
            }
        }

        var maxAttempts = generate ? MAX_ALIAS_GENERATION_ATTEMPTS : 1;
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            var alias = generate ? generateAlias() : customAlias;
            if (repository.save(alias, fullUrl, Instant.now())) {
                return new ShortenUrlResponse(baseUrl + "/" + alias, alias, fullUrl);
            }

            if (!generate) {
                throw new IllegalStateException("The alias '" + alias + "' is already taken.");
            }
        }

        throw new AliasGenerationException("Unable to generate an available alias. Please try again.");
    }

    public String getFullUrl(String alias) {
        return repository.findFullUrlByAlias(alias).orElse(null);
    }

    public UrlPage getPage(String baseUrl, int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("page must be 0 or greater.");
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("size must be between 1 and 100.");
        }

        var totalItems = repository.count();
        var items = repository.findPage(baseUrl, size, (long) page * size);
        var totalPages = (totalItems + size - 1) / size;
        return new UrlPage(items, page, size, totalItems, totalPages);
    }

    public boolean delete(String alias) {
        return repository.deleteByAlias(alias);
    }

    private static String generateAlias() {
        var alias = new StringBuilder(GENERATED_ALIAS_LENGTH);
        for (int i = 0; i < GENERATED_ALIAS_LENGTH; i++) {
            int index = ThreadLocalRandom.current().nextInt(ALIAS_CHARS.length());
            alias.append(ALIAS_CHARS.charAt(index));
        }
        return alias.toString();
    }

    private static boolean isValidAlias(String alias) {
        if (alias == null || alias.length() < 2 || alias.length() > 64) {
            return false;
        }
        return alias.chars().allMatch(c -> Character.isLetterOrDigit(c) || c == '-');
    }

    private static String normalizeUrl(String fullUrl) {
        if (fullUrl == null || fullUrl.isBlank()) {
            throw new IllegalArgumentException("fullUrl is required.");
        }

        try {
            var uri = new URI(fullUrl.trim());
            if (uri.getScheme() == null || uri.getHost() == null) {
                throw new IllegalArgumentException("fullUrl must be a valid URL.");
            }

            var scheme = uri.getScheme().toLowerCase();
            if (!scheme.equals("http") && !scheme.equals("https")) {
                throw new IllegalArgumentException("fullUrl must be a valid URL.");
            }

            var path = uri.getPath();
            if (path == null || path.isBlank()) {
                path = "/";
            }

            return new URI(
                    uri.getScheme(),
                    uri.getUserInfo(),
                    uri.getHost(),
                    uri.getPort(),
                    path,
                    uri.getQuery(),
                    uri.getFragment()
            ).toString();
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException("fullUrl must be a valid URL.");
        }
    }
}
