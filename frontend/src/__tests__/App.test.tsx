import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import App from '../App';
import { api } from '../services/api';
import type { UrlListItem, UrlPage } from '../types/api';

function makePage(items: UrlListItem[], page = 0, totalItems = items.length): UrlPage {
  return { items, page, size: 10, totalItems, totalPages: Math.ceil(totalItems / 10) };
}

const urls = Array.from({ length: 11 }, (_, i) => ({
  alias: `url-${i}`,
  fullUrl: `https://example.com/${i}`,
  shortUrl: `http://localhost:8080/url-${i}`,
}));

afterEach(() => {
  vi.restoreAllMocks();
});

describe('URL creation', () => {
  it('preserves inputs after failure and clears them after a successful retry', async () => {
    const created = {
      alias: 'available',
      fullUrl: 'https://example.com/test',
      shortUrl: 'http://localhost:8080/available',
    };
    vi.spyOn(api, 'list').mockResolvedValueOnce(makePage([])).mockResolvedValueOnce(makePage([created]));
    const shorten = vi.spyOn(api, 'shorten')
      .mockRejectedValueOnce(new Error("The alias 'taken' is already taken."))
      .mockResolvedValueOnce(created);
    const user = userEvent.setup();
    render(<App />);

    await screen.findByText(/no shortened urls yet/i);
    const urlInput = screen.getByLabelText(/long url/i);
    const aliasInput = screen.getByLabelText(/custom alias/i);
    const submit = screen.getByRole('button', { name: /shorten url/i });
    await user.type(urlInput, 'https://example.com/test');
    await user.type(aliasInput, 'taken');
    await user.click(submit);

    expect(await screen.findByRole('alert')).toHaveTextContent("The alias 'taken' is already taken.");
    expect(urlInput).toHaveValue('https://example.com/test');
    expect(aliasInput).toHaveValue('taken');
    expect(submit).toBeEnabled();

    await user.clear(aliasInput);
    await user.type(aliasInput, 'available');
    await user.click(submit);

    await waitFor(() => {
      expect(urlInput).toHaveValue('');
      expect(aliasInput).toHaveValue('');
    });
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    expect(await screen.findByRole('button', { name: 'Delete available' })).toBeInTheDocument();
    expect(shorten).toHaveBeenNthCalledWith(1, {
      fullUrl: 'https://example.com/test', customAlias: 'taken',
    });
    expect(shorten).toHaveBeenNthCalledWith(2, {
      fullUrl: 'https://example.com/test', customAlias: 'available',
    });
  });
});

describe('URL pagination', () => {
  it('navigates between pages and disables controls at the boundaries', async () => {
    const list = vi.spyOn(api, 'list').mockImplementation(async (page) =>
      page === 1 ? makePage(urls.slice(10), 1, 11) : makePage(urls.slice(0, 10), 0, 11));
    const user = userEvent.setup();
    render(<App />);

    await screen.findByRole('button', { name: 'Delete url-0' });
    expect(screen.getByText('Page 1 of 2')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Previous' })).toBeDisabled();

    await user.click(screen.getByRole('button', { name: 'Next' }));
    await screen.findByRole('button', { name: 'Delete url-10' });
    expect(screen.getByText('Page 2 of 2')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Next' })).toBeDisabled();
    expect(screen.queryByRole('button', { name: 'Delete url-0' })).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Previous' }));
    await screen.findByRole('button', { name: 'Delete url-0' });
    expect(screen.getByText('Page 1 of 2')).toBeInTheDocument();
    expect(list).toHaveBeenCalledWith(1, 10);
  });

  it('returns to the previous page when its last URL is deleted', async () => {
    const list = vi.spyOn(api, 'list')
      .mockResolvedValueOnce(makePage(urls.slice(0, 10), 0, 11))
      .mockResolvedValueOnce(makePage(urls.slice(10), 1, 11))
      .mockResolvedValueOnce(makePage([], 1, 10))
      .mockResolvedValueOnce(makePage(urls.slice(0, 10), 0, 10));
    const deleteUrl = vi.spyOn(api, 'delete').mockResolvedValue(undefined);
    const user = userEvent.setup();
    render(<App />);

    await screen.findByRole('button', { name: 'Delete url-0' });
    await user.click(screen.getByRole('button', { name: 'Next' }));
    await user.click(await screen.findByRole('button', { name: 'Delete url-10' }));

    await screen.findByRole('button', { name: 'Delete url-0' });
    expect(screen.getByText('Page 1 of 1')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Next' })).toBeDisabled();
    expect(deleteUrl).toHaveBeenCalledWith('url-10');
    expect(list).toHaveBeenNthCalledWith(3, 1, 10);
    expect(list).toHaveBeenNthCalledWith(4, 0, 10);
  });

  it('refills the current page after deletion', async () => {
    vi.spyOn(api, 'list')
      .mockResolvedValueOnce(makePage(urls.slice(0, 10), 0, 11))
      .mockResolvedValueOnce(makePage(urls.slice(1), 0, 10));
    vi.spyOn(api, 'delete').mockResolvedValue(undefined);
    const user = userEvent.setup();
    render(<App />);

    await user.click(await screen.findByRole('button', { name: 'Delete url-0' }));

    await screen.findByRole('button', { name: 'Delete url-10' });
    expect(screen.getByText('Page 1 of 1')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Delete url-0' })).not.toBeInTheDocument();
  });

  it('returns to the first page after creating a URL', async () => {
    const created = {
      alias: 'new-link', fullUrl: 'https://example.com/new', shortUrl: 'http://localhost:8080/new-link',
    };
    const list = vi.spyOn(api, 'list')
      .mockResolvedValueOnce(makePage(urls.slice(0, 10), 0, 11))
      .mockResolvedValueOnce(makePage(urls.slice(10), 1, 11))
      .mockResolvedValueOnce(makePage([created, ...urls.slice(0, 9)], 0, 12));
    vi.spyOn(api, 'shorten').mockResolvedValue(created);
    const user = userEvent.setup();
    render(<App />);

    await screen.findByRole('button', { name: 'Delete url-0' });
    await user.click(screen.getByRole('button', { name: 'Next' }));
    await screen.findByRole('button', { name: 'Delete url-10' });
    await user.type(screen.getByLabelText(/long url/i), created.fullUrl);
    await user.type(screen.getByLabelText(/custom alias/i), created.alias);
    await user.click(screen.getByRole('button', { name: 'Shorten URL' }));

    await screen.findByRole('button', { name: 'Delete new-link' });
    expect(screen.getByText('Page 1 of 2')).toBeInTheDocument();
    expect(list).toHaveBeenLastCalledWith(0, 10);
    expect(screen.getByLabelText(/long url/i)).toHaveValue('');
  });

  it('keeps the current page when loading the next page fails', async () => {
    vi.spyOn(api, 'list')
      .mockResolvedValueOnce(makePage(urls.slice(0, 10), 0, 11))
      .mockRejectedValueOnce(new Error('Unable to load the next page.'));
    const user = userEvent.setup();
    render(<App />);

    await screen.findByRole('button', { name: 'Delete url-0' });
    await user.click(screen.getByRole('button', { name: 'Next' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Unable to load the next page.');
    expect(screen.getByText('Page 1 of 2')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Delete url-0' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Next' })).toBeEnabled();
  });
});
