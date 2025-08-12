
const adminRepo = require('../../src/repositories/adminRepository');
const Admin = require('../../src/models/Admin');
const bcrypt = require('bcrypt');

jest.mock('../../src/models/Admin');
jest.mock('bcrypt', () => ({
  hash: jest.fn(),
  compare: jest.fn(),
}));

describe('adminRepository', () => {
  afterEach(() => jest.clearAllMocks());

  describe('create', () => {
    test('creates admin with hashed password', async () => {
      // Arrange
      const email = 'Test@Example.com';
      const password = 'secret123';
      const hash = 'hashed_pw';
      const created = { id: 1, email: 'test@example.com', passwordHash: hash, createdAt: new Date(), updatedAt: new Date() };
      bcrypt.hash.mockResolvedValue(hash);
      Admin.create.mockResolvedValue({ toJSON: () => created });

      // Act
      const result = await adminRepo.create({ email, password });

      // Assert
      expect(bcrypt.hash).toHaveBeenCalledWith(password, expect.any(Number));
      expect(Admin.create).toHaveBeenCalledWith({ email: 'test@example.com', passwordHash: hash });
      expect(result).toEqual(created);
    });
  });

  describe('findById', () => {
    test('returns admin when found', async () => {
      // Arrange
      const admin = { id: 2, email: 'a@b.com', passwordHash: 'pw', createdAt: new Date(), updatedAt: new Date() };
      Admin.findByPk.mockResolvedValue({ toJSON: () => admin });

      // Act
      const result = await adminRepo.findById(2);

      // Assert
      expect(Admin.findByPk).toHaveBeenCalledWith(2);
      expect(result).toEqual(admin);
    });

    test('returns null when not found', async () => {
      // Arrange
      Admin.findByPk.mockResolvedValue(null);

      // Act
      const result = await adminRepo.findById(99);

      // Assert
      expect(result).toBeNull();
    });
  });

  describe('findByEmail', () => {
    test('returns admin when found', async () => {
      // Arrange
      const admin = { id: 3, email: 'x@y.com', passwordHash: 'pw', createdAt: new Date(), updatedAt: new Date() };
      Admin.findOne.mockResolvedValue({ toJSON: () => admin });

      // Act
      const result = await adminRepo.findByEmail('x@y.com');

      // Assert
      expect(Admin.findOne).toHaveBeenCalledWith({ where: { email: 'x@y.com' } });
      expect(result).toEqual(admin);
    });

    test('returns null when not found', async () => {
      // Arrange
      Admin.findOne.mockResolvedValue(null);

      // Act
      const result = await adminRepo.findByEmail('notfound@email.com');

      // Assert
      expect(result).toBeNull();
    });
  });

  describe('updatePassword', () => {
    test('updates password and returns true if one row updated', async () => {
      // Arrange
      const id = 5;
      const newPassword = 'newpass123';
      const hash = 'newhash';
      bcrypt.hash.mockResolvedValue(hash);
      Admin.update.mockResolvedValue([1]);

      // Act
      const result = await adminRepo.updatePassword(id, newPassword);

      // Assert
      expect(bcrypt.hash).toHaveBeenCalledWith(newPassword, expect.any(Number));
      expect(Admin.update).toHaveBeenCalledWith({ passwordHash: hash }, { where: { id } });
      expect(result).toBe(true);
    });

    test('returns false if no rows updated', async () => {
      // Arrange
      bcrypt.hash.mockResolvedValue('hash');
      Admin.update.mockResolvedValue([0]);

      // Act
      const result = await adminRepo.updatePassword(1, 'pw');

      // Assert
      expect(result).toBe(false);
    });
  });

  describe('remove', () => {
    test('returns true if one row deleted', async () => {
      // Arrange
      Admin.destroy.mockResolvedValue(1);

      // Act
      const result = await adminRepo.remove(7);

      // Assert
      expect(Admin.destroy).toHaveBeenCalledWith({ where: { id: 7 } });
      expect(result).toBe(true);
    });

    test('returns false if no rows deleted', async () => {
      // Arrange
      Admin.destroy.mockResolvedValue(0);

      // Act
      const result = await adminRepo.remove(8);

      // Assert
      expect(result).toBe(false);
    });
  });
});
