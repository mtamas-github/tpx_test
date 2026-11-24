import { randomUUID } from 'node:crypto';

const usage = 'Usage: node scripts/seed-urls.mjs [count=100] [base-url=http://localhost:3000]';
if (process.argv[2] === '--help') {
  console.log(usage);
  process.exit(0);
}

const count = Number(process.argv[2] ?? 100);
const baseUrl = (process.argv[3] ?? 'http://localhost:3000').replace(/\/+$/, '');
if (!Number.isSafeInteger(count) || count < 1 || process.argv.length > 4) {
  console.error(usage);
  console.error('Count must be a positive integer.');
  process.exit(1);
}

const runId = randomUUID().slice(0, 8);
let created = 0;

try {
  for (let i = 1; i <= count; i++) {
    const fullUrl = `https://example.com/?demo=${runId}&item=${i}`
      + (i % 3 === 0 ? '&description=a-long-sample-address-for-checking-table-truncation-and-layout' : '');
    const response = await fetch(`${baseUrl}/shorten`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ fullUrl, customAlias: `demo-${runId}-${i}` }),
      signal: AbortSignal.timeout(10_000),
    });
    const body = await response.text();
    if (response.status !== 201) {
      throw new Error(`HTTP ${response.status}: ${body}`);
    }
    created++;
    if (created % 25 === 0 || created === count) console.log(`Created ${created}/${count}`);
  }
  console.log(`Done. Refresh ${baseUrl} to see the demo-${runId}- URLs.`);
} catch (error) {
  console.error(`Stopped after ${created}/${count}: ${error.message}`);
  process.exitCode = 1;
}
