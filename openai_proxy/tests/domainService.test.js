// tests/domainService.test.js
const service = require('../src/services/domainService');
const repo = require('../src/repositories/domainRepository');

//  Mock the repository functions
jest.mock('../src/repositories/domainRepository', () => ({
  findByName: jest.fn(),
  addDomain : jest.fn(),
}));

describe('domainService', () => {
  afterEach(() => jest.clearAllMocks());

  // ────────────────────────────────────────────────────────────
  // checkDomainDB
  // ────────────────────────────────────────────────────────────
  describe('checkDomainDB', () => {
    test('returns domain when found', async () => {
      // Arrange
      const fakeDomain = { name: 'example.com', suspicious: 0 };
      repo.findByName.mockResolvedValue(fakeDomain);

      // Act
      const result = await service.checkDomainDB('example.com');

      // Assert
      expect(repo.findByName).toHaveBeenCalledWith('example.com');
      expect(result).toBe(fakeDomain);
    });

    test('returns null when domain not found', async () => {
      // Arrange
      repo.findByName.mockResolvedValue(null);

      // Act
      const result = await service.checkDomainDB('missing.com');

      // Assert
      expect(result).toBeNull();
    });

    test('throws when name is missing', async () => {
      // Arrange, Act & Assert
      await expect(service.checkDomainDB('')).rejects.toThrow(
        /Domain name is required/
      );
    });
  });

  // ────────────────────────────────────────────────────────────
  // addDomainToDB
  // ────────────────────────────────────────────────────────────
  describe('addDomainToDB', () => {
    test('returns existing domain if already in DB', async () => {
      // Arrange
      const existing = { name: 'dup.com', suspicious: 0 };
      repo.findByName.mockResolvedValue(existing);

      // Act
      const result = await service.addDomainToDB('dup.com');

      // Assert
      expect(repo.addDomain).not.toHaveBeenCalled();
      expect(result).toBe(existing);
    });

    test('adds and returns new domain when not in DB', async () => {
      // Arrange
      const newDomain = { name: 'new.com', suspicious: 1 };
      repo.findByName.mockResolvedValue(null);
      repo.addDomain.mockResolvedValue(newDomain);

      // Act
      const result = await service.addDomainToDB('new.com', 1);

      // Assert
      expect(repo.addDomain).toHaveBeenCalledWith('new.com', 1);
      expect(result).toBe(newDomain);
    });

    test('throws when name is not a string', async () => {
      // Arrange, Act & Assert
      await expect(service.addDomainToDB(123)).rejects.toThrow(
        /Domain name is required/
      );
    });
  });
});
