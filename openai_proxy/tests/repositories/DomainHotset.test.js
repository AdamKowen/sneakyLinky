const repo = require('../../src/repositories/domainHotsetRepository');
const DomainHotset = require('../../src/models/DomainHotset');

jest.mock('../../src/models/DomainHotset');

describe('domainHotsetRepository', () => {
  afterEach(() => jest.clearAllMocks());

  describe('create', () => {
    test('creates a new record', async () => {
      // Arrange
      const payload = { whiteSnapshot: ['a.com'], blackSnapshot: ['b.com'] };
      const created = { id: 1, version: 1, ...payload };
      DomainHotset.create.mockResolvedValue(created);

      // Act
      const result = await repo.create(payload);

      // Assert
      expect(DomainHotset.create).toHaveBeenCalledWith(payload);
      expect(result).toBe(created);
    });

    test('throws on DB error', async () => {
      // Arrange
      DomainHotset.create.mockRejectedValue(new Error('DB error'));

      // Act & Assert
      await expect(repo.create({ whiteSnapshot: [], blackSnapshot: [] }))
        .rejects.toThrow('DB error');
    });
  });

  describe('findLatest', () => {
    test('returns latest record by version desc', async () => {
      // Arrange
      const rec = { version: 3 };
      DomainHotset.findOne.mockResolvedValue(rec);

      // Act
      const result = await repo.findLatest();

      // Assert
      expect(DomainHotset.findOne).toHaveBeenCalledWith({ order: [['version', 'DESC']] });
      expect(result).toBe(rec);
    });
  });

  describe('findByVersion', () => {
    test('returns record for given version', async () => {
      // Arrange
      const rec = { version: 2 };
      DomainHotset.findOne.mockResolvedValue(rec);

      // Act
      const result = await repo.findByVersion(2);

      // Assert
      expect(DomainHotset.findOne).toHaveBeenCalledWith({ where: { version: 2 } });
      expect(result).toBe(rec);
    });
  });

  describe('count', () => {
    test('returns count', async () => {
      // Arrange
      DomainHotset.count.mockResolvedValue(7);

      // Act
      const result = await repo.count();

      // Assert
      expect(DomainHotset.count).toHaveBeenCalled();
      expect(result).toBe(7);
    });
  });

  describe('deleteByVersion', () => {
    test('deletes one record safely', async () => {
      // Arrange
      DomainHotset.destroy.mockResolvedValue(1);

      // Act
      const result = await repo.deleteByVersion(5);

      // Assert
      expect(DomainHotset.destroy).toHaveBeenCalledWith({ where: { version: 5 }, limit: 1 });
      expect(result).toBe(1);
    });
  });

  describe('updateByVersion', () => {
    test('updates record by version', async () => {
      // Arrange
      DomainHotset.update.mockResolvedValue([1]);

      // Act
      const result = await repo.updateByVersion(4, { whiteAdd: ['x.com'] });

      // Assert
      expect(DomainHotset.update).toHaveBeenCalledWith({ whiteAdd: ['x.com'] }, { where: { version: 4 } });
      expect(result).toEqual([1]);
    });
  });

  describe('findOldestVersion', () => {
    test('returns oldest record by version asc', async () => {
      // Arrange
      const rec = { version: 1 };
      DomainHotset.findOne.mockResolvedValue(rec);

      // Act
      const result = await repo.findOldestVersion();

      // Assert
      expect(DomainHotset.findOne).toHaveBeenCalledWith({ order: [['version', 'ASC']] });
      expect(result).toBe(rec);
    });
  });
});