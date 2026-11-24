package com.urlshortener;

import com.urlshortener.controller.UrlsController;
import com.urlshortener.exception.ApiExceptionHandler;
import com.urlshortener.model.ShortenUrlRequest;
import com.urlshortener.repository.ShortenedUrlRepository;
import com.urlshortener.service.UrlShortenerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UrlShortenerAliasTests {

    private ShortenedUrlRepository repository;
    private UrlShortenerService service;
    private ShortenUrlRequest request;

    @BeforeEach
    void setUp() {
        repository = mock(ShortenedUrlRepository.class);
        service = new UrlShortenerService(repository);
        request = new ShortenUrlRequest();
        request.setFullUrl("https://example.com/test");
    }

    @Test
    void shorten_WhenGeneratedAliasCollides_RetriesAndReturnsSavedAlias() {
        when(repository.save(anyString(), anyString(), any(Instant.class))).thenReturn(false, true);

        var response = service.shorten(request, "http://localhost:8080");

        var aliases = ArgumentCaptor.forClass(String.class);
        verify(repository, times(2)).save(aliases.capture(), eq("https://example.com/test"), any(Instant.class));
        assertTrue(aliases.getAllValues().get(0).matches("[a-zA-Z0-9]{7}"));
        assertTrue(aliases.getAllValues().get(1).matches("[a-zA-Z0-9]{7}"));
        assertEquals(aliases.getAllValues().get(1), response.getAlias());
        assertEquals("http://localhost:8080/" + response.getAlias(), response.getShortUrl());
    }

    @Test
    void shorten_WhenAllAttemptsCollide_Returns503AfterTenAttempts() throws Exception {
        when(repository.save(anyString(), anyString(), any(Instant.class))).thenReturn(false);
        var mockMvc = MockMvcBuilders.standaloneSetup(new UrlsController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();

        mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullUrl\":\"https://example.com/test\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("Unable to generate an available alias. Please try again."));

        verify(repository, times(10)).save(anyString(), eq("https://example.com/test"), any(Instant.class));
    }

    @Test
    void shorten_WhenCustomAliasIsTaken_DoesNotRetry() {
        request.setCustomAlias("taken");
        when(repository.save(anyString(), anyString(), any(Instant.class))).thenReturn(false);

        var exception = assertThrows(IllegalStateException.class,
                () -> service.shorten(request, "http://localhost:8080"));

        assertEquals("The alias 'taken' is already taken.", exception.getMessage());
        verify(repository).save(eq("taken"), eq("https://example.com/test"), any(Instant.class));
        verifyNoMoreInteractions(repository);
    }

    @Test
    void shorten_WhenDatabaseFails_DoesNotRetryAsCollision() {
        var databaseError = new DataAccessResourceFailureException("Database unavailable");
        when(repository.save(anyString(), anyString(), any(Instant.class))).thenThrow(databaseError);

        var exception = assertThrows(DataAccessResourceFailureException.class,
                () -> service.shorten(request, "http://localhost:8080"));

        assertSame(databaseError, exception);
        verify(repository).save(anyString(), eq("https://example.com/test"), any(Instant.class));
        verifyNoMoreInteractions(repository);
    }
}
