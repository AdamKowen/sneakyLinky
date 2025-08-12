
const adminService = require('../../src/services/adminService');
const adminRepo = require('../../src/repositories/adminRepository');

jest.mock('../../src/repositories/adminRepository');

describe('adminService', () => {
  afterEach(() => jest.clearAllMocks());

  describe('createAdmin', () => {
    test('creates admin with valid input', async () => {
      // Arrange
      const email = 'user@example.com';
      const password = 'password123';
      const created = { id: 1, email, passwordHash: 'hash' };
      adminRepo.create.mockResolvedValue(created);

      // Act
      const result = await adminService.createAdmin(email, password);

      // Assert
      expect(adminRepo.create).toHaveBeenCalledWith({ email, password });
      expect(result).toBe(created);
    });

    test('throws on invalid email', async () => {
      await expect(adminService.createAdmin('bad', 'password123')).rejects.toThrow('Invalid email');
    });

    test('throws on short password', async () => {
      await expect(adminService.createAdmin('user@example.com', 'short')).rejects.toThrow('Password must be at least 8 characters');
    });
  });

  describe('getAdminById', () => {
    test('returns admin for valid id', async () => {
      // Arrange
      const admin = { id: 2, email: 'a@b.com' };
      adminRepo.findById.mockResolvedValue(admin);

      // Act
      const result = await adminService.getAdminById(2);

      // Assert
      expect(adminRepo.findById).toHaveBeenCalledWith(2);
      expect(result).toBe(admin);
    });

    test('throws on invalid id', async () => {
      await expect(adminService.getAdminById('bad')).rejects.toThrow('Invalid admin id');
      await expect(adminService.getAdminById(-1)).rejects.toThrow('Invalid admin id');
    });
  });

  describe('getAdminByEmail', () => {
    test('returns admin for valid email', async () => {
      // Arrange
      const admin = { id: 3, email: 'x@y.com' };
      adminRepo.findByEmail.mockResolvedValue(admin);

      // Act
      const result = await adminService.getAdminByEmail('x@y.com');

      // Assert
      expect(adminRepo.findByEmail).toHaveBeenCalledWith('x@y.com');
      expect(result).toBe(admin);
    });

    test('throws on invalid email', async () => {
      await expect(adminService.getAdminByEmail('bad')).rejects.toThrow('Invalid email');
    });
  });

  describe('updateAdminPassword', () => {
    test('updates password for valid input', async () => {
      // Arrange
      adminRepo.updatePassword.mockResolvedValue(true);

      // Act
      const result = await adminService.updateAdminPassword(1, 'newpassword');

      // Assert
      expect(adminRepo.updatePassword).toHaveBeenCalledWith(1, 'newpassword');
      expect(result).toBe(true);
    });

    test('throws on invalid id', async () => {
      await expect(adminService.updateAdminPassword('bad', 'newpassword')).rejects.toThrow('Invalid admin id');
    });

    test('throws on short password', async () => {
      await expect(adminService.updateAdminPassword(1, 'short')).rejects.toThrow('Password must be at least 8 characters');
    });
  });

  describe('deleteAdmin', () => {
    test('deletes admin for valid id', async () => {
      // Arrange
      adminRepo.remove.mockResolvedValue(true);

      // Act
      const result = await adminService.deleteAdmin(1);

      // Assert
      expect(adminRepo.remove).toHaveBeenCalledWith(1);
      expect(result).toBe(true);
    });

    test('throws on invalid id', async () => {
      await expect(adminService.deleteAdmin('bad')).rejects.toThrow('Invalid admin id');
    });
  });
});
