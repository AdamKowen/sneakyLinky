/**
 * @file Admin.js
 * @description Sequelize model definition for the **Admin** table.
 *              Since the application does not support self-registration,
 *              this table stores only privileged administrator accounts.
 *
 * Columns
 * --------
 * - **id**            `BIGINT`  – primary key, auto-increment.  
 * - **email**         `STRING`  – unique login identifier; RFC-5322.  
 * - **passwordHash**  `STRING`  – bcrypt / argon2 hash of the password.  
 *
 * `createdAt` / `updatedAt` timestamps are added automatically
 * (`timestamps: true`).
 */

const { DataTypes } = require('sequelize');
const { sequelize } = require('../config/db');
const validator     = require('validator');

/**
 * Sequelize model for application administrators.
 *
 * @type {import('sequelize').Model}
 */
const Admin = sequelize.define(
  'Admin',
  {
    /** Surrogate primary key (auto-increment). */
    id: {
      type: DataTypes.BIGINT,
      primaryKey: true,
      autoIncrement: true,
    },

    /**
     * Administrator e-mail (unique, case-insensitive).
     * The value is normalized (trimmed and lowercased) before storage.
     */
    email: {
      type: DataTypes.STRING,
      allowNull: false,
      unique: true,
      validate: {
        isEmail: {
          args: true,
          msg: 'Invalid e-mail address',
        },
      },
      set(value) {
        if (typeof value === 'string') {
          this.setDataValue('email', value.trim().toLowerCase());
        } else {
          this.setDataValue('email', value);
        }
      },
    },

    /** Bcrypt / Argon2 hash of the administrator’s password. */
    passwordHash: {
      type: DataTypes.STRING(255),
      allowNull: false,
      validate: {
        isProperLength(value) {
          if (value.length < 60) {
            throw new Error('Password hash too short – did you hash it?');
          }
        },
      },
    },
  },
  {
    tableName:  'admins',   // explicit DB table name
    timestamps: true,       // adds createdAt / updatedAt
    underscored: true,      // snake_case column names
  }
);

module.exports = Admin;
