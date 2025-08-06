// tests/models/Admin.test.js
const { sequelize } = require('../../src/config/db');
const Admin         = require('../../src/models/Admin');

beforeAll(() => sequelize.sync({ force: true }));
afterAll(() => sequelize.close());

/*
 * Helpers
 * -------
 * • 60-char dummy string satisfies the “hash length ≥ 60” rule without
 *   having to pull in bcrypt/argon2.
 */
const VALID_HASH = 'x'.repeat(60);    // e.g. "$2b$12$..." would also work

/* ────────────────────────────────────────────────────────────────────────── */
/*  Email validation                                                        */
/* ────────────────────────────────────────────────────────────────────────── */

test('rejects invalid e-mail', async () => {
  await expect(
    Admin.create({ email: 'not-an-email', passwordHash: VALID_HASH })
  ).rejects.toThrow(/Invalid e-mail address/);
});

test('trims & lower-cases e-mail before save', async () => {
  const raw       = '  ADMIN@Example.COM ';
  const normalised = 'admin@example.com';

  const a = await Admin.create({ email: raw, passwordHash: VALID_HASH });
  expect(a.email).toBe(normalised);
});

test('rejects duplicate e-mail (case-insensitive)', async () => {
  const mail1 = 'duplicate@example.com';
  const mail2 = 'DUPLICATE@EXAMPLE.COM';

  await Admin.create({ email: mail1, passwordHash: VALID_HASH });

  await expect(
    Admin.create({ email: mail2, passwordHash: VALID_HASH })
  ).rejects.toThrow(/Validation error/i);   // unique constraint
});

test('rejects NULL e-mail', async () => {
  await expect(
    Admin.create({ email: null, passwordHash: VALID_HASH })
  ).rejects.toThrow(/notNull violation/i);
});

/* ────────────────────────────────────────────────────────────────────────── */
/*  Password-hash validation                                                */
/* ────────────────────────────────────────────────────────────────────────── */

test('rejects passwordHash shorter than 60 chars', async () => {
  const shortHash = 'too-short';

  await expect(
    Admin.create({ email: 'short@hash.com', passwordHash: shortHash })
  ).rejects.toThrow(/Password hash too short/);
});

/* ────────────────────────────────────────────────────────────────────────── */
/*  Timestamps                                                              */
/* ────────────────────────────────────────────────────────────────────────── */

test('sets createdAt and updatedAt automatically', async () => {
  const a = await Admin.create({
    email:        'timestamps@example.com',
    passwordHash: VALID_HASH,
  });

  expect(a.createdAt).toBeInstanceOf(Date);
  expect(a.updatedAt).toBeInstanceOf(Date);
});
