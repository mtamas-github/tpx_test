package com.urlshortener.repository;

import com.urlshortener.model.UrlListItem;
import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class ShortenedUrlRepository {

    private final JdbcTemplate jdbc;

    public ShortenedUrlRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostConstruct
    public void init() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS shortened_urls ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "alias TEXT NOT NULL UNIQUE, "
                + "full_url TEXT NOT NULL, "
                + "created_at TEXT NOT NULL"
                + ")");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_shortened_urls_created_at "
                + "ON shortened_urls (rtrim(created_at, 'Z') DESC, id DESC)");
    }

    public boolean save(String alias, String fullUrl, Instant createdAt) {
        var rows = jdbc.update("INSERT INTO shortened_urls (alias, full_url, created_at) VALUES (?, ?, ?) "
                        + "ON CONFLICT(alias) DO NOTHING",
                alias, fullUrl, createdAt.toString());
        return rows > 0;
    }

    public Optional<String> findFullUrlByAlias(String alias) {
        var results = jdbc.query("SELECT full_url FROM shortened_urls WHERE alias = ? LIMIT 1",
                (rs, rowNum) -> rs.getString("full_url"),
                alias);
        return results.stream().findFirst();
    }

    public long count() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM shortened_urls", Long.class);
    }

    public List<UrlListItem> findPage(String baseUrl, int size, long offset) {
        // Instant's optional fractional seconds sort correctly after removing the UTC suffix.
        return jdbc.query("SELECT alias, full_url FROM shortened_urls "
                        + "ORDER BY rtrim(created_at, 'Z') DESC, id DESC LIMIT ? OFFSET ?",
                urlListItemMapper(baseUrl), size, offset);
    }

    public boolean deleteByAlias(String alias) {
        var rows = jdbc.update("DELETE FROM shortened_urls WHERE alias = ?", alias);
        return rows > 0;
    }

    private RowMapper<UrlListItem> urlListItemMapper(String baseUrl) {
        return (ResultSet rs, int rowNum) -> new UrlListItem(
                rs.getString("alias"),
                rs.getString("full_url"),
                baseUrl + "/" + rs.getString("alias")
        );
    }
}
