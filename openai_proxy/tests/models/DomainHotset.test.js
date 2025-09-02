// tests/models/DomainHotset.test.js
const { sequelize } = require('../../src/config/db');
const DomainHotset = require('../../src/models/DomainHotset');

beforeAll(() => sequelize.sync({ force: true }));
afterAll(() => sequelize.close());

// Helpers
const W_LIMIT = Number(process.env.MAX_WHITE_LIST_SIZE || 1000);
const B_LIMIT = Number(process.env.MAX_BLACK_LIST_SIZE || 1000);
const makeDomains = (n, base = 'example') => Array.from({ length: n }, (_, i) => `${base}${i}.com`);

/* ────────────────────────────────────────────────────────────────────────── */
/*  Defaults & timestamps                                                    */
/* ────────────────────────────────────────────────────────────────────────── */

test('creates with defaults and sets timestamps', async () => {
  // Arrange
  // (no input – rely on defaults)

  // Act
  const rec = await DomainHotset.create({});

  // Assert
  expect(typeof rec.version).toBe('number');
  expect(rec.whiteSnapshot).toEqual([]);
  expect(rec.blackSnapshot).toEqual([]);
  expect(rec.whiteAdd).toBeNull();
  expect(rec.whiteRemove).toBeNull();
  expect(rec.blackAdd).toBeNull();
  expect(rec.blackRemove).toBeNull();
  expect(rec.createdAt).toBeInstanceOf(Date);
  expect(rec.updatedAt).toBeInstanceOf(Date);
});

/* ────────────────────────────────────────────────────────────────────────── */
/*  Auto-increment version                                                   */
/* ────────────────────────────────────────────────────────────────────────── */

test('auto-increments version on subsequent creates', async () => {
  // Arrange
  const first = await DomainHotset.create({});

  // Act
  const second = await DomainHotset.create({});

  // Assert
  expect(second.version).toBe(first.version + 1);
});

/* ────────────────────────────────────────────────────────────────────────── */
/*  Not-null constraints                                                     */
/* ────────────────────────────────────────────────────────────────────────── */

test('rejects NULL whiteSnapshot (allowNull: false)', async () => {
  // Arrange
  const payload = { whiteSnapshot: null, blackSnapshot: [] };

  // Act & Assert
  await expect(DomainHotset.create(payload)).rejects.toThrow(/notNull violation/i);
});

/* ────────────────────────────────────────────────────────────────────────── */
/*  Size validations (per model validate)                                    */
/* ────────────────────────────────────────────────────────────────────────── */

test('rejects whiteSnapshot exceeding configured max size', async () => {
  // Arrange
  const tooMany = makeDomains(W_LIMIT + 1, 'white');
  const payload = { whiteSnapshot: tooMany, blackSnapshot: [] };

  // Act & Assert
  await expect(DomainHotset.create(payload))
    .rejects.toThrow(/whiteSnapshot exceeds maximum size/i);
});

test('rejects blackSnapshot exceeding configured max size', async () => {
  // Arrange
  const tooMany = makeDomains(B_LIMIT + 1, 'black');
  const payload = { whiteSnapshot: [], blackSnapshot: tooMany };

  // Act & Assert
  await expect(DomainHotset.create(payload))
    .rejects.toThrow(/blackSnapshot exceeds maximum size/i);
});

/* ────────────────────────────────────────────────────────────────────────── */
/*  Accepts snapshots within limits                                          */
/* ────────────────────────────────────────────────────────────────────────── */

test('accepts snapshots within limits and persists arrays', async () => {
  // Arrange
  const white = makeDomains(Math.min(3, W_LIMIT), 'okwhite');
  const black = makeDomains(Math.min(2, B_LIMIT), 'okblack');
  const payload = { whiteSnapshot: white, blackSnapshot: black };

  // Act
  const rec = await DomainHotset.create(payload);

  // Assert
  expect(rec.whiteSnapshot).toEqual(white);
  expect(rec.blackSnapshot).toEqual(black);
});
