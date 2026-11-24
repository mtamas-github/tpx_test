package com.urlshortener;

import com.fasterxml.jackson.databind.JsonNode;
import com.urlshortener.model.ShortenUrlRequest;
import com.urlshortener.model.ShortenUrlResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.FOUND;

@SpringBootTest(webEnvironment = RANDOM_PORT)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:sqlite::memory:",
        "spring.datasource.driver-class-name=org.sqlite.JDBC",
        "spring.datasource.hikari.maximum-pool-size=1"
})
class UrlsControllerIntegrationTests {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DELETE FROM shortened_urls");
        jdbcTemplate.update(
                "INSERT INTO shortened_urls (alias, full_url, created_at) VALUES (?, ?, ?)",
                "redir",
                "https://target.com/",
                Instant.now().toString()
        );
    }

    @Test
    void shorten_WithoutCustomAlias_Returns201WithGeneratedAliasAndPersistsUrl() {
        var request = new ShortenUrlRequest();
        request.setFullUrl("https://example.com/test");

        var response = restTemplate.postForEntity("/shorten", request, ShortenUrlResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(CREATED);
        var body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getAlias()).matches("[a-zA-Z0-9]{7}");
        assertThat(body.getFullUrl()).isEqualTo("https://example.com/test");
        assertThat(body.getShortUrl()).isEqualTo(restTemplate.getRootUri() + "/" + body.getAlias());
        assertThat(response.getHeaders().getLocation()).hasToString(body.getShortUrl());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT full_url FROM shortened_urls WHERE alias = ?",
                String.class,
                body.getAlias()
        )).isEqualTo("https://example.com/test");
    }

    @Test
    void shorten_WithCustomAlias_Returns201WithUrlDetailsAndPersistsUrl() {
        var request = new ShortenUrlRequest();
        request.setFullUrl("https://example.com/test");
        request.setCustomAlias("my-alias");

        var response = restTemplate.postForEntity("/shorten", request, ShortenUrlResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(CREATED);
        var body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getAlias()).isEqualTo("my-alias");
        assertThat(body.getFullUrl()).isEqualTo("https://example.com/test");
        assertThat(body.getShortUrl()).isEqualTo(restTemplate.getRootUri() + "/my-alias");
        assertThat(response.getHeaders().getLocation()).hasToString(body.getShortUrl());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT full_url FROM shortened_urls WHERE alias = ?",
                String.class,
                "my-alias"
        )).isEqualTo("https://example.com/test");
    }

    @Test
    void shorten_WithBlankUrl_Returns400() {
        var request = new ShortenUrlRequest();
        request.setFullUrl(" ");
        request.setCustomAlias("my-alias");

        var response = restTemplate.postForEntity("/shorten", request, JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST);
        assertThat(response.getBody().path("error").asText()).isEqualTo("fullUrl is required.");
    }

    @Test
    void shorten_WithInvalidUrl_Returns400() {
        var request = new ShortenUrlRequest();
        request.setFullUrl("not-a-url");
        request.setCustomAlias("my-alias");

        var response = restTemplate.postForEntity("/shorten", request, JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST);
        assertThat(response.getBody().path("error").asText()).isEqualTo("fullUrl must be a valid URL.");
    }

    @Test
    void shorten_WithExistingAlias_Returns400AndPreservesExistingUrl() {
        var request = new ShortenUrlRequest();
        request.setFullUrl("https://example.com/test");
        request.setCustomAlias("redir");

        var response = restTemplate.postForEntity("/shorten", request, JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST);
        assertThat(response.getBody().path("error").asText()).isEqualTo("The alias 'redir' is already taken.");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT full_url FROM shortened_urls WHERE alias = ?",
                String.class,
                "redir"
        )).isEqualTo("https://target.com/");
    }

    @Test
    void getAlias_ExistingAlias_Returns302WithLocation() {
        var response = restTemplate.getForEntity("/redir", String.class);

        assertThat(response.getStatusCode()).isEqualTo(FOUND);
        assertThat(response.getHeaders().getLocation()).hasToString("https://target.com/");
    }
}
