// tests/parseDomain.test.js
const { extractDomain } = require('../src/utils/parseDomain');

describe('extractDomain', () => {
  test('returns hostname from https URL', () => {
    // Arrange
    const input = 'https://www.example.com/path?a=1';

    // Act
    const result = extractDomain(input);

    // Assert
    expect(result).toBe('www.example.com');
  });

  test('returns hostname from http URL with port', () => {
    // Arrange
    const input = 'http://sub.test.co.uk:8080/hello';

    // Act
    const result = extractDomain(input);

    // Assert
    expect(result).toBe('sub.test.co.uk');
  });

  test('returns null for invalid URL', () => {
    // Arrange
    const input = 'not a url';

    // Act
    const result = extractDomain(input);

    // Assert
    expect(result).toBeNull();
  });

  test('returns null for URL without protocol', () => {
    // Arrange
    const input = 'example.com/path';

    // Act
    const result = extractDomain(input);

    // Assert
    expect(result).toBeNull(); // new URL() throws without protocol
  });
});
