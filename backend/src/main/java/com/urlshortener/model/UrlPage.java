package com.urlshortener.model;

import java.util.List;

public record UrlPage(List<UrlListItem> items, int page, int size, long totalItems, long totalPages) {
}
