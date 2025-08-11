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
const COLUMN_NAME = 'IDN_Domain'; // expected header name
const TOTAL_EXPECTED = 1_000_000; // adjust if known total differs

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
      let domainRaw =
        row[COLUMN_NAME] ??
        row.IDN_Domain ??
        row.idn_domain ??
        row.Domain ??
        row.domain;

      if (!domainRaw) continue;

      if (!isAscii(domainRaw)) {
        skippedNonAscii++;
        continue;
      }

      let domain = String(domainRaw).trim().toLowerCase();
      if (!isValidHostname(domain)) {
        skippedInvalid++;
        continue;
      }

      try {
        await addDomainToDB(domain, 0);
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
    console.log(`Inserted:            ${inserted.toLocaleString()}`);
    console.log(`Skipped (non-ASCII): ${skippedNonAscii.toLocaleString()}`);
    console.log(`Skipped (invalid):   ${skippedInvalid.toLocaleString()}`);
  } catch (err) {
    console.error('Fatal error while reading CSV:', err);
    process.exit(1);
  }
})();
