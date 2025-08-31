#!/usr/bin/env node
'use strict';

/**
 * load_domains.js
 * ----------------
 * Loads domains from a CSV file (one column named "IDN_Domain"),
 * keeps only ASCII hostnames, normalizes to lowercase, and inserts
 * each via addDomainToDB(domain, 0).
 *
 * Shows progress every 1000 inserted rows with % remaining.
 *
 * Usage:
 *   node load_domains.js ./domains.csv
 */

const fs = require('fs');
const { parse } = require('csv-parse');
const { addDomainToDB } = require('./services/domainService'); // ← update path if needed

// ---- Config ----
const CSV_PATH = process.argv[2];
const COLUMN_NAME = 'url'; // expected header name
const TOTAL_EXPECTED = 1_000_000; // adjust if known total differs
const MAX_ROWS = 1050; // maximum rows to process

if (!CSV_PATH) {
  console.error('Usage: node load_domains.js <path-to-csv>');
  process.exit(1);
}

// ---- Helpers ----
const ASCII_ONLY = /^[\x00-\x7F]+$/;
const LDH = /^[a-z0-9-]+$/i; // letters/digits/hyphen per label

function isAscii(s) {
  return ASCII_ONLY.test(s);
}

function extractHostname(urlOrDomain) {
  try {
    // If it looks like a URL, extract hostname
    if (urlOrDomain.includes('://')) {
      const url = new URL(urlOrDomain);
      return url.hostname;
    }
    // Otherwise, assume it's already a hostname/domain
    return urlOrDomain;
  } catch (e) {
    // If URL parsing fails, try to extract manually
    const match = urlOrDomain.match(/^https?:\/\/([^\/]+)/);
    return match ? match[1] : urlOrDomain;
  }
}

function isValidHostname(hostname) {
  if (!hostname) return false;
  if (hostname.length > 253) return false;
  if (hostname.startsWith('.') || hostname.endsWith('.')) return false;
  if (hostname.includes('..')) return false;

  const labels = hostname.split('.');
  for (const label of labels) {
    if (label.length < 1 || label.length > 63) return false;
    if (!LDH.test(label)) return false;
    if (label.startsWith('-') || label.endsWith('-')) return false;
  }
  return true;
}

// ---- Main ----
(async function main() {
  let inserted = 0;
  let skippedNonAscii = 0;
  let skippedInvalid = 0;
  let processed = 0; // total rows processed (including skipped)

  const parser = fs
    .createReadStream(CSV_PATH)
    .pipe(
      parse({
        bom: true,
        columns: true,
        trim: true,
        skip_empty_lines: true,
      })
    );

  try {
    for await (const row of parser) {
      if (processed >= MAX_ROWS) {
        console.log(`\n🛑 Reached maximum limit of ${MAX_ROWS} rows. Stopping.`);
        break;
      }

      // Debug: show first few rows structure
      if (processed < 3) {
        console.log(`Row ${processed + 1} keys:`, Object.keys(row));
        console.log(`Row ${processed + 1} data:`, row);
      }

      let domainRaw =
        row[COLUMN_NAME] ??
        row.IDN_Domain ??
        row.idn_domain ??
        row.Domain ??
        row.domain;

      if (!domainRaw) {
        if (processed < 5) {
          console.log(`Row ${processed + 1}: No domain found in any column`);
        }
        continue;
      }
      
      processed++;

      if (!isAscii(domainRaw)) {
        skippedNonAscii++;
        continue;
      }

      // Extract hostname from URL if needed
      let domain = extractHostname(String(domainRaw).trim().toLowerCase());
      
      if (processed < 5) {
        console.log(`Row ${processed}: "${domainRaw}" -> extracted: "${domain}"`);
      }
      
      if (!isValidHostname(domain)) {
        skippedInvalid++;
        continue;
      }

      try {
        await addDomainToDB(domain, 1);
        inserted++;

        if (inserted % 1000 === 0) {
          const remainingPercent = Math.max(
            0,
            100 - (inserted / TOTAL_EXPECTED) * 100
          ).toFixed(1);
          console.log(
            `Inserted: ${inserted.toLocaleString()} | Remaining: ${remainingPercent}%`
          );
        }
      } catch (err) {
        console.error(`Insert error for "${domain}": ${err.message}`);
      }
    }

    console.log('✅ Done');
    console.log(`Processed:           ${processed.toLocaleString()}`);
    console.log(`Inserted:            ${inserted.toLocaleString()}`);
    console.log(`Skipped (non-ASCII): ${skippedNonAscii.toLocaleString()}`);
    console.log(`Skipped (invalid):   ${skippedInvalid.toLocaleString()}`);
  } catch (err) {
    console.error('Fatal error while reading CSV:', err);
    process.exit(1);
  }
})();
