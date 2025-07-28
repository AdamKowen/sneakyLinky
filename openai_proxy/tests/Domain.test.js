// Domain.test.js
const { sequelize } = require('../src/config/db');
const Domain = require('../src/models/Domain');

beforeAll(() => sequelize.sync({ force: true }));
afterAll(() => sequelize.close());

// FQDN - Fully Qualified Domain Name

test('rejects invalid FQDN', async () => {
  // Arrange
  const input = 'bad domain';

  // Act + Assert
  await expect(Domain.create({ name: input, suspicious: true }))
    .rejects.toThrow(/Invalid domain/);
});

test('trims & saves valid FQDN', async () => {
  // Arrange
  const input = ' Example.com ';
  const expected = 'example.com';

  // Act
  const d = await Domain.create({ name: input, suspicious: 0 });

  // Assert
  expect(d.name).toBe(expected);
});

test('rejects empty domain', async () => {
  // Arrange
  const input = '';

  // Act + Assert
  await expect(Domain.create({ name: input }))
    .rejects.toThrow(/Validation notEmpty/);
});

test('rejects domain shorter than 3 characters', async () => {
  // Arrange
  const input = 'a.';

  // Act + Assert
  await expect(Domain.create({ name: input }))
    .rejects.toThrow(/Validation len/);
});

test('accepts domain with exactly 253 characters', async () => {
  // Arrange
  const label = 'a'.repeat(63);
  const domain = `${label}.${label}.${label}.${'a'.repeat(57)}.com`;
  expect(domain.length).toBe(253);

  // Act
  const d = await Domain.create({ name: domain, suspicious: 0 });

  // Assert
  expect(d.name).toBe(domain);
});

test('rejects domain longer than 253 characters', async () => {
  // Arrange
  const domain = 'a'.repeat(254);

  // Act + Assert
  await expect(Domain.create({ name: domain }))
    .rejects.toThrow(/Validation len/);
});

test('rejects domain without TLD', async () => {
  // Arrange
  const input = 'localhost';

  // Act + Assert
  await expect(Domain.create({ name: input }))
    .rejects.toThrow(/Invalid domain name/);
});

test('rejects same domain with different casing as duplicate (case-insensitive)', async () => {
  // Arrange
  const domain1 = 'TestDomain.com';
  const domain2 = 'testdomain.com'; 

  // Act
  await Domain.create({ name: domain1, suspicious: 0});

  // Assert
  await expect(Domain.create({ name: domain2, suspicious: 0 }))
    .rejects.toThrow(/Validation error/i);
});

test('rejects duplicate domain due to primary key constraint', async () => {
  // Arrange
  const input = 'dup-example.com';
  await Domain.create({ name: input, suspicious: 0 });

  // Act + Assert
  await expect(Domain.create({ name: input, suspicious: 0 }))
    .rejects.toThrow(/Validation error/i);
});

test('sets createdAt and updatedAt automatically', async () => {
  // Arrange
  const input = 'timestamp-test.com';

  // Act
  const d = await Domain.create({ name: input, suspicious: 0 });

  // Assert
  expect(d.createdAt).toBeInstanceOf(Date);
  expect(d.updatedAt).toBeInstanceOf(Date);
});
