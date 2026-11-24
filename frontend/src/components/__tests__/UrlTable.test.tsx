import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { UrlTable } from '../UrlTable';
import type { UrlListItem } from '../../types/api';

const mockUrls: UrlListItem[] = [
  { alias: 'abc', fullUrl: 'https://example.com', shortUrl: 'http://localhost:8080/abc' },
  { alias: 'def', fullUrl: 'https://another.com/path', shortUrl: 'http://localhost:8080/def' },
];

describe('UrlTable', () => {
  it('shows empty state when there are no URLs', () => {
    render(<UrlTable urls={[]} onDelete={vi.fn()} />);
    expect(screen.getByText(/no shortened urls yet/i)).toBeInTheDocument();
  });

  it('renders a row for each URL', () => {
    render(<UrlTable urls={mockUrls} onDelete={vi.fn()} />);
    expect(screen.getByText('http://localhost:8080/abc')).toBeInTheDocument();
    expect(screen.getByText('http://localhost:8080/def')).toBeInTheDocument();
    expect(screen.getByText('https://example.com')).toBeInTheDocument();
    expect(screen.getByText('https://another.com/path')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'http://localhost:8080/abc' }))
      .toHaveAttribute('href', 'http://localhost:8080/abc');
  });

  it('calls onDelete with the alias when delete is clicked', () => {
    const onDelete = vi.fn();
    render(<UrlTable urls={mockUrls} onDelete={onDelete} />);
    expect(onDelete).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole('button', { name: 'Delete abc' }));
    fireEvent.click(screen.getByRole('button', { name: 'Delete def' }));

    expect(onDelete).toHaveBeenCalledTimes(2);
    expect(onDelete).toHaveBeenNthCalledWith(1, 'abc');
    expect(onDelete).toHaveBeenNthCalledWith(2, 'def');
  });
});
