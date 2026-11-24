# Snip — URL Shortener

A URL shortener built with Java 17, Spring Boot, SQLite, and React with TypeScript. Create a generated or custom short link, browse saved URLs newest first, navigate between pages, and delete links.

The original assessment README is preserved unchanged in [README.original.md](README.original.md).

## Run with Docker

Requires Docker with Docker Compose. Run all commands below from the repository root unless stated otherwise.

```bash
docker compose up --build --wait
```

Open [http://localhost:3000](http://localhost:3000). The command starts the services in the background and waits for their health checks. The Docker build stages run the backend tests, frontend tests, and frontend production build; unchanged build layers can be reused.

Nginx serves the UI and proxies API requests to the Java service at `api:8080` on the Compose network. Use the public web address for both the UI and API. The API is not published on a separate host port.


Inspect service status and logs:

```bash
docker compose ps
docker compose logs -f
```

Stop and remove the containers while retaining saved URLs:

```bash
docker compose down
```

SQLite is stored in the named `url-data` volume, mounted at `/data` in the API container. Data survives container recreation and image rebuilds. A fresh volume starts empty; sample data is generated only when explicitly requested.

**To delete all saved URLs and start with an empty database**, remove the volume too:

```bash
docker compose down --volumes
```

## Development with Docker (automatic updates)

Requires Docker Compose 2.32 or newer. Start the development setup from the repository root:

```bash
docker compose -f docker-compose.dev.yml up --build --watch
```

Open [http://localhost:3100](http://localhost:3100) and keep the command running while editing. Java, Maven, Node, and npm stay inside containers. The first start downloads dependencies; Docker volumes cache them for later starts.

- React, TypeScript, and CSS edits are synchronized into Vite and reload in the browser.
- Java and backend resource edits are synchronized into the API container, which restarts and recompiles them. The API is briefly unavailable during this restart; refresh the page if a request overlaps it. A compilation error is printed in the terminal; fixing the file triggers another compilation.
- Changes to `pom.xml`, `package.json`, or `package-lock.json` restart the corresponding service and resolve its dependencies.
- Dockerfile or Compose configuration changes require restarting this command with `--build`.

The development project is named `snip-dev`, with its own database and dependency-cache volumes. It can run alongside the regular setup on port 3000; saved URLs are separate. Set `DEV_WEB_PORT` to choose another development web port.

In another terminal, add sample data or run tests inside the running development containers:

```bash
docker compose -f docker-compose.dev.yml exec web node /scripts/seed-urls.mjs 100 http://api:8080
docker compose -f docker-compose.dev.yml exec api mvn -B -ntp test
docker compose -f docker-compose.dev.yml exec web npm test
docker compose -f docker-compose.dev.yml exec web npm run build
```

Ctrl+C stops the foreground development session. To remove its containers and network while retaining its data and dependency caches:

```bash
docker compose -f docker-compose.dev.yml down
```

Adding `--volumes` to this development shutdown command deletes the development database and dependency caches. The regular setup's data volume is separate.

This workflow uses [Compose Watch](https://docs.docker.com/compose/how-tos/file-watch/) to synchronize edits and restart the API without rebuilding its image on every source change.

## Local development without Docker (optional)

Requires a Java 17 JDK, Maven, Node.js 18 or newer, and npm.

Start the backend in one terminal:

```bash
cd backend
mvn spring-boot:run
```

Start the frontend from the repository root in another terminal:

```bash
cd frontend
npm ci
npm run dev
```

Open [http://localhost:3000](http://localhost:3000). Vite proxies API and alias requests to `http://localhost:8080`. Stop the Docker web service first if it is using port 3000.

If port 8080 is occupied, use `SERVER_PORT=18080 mvn spring-boot:run` in the backend directory and `API_PROXY_TARGET=http://localhost:18080 npm run dev` in the frontend directory.

The backend creates `urlshortener.db` in its working directory by default. `DATABASE_PATH` can select another database file; `SERVER_PORT` controls its listening port. These are separate from `WEB_PORT`, which controls the public Docker web port.

## Tests

From the repository root, run the backend tests with Maven:

```bash
mvn -f backend/pom.xml test
```

Install the frontend's locked dependencies, run its tests, and check its production build:

```bash
npm --prefix frontend ci
npm --prefix frontend test
npm --prefix frontend run build
```

If using Docker only, force a fresh build to rerun the tests in both build stages:

```bash
docker compose build --no-cache
```

Database-backed tests use isolated in-memory SQLite databases. Coverage includes URL creation, deletion, alias collisions and retry limits, redirects, sorting, pagination boundaries, validation, form failure and retry, and UI page changes after creation or deletion. Tests do not use the application's saved URL database.

## Sample data for UI testing

With the application running, add 100 sample URLs using Node.js 18 or newer:

```bash
node scripts/seed-urls.mjs
```

Optionally specify a count and server address:

```bash
node scripts/seed-urls.mjs 500 http://localhost:3100
```

To run the script without Node installed on the host, use a temporary Node container:

```bash
docker run --rm \
  --network code-exercise-java-incomplete-public_default \
  -v "$PWD/scripts:/scripts:ro" \
  node:20-alpine \
  node /scripts/seed-urls.mjs 100 http://api:8080
```

This assumes the default Compose project name from the checkout directory, `code-exercise-java-incomplete-public`. If you rename the directory or use `docker compose -p`, replace the network name with `<project-name>_default`. The app must already be running.

The script calls `POST /shorten` sequentially, uses a random `demo-` alias prefix for each run, and creates a mixture of short and long destination URLs. Existing URLs are retained. If a request fails, it stops and reports how many were created. It never runs automatically during startup or builds.

Refresh the UI at its public address after generating data. In the Docker command, `api:8080` is the internal API address; the browser still uses [http://localhost:3000](http://localhost:3000), or the port selected with `WEB_PORT`.

The `.mjs` extension identifies a JavaScript ES module, allowing `import` and top-level `await` without a separate package configuration. The script uses Node's built-in modules and needs no npm installation.

## API

The examples use the default Docker address, `http://localhost:3000`. For direct backend development, use its configured port, normally 8080.

| Method | Path | Success | Behavior |
| --- | --- | --- | --- |
| `POST` | `/shorten` | `201 Created` | Save a URL and return its short link |
| `GET` | `/urls?page=0&size=10` | `200 OK` | Retrieve a page of URLs, newest first |
| `GET` | `/{alias}` | `302 Found` | Redirect to the saved destination |
| `DELETE` | `/{alias}` | `204 No Content` | Delete the matching URL |

### Create a short URL

```bash
curl -i http://localhost:3000/shorten \
  -H 'Content-Type: application/json' \
  -d '{"fullUrl":"https://example.com/articles/intro","customAlias":"my-alias"}'
```

The response contains the following JSON and a `Location` header pointing to the short URL:

```json
{
  "alias": "my-alias",
  "fullUrl": "https://example.com/articles/intro",
  "shortUrl": "http://localhost:3000/my-alias"
}
```

`fullUrl` must be an absolute HTTP or HTTPS URL with a host. Leading and trailing whitespace is removed, and a missing path becomes `/`. Omit `customAlias`, or send it blank, to request a generated alias. Custom aliases are case-sensitive and must contain 2–64 characters; use ASCII letters, digits, and hyphens for consistent routing through the UI and Nginx.

Invalid input or a taken custom alias returns `400 Bad Request`. If all 10 generated-alias attempts collide, the response is `503 Service Unavailable`.

### List URLs

```bash
curl 'http://localhost:3000/urls?page=0&size=10'
```

```json
{
  "items": [
    {
      "alias": "my-alias",
      "fullUrl": "https://example.com/articles/intro",
      "shortUrl": "http://localhost:3000/my-alias"
    }
  ],
  "page": 0,
  "size": 10,
  "totalItems": 1,
  "totalPages": 1
}
```

Pages are zero-based. Defaults are `page=0` and `size=10`. Page must be a nonnegative integer; size must be an integer between 1 and 100. Invalid parameters return `400`.

Items are sorted by creation time descending, with row ID descending as the tie-breaker. A page beyond the last page returns an empty `items` array with current totals. An empty database has `totalItems=0` and `totalPages=0`.

The UI displays page numbers starting at 1. Creating a URL returns to the first page. Deletion refreshes the current page to fill gaps, or moves back if the last page has disappeared.

### Redirect and delete

```bash
curl -i http://localhost:3000/my-alias
curl -i -X DELETE http://localhost:3000/my-alias
```

Redirects include the original URL in the `Location` header. Both operations return an empty `404 Not Found` response when the alias does not exist. Deleting the same alias again therefore returns `404`.

Application validation and alias errors use JSON bodies such as:

```json
{
  "error": "The alias 'my-alias' is already taken."
}
```

Other framework errors, such as parameter type-conversion errors, use Spring's default error format.

## Structure and design decisions

```text
backend/             Spring Boot API, JDBC repository, SQLite, backend tests
frontend/            React/TypeScript UI, Vite, Nginx, frontend tests
scripts/             Optional sample-data generator
docker-compose.yml   API, web service, health checks, persistent data volume
docker-compose.dev.yml Development containers with automatic source updates
README.original.md   Unchanged original assessment README
```

- **Existing architecture retained.** Controllers handle HTTP, the service owns validation and creation/deletion rules, and the repository executes parameterized SQL. SQLite and JDBC were already part of the starter implementation; they suit the exercise without introducing an ORM or separate database service.
- **Alias generation and uniqueness.** Generated aliases contain seven characters from `a–z`, `A–Z`, and `0–9`, selected with `ThreadLocalRandom`. The database's unique alias constraint is authoritative. `INSERT ... ON CONFLICT(alias) DO NOTHING` returns whether an insert succeeded, avoiding a separate availability check that could race with another request. Generated collisions retry up to 10 total attempts; a taken custom alias returns an error immediately. Aliases are public identifiers, not access secrets.
- **Deletion.** A single `DELETE ... WHERE alias = ?` removes the requested row. Its affected-row count determines whether the API returns `204` or `404`.
- **Sorting and pagination.** SQLite performs ordering and `LIMIT`/`OFFSET` pagination. Stored timestamps are UTC `Instant` strings; sorting removes the trailing `Z` for comparison so optional fractional seconds retain chronological order. An index supports this expression and the row-ID tie-breaker. Counts are returned separately so the UI can show totals and disable page controls.
- **Frontend state.** The hook owns API requests, page metadata, loading, and errors; the table receives rows and callbacks. Failed creation preserves form inputs. A request counter prevents older list responses from replacing newer pages.
- **Docker routing and storage.** Multi-stage builds keep build tools out of runtime images. Nginx gives the browser one origin for the UI, API, and redirects. Spring honors Nginx's forwarded host and scheme when building public short URLs. Health checks order startup, the Java process runs as a non-root user, and a named volume preserves SQLite data.

## Assumptions and known limitations

- This is a public assessment application. It has no authentication, ownership checks, rate limiting, or link expiration; anyone with an alias can request its deletion.
- The UI and Nginx use ASCII alias rules, while backend validation currently also accepts Unicode letters and digits. Validation should be aligned, and application route names such as `urls` should be reserved before broadening usage.
- URL normalization rebuilds URI components. Percent-encoded path and query separators need additional handling and regression coverage to ensure destination semantics are preserved.
- Offset pagination is sufficient for this exercise. Concurrent additions or deletions can move items between pages, and the count and page queries do not provide a shared snapshot. A larger or busier deployment could use cursor pagination and stronger consistency guarantees.
