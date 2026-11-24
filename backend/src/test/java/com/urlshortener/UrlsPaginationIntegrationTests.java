package com.urlshortener;

import com.fasterxml.jackson.databind.JsonNode;
import com.urlshortener.model.UrlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.OK;

@SpringBootTest(webEnvironment = RANDOM_PORT)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:sqlite::memory:",
        "spring.datasource.driver-class-name=org.sqlite.JDBC",
        "spring.datasource.hikari.maximum-pool-size=1"
})
class UrlsPaginationIntegrationTests {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DELETE FROM shortened_urls");
    }

    @Test
    void getUrls_ReturnsNewestFirstAcrossPagesWithStableTies() {
        insert("middle", "2026-01-02T00:00:00Z");
        insert("oldest", "2026-01-01T00:00:00Z");
        insert("newest", "2026-01-03T00:00:00Z");
        insert("tie-first", "2026-01-02T12:00:00Z");
        insert("tie-second", "2026-01-02T12:00:00Z");

        var first = getPage(0, 2);
        var second = getPage(1, 2);
        var last = getPage(2, 2);

        assertThat(first.items()).extracting("alias").containsExactly("newest", "tie-second");
        assertThat(second.items()).extracting("alias").containsExactly("tie-first", "middle");
        assertThat(last.items()).extracting("alias").containsExactly("oldest");
        assertThat(first.page()).isZero();
        assertThat(second.page()).isEqualTo(1);
        assertThat(last.page()).isEqualTo(2);
        assertThat(first.size()).isEqualTo(2);
        assertThat(first.totalItems()).isEqualTo(5);
        assertThat(first.totalPages()).isEqualTo(3);
        assertThat(first.items().get(0).getShortUrl()).isEqualTo(restTemplate.getRootUri() + "/newest");
    }

    @Test
    void getUrls_OrdersWholeAndFractionalSecondTimestampsCorrectly() {
        insert("newest", "2026-01-01T00:00:00.123456790Z");
        insert("whole-second", "2026-01-01T00:00:00Z");
        insert("older-fraction", "2026-01-01T00:00:00.123456789Z");

        assertThat(getPage(0, 10).items()).extracting("alias")
                .containsExactly("newest", "older-fraction", "whole-second");
    }

    @Test
    void getUrls_WithoutParameters_DefaultsToTenItemsOnFirstPage() {
        for (int i = 0; i < 11; i++) {
            insert("item-" + i, Instant.parse("2026-01-01T00:00:00Z").plusSeconds(i).toString());
        }

        var response = restTemplate.getForEntity("/urls", UrlPage.class);

        assertThat(response.getStatusCode()).isEqualTo(OK);
        var page = response.getBody();
        assertThat(page).isNotNull();
        assertThat(page.page()).isZero();
        assertThat(page.size()).isEqualTo(10);
        assertThat(page.items()).hasSize(10);
        assertThat(page.items().get(0).getAlias()).isEqualTo("item-10");
        assertThat(page.totalItems()).isEqualTo(11);
        assertThat(page.totalPages()).isEqualTo(2);
    }

    @Test
    void getUrls_WithEmptyDatabase_ReturnsEmptyPage() {
        var page = getPage(0, 10);

        assertThat(page.items()).isEmpty();
        assertThat(page.totalItems()).isZero();
        assertThat(page.totalPages()).isZero();
    }

    @Test
    void getUrls_BeyondLastPage_ReturnsEmptyItemsWithoutOffsetOverflow() {
        insert("existing", "2026-01-01T00:00:00Z");

        var page = getPage(Integer.MAX_VALUE, 100);

        assertThat(page.items()).isEmpty();
        assertThat(page.page()).isEqualTo(Integer.MAX_VALUE);
        assertThat(page.totalItems()).isEqualTo(1);
        assertThat(page.totalPages()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "page=-1", "size=0", "size=101", "page=abc", "size=abc", "page=2147483648"
    })
    void getUrls_WithInvalidParameters_Returns400(String query) {
        var response = restTemplate.getForEntity("/urls?" + query, JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST);
    }

    private UrlPage getPage(int page, int size) {
        var response = restTemplate.getForEntity("/urls?page=" + page + "&size=" + size, UrlPage.class);
        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }

    private void insert(String alias, String createdAt) {
        jdbcTemplate.update("INSERT INTO shortened_urls (alias, full_url, created_at) VALUES (?, ?, ?)",
                alias, "https://example.com/" + alias, createdAt);
    }
}
