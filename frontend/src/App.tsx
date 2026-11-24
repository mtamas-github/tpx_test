import { useUrlShortener } from './hooks/useUrlShortener';
import { ShortenForm } from './components/ShortenForm';
import { ResultBanner } from './components/ResultBanner';
import { UrlTable } from './components/UrlTable';

export default function App() {
  const {
    urls, page, totalItems, totalPages, loading, error, lastCreated,
    shorten, deleteUrl, goToPage, clearLastCreated,
  } = useUrlShortener();

  return (
    <div className="app">
      <header className="app-header">
        <div className="app-header__inner">
          <div className="logo">
            <span className="logo__mark" aria-hidden="true">⌁</span>
            <span className="logo__name">Snip</span>
          </div>
          <p className="tagline">Long URLs, made short.</p>
        </div>
      </header>

      <main className="app-main">
        <section className="card shorten-card" aria-labelledby="shorten-heading">
          <h2 id="shorten-heading" className="card__title">
            Shorten a URL
          </h2>
          <ShortenForm onSubmit={shorten} disabled={loading} />
        </section>

        {lastCreated && (
          <ResultBanner result={lastCreated} onDismiss={clearLastCreated} />
        )}

        {error && (
          <div className="banner banner--error" role="alert">
            <strong>Error:</strong> {error}
          </div>
        )}

        <section className="card" aria-labelledby="list-heading">
          <h2 id="list-heading" className="card__title">
            All shortened URLs
            {totalItems > 0 && (
              <span className="badge">{totalItems}</span>
            )}
          </h2>

          {loading ? (
            <div className="loading" aria-live="polite" aria-busy="true">
              Loading…
            </div>
          ) : (
            <UrlTable urls={urls} onDelete={deleteUrl} />
          )}

          {totalPages > 0 && (
            <nav className="pagination" aria-label="URL pagination">
              <button
                type="button"
                className="btn-secondary"
                disabled={loading || page === 0}
                onClick={() => void goToPage(page - 1)}
              >
                Previous
              </button>
              <span aria-live="polite">Page {page + 1} of {totalPages}</span>
              <button
                type="button"
                className="btn-secondary"
                disabled={loading || page + 1 >= totalPages}
                onClick={() => void goToPage(page + 1)}
              >
                Next
              </button>
            </nav>
          )}
        </section>
      </main>

      <footer className="app-footer">
        <p>Snip URL Shortener</p>
      </footer>
    </div>
  );
}
