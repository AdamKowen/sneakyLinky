/**
 * Extracts the domain from a given URL string.
 *
 * @param {string} url - The full URL (e.g., "https://example.com/path?query").
 * @returns {string|null} - The domain (e.g., "example.com"), or null if invalid.
 */
function extractDomain(url) {
  try {
    const parsedUrl = new URL(url);
    return parsedUrl.hostname;
  } catch (err) {
    // Invalid URL
    return null;
  }
}

module.exports = { extractDomain };