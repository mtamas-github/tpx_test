import { useState, useCallback, useEffect, useRef } from 'react';
import { api } from '../services/api';
import type { ShortenUrlResponse, UrlListItem, UrlPage } from '../types/api';

const PAGE_SIZE = 10;

interface UseUrlShortenerReturn {
  urls: UrlListItem[];
  page: number;
  totalItems: number;
  totalPages: number;
  loading: boolean;
  error: string | null;
  lastCreated: ShortenUrlResponse | null;
  shorten: (fullUrl: string, customAlias?: string) => Promise<boolean>;
  deleteUrl: (alias: string) => Promise<void>;
  goToPage: (page: number) => Promise<void>;
  clearLastCreated: () => void;
}

export function useUrlShortener(): UseUrlShortenerReturn {
  const [urlPage, setUrlPage] = useState<UrlPage>({
    items: [], page: 0, size: PAGE_SIZE, totalItems: 0, totalPages: 0,
  });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [lastCreated, setLastCreated] = useState<ShortenUrlResponse | null>(null);
  const latestRequest = useRef(0);

  const fetchPage = useCallback(async (page: number) => {
    const request = ++latestRequest.current;
    setLoading(true);
    setError(null);
    try {
      let data = await api.list(page, PAGE_SIZE);
      if (request !== latestRequest.current) return;
      if (data.page > 0 && data.page >= data.totalPages) {
        data = await api.list(Math.max(0, data.totalPages - 1), PAGE_SIZE);
      }
      if (request === latestRequest.current) setUrlPage(data);
    } catch (e) {
      if (request === latestRequest.current) {
        setError(e instanceof Error ? e.message : 'Failed to load URLs.');
      }
    } finally {
      if (request === latestRequest.current) setLoading(false);
    }
  }, []);

  useEffect(() => {
    void fetchPage(0);
    return () => { latestRequest.current++; };
  }, [fetchPage]);

  const shorten = useCallback(async (fullUrl: string, customAlias?: string) => {
    latestRequest.current++;
    setError(null);
    setLastCreated(null);
    setLoading(true);
    try {
      const result = await api.shorten({ fullUrl, customAlias: customAlias || undefined });
      setLastCreated(result);
      await fetchPage(0);
      return true;
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to shorten URL.');
      return false;
    } finally {
      setLoading(false);
    }
  }, [fetchPage]);

  const deleteUrl = useCallback(async (alias: string) => {
    latestRequest.current++;
    setError(null);
    setLoading(true);
    try {
      await api.delete(alias);
      await fetchPage(urlPage.page);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to delete URL.');
    } finally {
      setLoading(false);
    }
  }, [fetchPage, urlPage.page]);

  const clearLastCreated = useCallback(() => setLastCreated(null), []);

  return {
    urls: urlPage.items, page: urlPage.page,
    totalItems: urlPage.totalItems, totalPages: urlPage.totalPages,
    loading, error, lastCreated, shorten, deleteUrl,
    goToPage: fetchPage, clearLastCreated,
  };
}
