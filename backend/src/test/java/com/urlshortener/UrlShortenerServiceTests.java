package com.urlshortener;

import com.urlshortener.model.ShortenUrlRequest;
import com.urlshortener.model.ShortenUrlResponse;
import com.urlshortener.repository.ShortenedUrlRepository;
import com.urlshortener.service.UrlShortenerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import static org.junit.jupiter.api.Assertions.*;

public class UrlShortenerServiceTests {

    private static final String BASE_URL = "http://localhost:8080";
    private UrlShortenerService service;

    @BeforeEach
    void setUp() {
        var dataSource = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
        var jdbcTemplate = new JdbcTemplate(dataSource);
        var repository = new ShortenedUrlRepository(jdbcTemplate);
        repository.init();
        service = new UrlShortenerService(repository);
    }

    @Test
    void shorten_WithValidUrl_ReturnsShortUrlResponse() {
        var request = new ShortenUrlRequest();
        request.setFullUrl("https://example.com/test");

        ShortenUrlResponse response = service.shorten(request, BASE_URL);

        assertTrue(response.getAlias().matches("[a-zA-Z0-9]{7}"));
        assertEquals("https://example.com/test", response.getFullUrl());
        assertEquals(BASE_URL + "/" + response.getAlias(), response.getShortUrl());
        assertEquals("https://example.com/test", service.getFullUrl(response.getAlias()));
    }

    @Test
    void shorten_WithBlankAlias_GeneratesAlias() {
        var request = new ShortenUrlRequest();
        request.setFullUrl("https://example.com/test");
        request.setCustomAlias("   ");

        var response = service.shorten(request, BASE_URL);

        assertTrue(response.getAlias().matches("[a-zA-Z0-9]{7}"));
        assertEquals("https://example.com/test", service.getFullUrl(response.getAlias()));
    }

    @Test
    void shorten_WithCustomAlias_ReturnsCustomAlias() {
        var request = new ShortenUrlRequest();
        request.setFullUrl("https://example.com");
        request.setCustomAlias("my-alias");

        ShortenUrlResponse response = service.shorten(request, BASE_URL);

        assertEquals("my-alias", response.getAlias());
        assertEquals(BASE_URL + "/my-alias", response.getShortUrl());
    }

    @Test
    void getFullUrl_ReturnsNormalizedUrl() {
        var request = new ShortenUrlRequest();
        request.setFullUrl("https://example.com");
        request.setCustomAlias("example");
        service.shorten(request, BASE_URL);

        String fullUrl = service.getFullUrl("example");

        assertEquals("https://example.com/", fullUrl);
    }

    @Test
    void delete_WithExistingAlias_RemovesUrlAndReturnsTrue() {
        var request = new ShortenUrlRequest();
        request.setFullUrl("https://example.com/test");
        request.setCustomAlias("example");
        service.shorten(request, BASE_URL);

        assertTrue(service.delete("example"));

        assertNull(service.getFullUrl("example"));
        assertTrue(service.getPage(BASE_URL, 0, 10).items().isEmpty());
        assertFalse(service.delete("example"));
    }

    @Test
    void delete_WithMissingAlias_ReturnsFalse() {
        assertFalse(service.delete("missing"));
    }

    @Test
    void delete_WithExistingDeletedSuffixAlias_LeavesOtherUrlUnchanged() {
        var request = new ShortenUrlRequest();
        request.setFullUrl("https://example.com/test");
        request.setCustomAlias("example");
        service.shorten(request, BASE_URL);

        var otherRequest = new ShortenUrlRequest();
        otherRequest.setFullUrl("https://other.com/test");
        otherRequest.setCustomAlias("example-deleted");
        service.shorten(otherRequest, BASE_URL);

        assertTrue(service.delete("example"));

        assertNull(service.getFullUrl("example"));
        assertEquals("https://other.com/test", service.getFullUrl("example-deleted"));
        assertEquals(1, service.getPage(BASE_URL, 0, 10).totalItems());
    }
}
