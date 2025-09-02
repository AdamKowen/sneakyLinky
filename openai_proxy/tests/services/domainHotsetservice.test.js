// tests/services/domainHotsetservice.test.js

jest.mock('../../src/repositories/domainHotsetRepository', () => ({
  create: jest.fn(),
  count: jest.fn(),
  findOldestVersion: jest.fn(),
  deleteByVersion: jest.fn(),
  updateByVersion: jest.fn(),
  findByVersion: jest.fn(),
  findLatest: jest.fn(),
}));

jest.mock('../../src/repositories/domainRepository', () => ({
  getLatestHotset: jest.fn(),
}));

const repo = require('../../src/repositories/domainHotsetRepository');
const domainRepo = require('../../src/repositories/domainRepository');
const service = require('../../src/services/domainHotsetService');

beforeEach(() => {
  jest.clearAllMocks();
});

/* ────────────────────────────────────────────────────────────────────────── */
/*  getLatestVersionNumber                                                   */
/* ────────────────────────────────────────────────────────────────────────── */

test('getLatestVersionNumber returns version when record exists', async () => {
  // Arrange
  repo.findLatest.mockResolvedValue({ version: 7 });

  // Act
  const v = await service.getLatestVersionNumber();

  // Assert
  expect(repo.findLatest).toHaveBeenCalled();
  expect(v).toBe(7);
});

test('getLatestVersionNumber throws when record not found', async () => {
  // Arrange
  repo.findLatest.mockResolvedValue(null);

  // Act & Assert
  await expect(service.getLatestVersionNumber()).rejects.toThrow('Record not found');
});

/* ────────────────────────────────────────────────────────────────────────── */
/*  getDeltaChangesByVersion                                                 */
/* ────────────────────────────────────────────────────────────────────────── */

test('getDeltaChangesByVersion returns delta fields', async () => {
  // Arrange
  const rec = {
    version: 3,
    whiteAdd: ['a.com'], whiteRemove: ['b.com'],
    blackAdd: ['c.com'], blackRemove: ['d.com'],
  };
  repo.findByVersion.mockResolvedValue(rec);

  // Act
  const delta = await service.getDeltaChangesByVersion(3);

  // Assert
  expect(repo.findByVersion).toHaveBeenCalledWith(3);
  expect(delta).toEqual({
    whiteAdd: ['a.com'], whiteRemove: ['b.com'],
    blackAdd: ['c.com'], blackRemove: ['d.com'],
  });
});

/* ────────────────────────────────────────────────────────────────────────── */
/*  getOldestVersionNumber                                                   */
/* ────────────────────────────────────────────────────────────────────────── */

test('getOldestVersionNumber returns the oldest version', async () => {
  // Arrange
  repo.findOldestVersion.mockResolvedValue({ version: 1 });

  // Act
  const v = await service.getOldestVersionNumber();

  // Assert
  expect(repo.findOldestVersion).toHaveBeenCalled();
  expect(v).toBe(1);
});

/* ────────────────────────────────────────────────────────────────────────── */
/*  getVersionRecord                                                         */
/* ────────────────────────────────────────────────────────────────────────── */

test('getVersionRecord returns the full record', async () => {
  // Arrange
  const rec = { version: 2, whiteSnapshot: [], blackSnapshot: [] };
  repo.findByVersion.mockResolvedValue(rec);

  // Act
  const out = await service.getVersionRecord(2);

  // Assert
  expect(repo.findByVersion).toHaveBeenCalledWith(2);
  expect(out).toBe(rec);
});

/* ────────────────────────────────────────────────────────────────────────── */
/*  createDomainHotsetVersion                                                */
/* ────────────────────────────────────────────────────────────────────────── */

test('createDomainHotsetVersion creates record and does not delete when under limit', async () => {
  // Arrange
  const white = ['w1.com', 'w2.com'];
  const black = ['b1.com'];
  domainRepo.getLatestHotset.mockResolvedValueOnce(white).mockResolvedValueOnce(black);

  const created = {
    version: 2,
    whiteSnapshot: white,
    blackSnapshot: black,
    whiteAdd: null, whiteRemove: null, blackAdd: null, blackRemove: null,
  };
  repo.create.mockResolvedValue(created);
  repo.count.mockResolvedValue(1); // <= MAX_RECORDS_COUNT
  repo.findByVersion.mockResolvedValue(null); // ensure delta updater loop exits immediately

  // Act
  const out = await service.createDomainHotsetVersion();

  // Assert
  expect(domainRepo.getLatestHotset).toHaveBeenCalledTimes(2);
  expect(repo.create).toHaveBeenCalledWith({
    whiteSnapshot: white,
    blackSnapshot: black,
    whiteAdd: null, whiteRemove: null, blackAdd: null, blackRemove: null,
  });
  expect(repo.count).toHaveBeenCalled();
  expect(repo.deleteByVersion).not.toHaveBeenCalled();
  expect(out).toBe(created);
});

test('createDomainHotsetVersion deletes oldest when over limit', async () => {
  // Arrange
  const white = ['w.com'];
  const black = ['b.com'];
  domainRepo.getLatestHotset.mockResolvedValueOnce(white).mockResolvedValueOnce(black);

  const created = { version: 6, whiteSnapshot: white, blackSnapshot: black, whiteAdd: null, whiteRemove: null, blackAdd: null, blackRemove: null };
  repo.create.mockResolvedValue(created);
  repo.count.mockResolvedValue(6); // > default MAX_RECORDS_COUNT (5)
  repo.findOldestVersion.mockResolvedValue({ version: 1 });
  repo.findByVersion.mockResolvedValue(null); // ensure delta updater loop exits

  // Act
  await service.createDomainHotsetVersion();

  // Assert
  expect(repo.findOldestVersion).toHaveBeenCalled();
  expect(repo.deleteByVersion).toHaveBeenCalledWith(1);
});
