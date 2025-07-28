const { addDomain, findByName } = require('../src/repositories/domainRepository');
const Domain = require('../src/models/Domain');

jest.mock('../src/models/Domain');

describe('domainRepository', () => {
  afterEach(() => jest.clearAllMocks());

  describe('findByName', () => {
    test('returns domain when found', async () => {
      // Arrange
      const domainName = 'Example.com';
      const expected = { id: 1, name: 'example.com', suspicious: 1 };
      Domain.findOne.mockResolvedValue(expected);

      // Act
      const result = await findByName(domainName);

      // Assert
      expect(Domain.findOne).toHaveBeenCalledWith({ where: { name: 'example.com' } });
      expect(result).toBe(expected);
    });

    test('returns null when not found', async () => {
      // Arrange
      Domain.findOne.mockResolvedValue(null);

      // Act
      const result = await findByName('notfound.com');

      // Assert
      expect(result).toBeNull();
    });

    test('throws error on DB error', async () => {
      // Arrange
      Domain.findOne.mockRejectedValue(new Error('DB error'));

      // Act & Assert
      await expect(findByName('fail.com')).rejects.toThrow('DB error');
    });
  });

  describe('addDomain', () => {
    test('creates domain with default suspicious', async () => {
      // Arrange
      const domainName = 'Test.com';
      const created = { id: 2, name: 'test.com', suspicious: 1 };
      Domain.create.mockResolvedValue(created);

      // Act
      const result = await addDomain(domainName);

      // Assert
      expect(Domain.create).toHaveBeenCalledWith({ name: 'test.com', suspicious: 1 });
      expect(result).toBe(created);
    });

    test('creates domain with suspicious=0', async () => {
      // Arrange
      const domainName = 'Safe.com';
      const created = { id: 3, name: 'safe.com', suspicious: 0 };
      Domain.create.mockResolvedValue(created);

      // Act
      const result = await addDomain(domainName, 0);

      // Assert
      expect(Domain.create).toHaveBeenCalledWith({ name: 'safe.com', suspicious: 0 });
      expect(result).toBe(created);
    });

    test('throws error on DB error', async () => {
      // Arrange
      Domain.create.mockRejectedValue(new Error('DB error'));

      // Act & Assert
      await expect(addDomain('fail.com')).rejects.toThrow('DB error');
    });
  });
});
