#!/usr/bin/env node
'use strict';

/**
 * load_domains.js
 * ----------------
 * Loads domains from a CSV file (one column named "IDN_Domain" or compatible),
 * keeps only ASCII hostnames, normalizes to lowercase, and inserts
 * each via addDomainToDB(domain, flag).
 *
 * Shows progress every 1000 inserted rows with % remaining.
 *
 * Usage:
 *   node load_domains.js ./domains.csv
 */

const fs = require('fs');
const { parse } = require('csv-parse');
const { addDomainToDB, checkDomainDB } = require('./services/domainService'); // service functions
const readline = require('readline');

// ---- Config ----
const CSV_PATH = process.argv[2];
const COLUMN_NAME = 'url'; // expected header name (if headers exist)
const COLUMN_INDEX = 1; // column index if no headers (0-based) - changed to 1 for second column
const HAS_HEADERS = true; // set to false if CSV has no header row
const TOTAL_EXPECTED = 52_000; // adjust if known total differs
let MAX_ROWS = 1050; // maximum rows to process (can be overridden by user)

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
  
  // Reject IP addresses (basic check)
  if (/^\d+\.\d+\.\d+\.\d+$/.test(hostname)) {
    return false;
  }

  const labels = hostname.split('.');
  if (labels.length < 2) return false; // Domain must have at least 2 parts
  
  for (const label of labels) {
    if (label.length < 1 || label.length > 63) return false;
    if (!LDH.test(label)) return false;
    if (label.startsWith('-') || label.endsWith('-')) return false;
  }
  return true;
}

// ---- Main ----
(async function main() {
  let inserted = 0;                 // number of newly inserted domains
  let skippedMissing = 0;           // rows missing a domain/url column
  let skippedNonAscii = 0;          // skipped because non-ASCII input
  let skippedInvalid = 0;           // skipped because invalid hostname
  let alreadyExisting = 0;          // domain already exists in DB (not inserted)
  let dbFailed = 0;                 // attempted insert but DB failed (errors, etc.)
  let processed = 0;                // total rows processed (including skipped)

  // Interactive prompts
  const rl = readline.createInterface({ input: process.stdin, output: process.stdout });
  const ask = (q) => new Promise((resolve) => rl.question(q, resolve));
  let suspiciousFlag = 1; // default: suspicious
  try {
    const typeAns = (await ask('Insert as safe or suspicious domains? (safe/suspicious) [suspicious]: ')).trim().toLowerCase();
    if (typeAns.startsWith('safe') || typeAns === 's' && typeAns.includes('safe')) {
      suspiciousFlag = 0; // safe
      console.log('Will insert as SAFE domains (suspicious=0)');
    } else {
      suspiciousFlag = 1; // suspicious (default)
      console.log('Will insert as SUSPICIOUS domains (suspicious=1)');
    }

    const limitAns = (await ask(`How many domains to insert? (number, default ${MAX_ROWS}): `)).trim();
    const parsedLimit = parseInt(limitAns.replace(/[\,\s]/g, ''), 10);
    if (!isNaN(parsedLimit) && parsedLimit > 0) {
      MAX_ROWS = parsedLimit;
    }
  } finally {
    rl.close();
  }

  const parser = fs
    .createReadStream(CSV_PATH)
    .pipe(
      parse({
        bom: true,
        columns: HAS_HEADERS, // true for headers, false for no headers
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
        if (HAS_HEADERS) {
          console.log(`Row ${processed + 1} keys:`, Object.keys(row));
          console.log(`Row ${processed + 1} data:`, row);
        } else {
          console.log(`Row ${processed + 1} array:`, row);
        }
      }

      let domainRaw;
      if (HAS_HEADERS) {
        // Use column names
        domainRaw =
          row[COLUMN_NAME] ??
          row.IDN_Domain ??
          row.idn_domain ??
          row.Domain ??
          row.domain;
      } else {
        // Use column index (array access)
        domainRaw = row[COLUMN_INDEX];
      }

      if (!domainRaw) {
        if (processed < 5) {
          console.log(`Row ${processed + 1}: No domain found in any column`);
        }
        skippedMissing++;
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
        console.log(`Row ${processed}: "${domainRaw}" -> extracted: "${domain}" (suspicious=${suspiciousFlag})`);
      }
      
      if (!isValidHostname(domain)) {
        skippedInvalid++;
        continue;
      }

      // Check existence first to avoid counting existing as inserted
      try {
        const exists = await checkDomainDB(domain);
        if (exists) {
          alreadyExisting++;
          continue;
        }
      } catch (e) {
        // If existence check itself fails, attempt insert anyway
      }

      try {
        await addDomainToDB(domain, suspiciousFlag);
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
        dbFailed++;
        console.error(`Insert error for "${domain}": ${err.message}`);
      }
    }

    console.log('✅ Done');
    console.log(`Processed (rows read):   ${processed.toLocaleString()}`);
    console.log(`Inserted (new):          ${inserted.toLocaleString()}`);
    console.log(`Skipped (missing column):${skippedMissing.toLocaleString()}`);
    console.log(`Skipped (non-ASCII):     ${skippedNonAscii.toLocaleString()}`);
    console.log(`Skipped (invalid host):  ${skippedInvalid.toLocaleString()}`);
    console.log(`Already existed:         ${alreadyExisting.toLocaleString()}`);
    console.log(`DB insert failures:      ${dbFailed.toLocaleString()}`);
  } catch (err) {
    console.error('Fatal error while reading CSV:', err);
    process.exit(1);
  }
})();
