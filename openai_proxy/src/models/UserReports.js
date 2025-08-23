/**
 * @file UserReports.js
 * @description Sequelize model definition for the **UserReports** table.
 *              Stores user-submitted reports about suspicious URLs.
 *
 * Columns
 * --------
 * - **id**                   `UUID`      – primary key, auto-generated.
 * - **url**                  `STRING`    – reported URL.
 * - **systemClassification** `INTEGER`   – system classification (0=safe, 1=suspicious).
 * - **userClassification**   `INTEGER`   – user classification (0=safe, 1=suspicious).
 * - **userReason**           `STRING`    – user-provided reason for classification.
 * - **adminDecision**        `INTEGER`   – admin decision (0=safe, 1=suspicious, nullable).
 * - **createdAt/updatedAt**  `DATE`      – timestamps (auto-managed).
 */

const { DataTypes } = require('sequelize');
const { sequelize } = require('../config/db');

const UserReports = sequelize.define(
  'UserReports',
  {
    id: {
      type: DataTypes.UUID,
      defaultValue: DataTypes.UUIDV4,
      primaryKey: true,
    },
    url: {
      type: DataTypes.STRING(1024),
      allowNull: false,
      validate: {
        notEmpty: true,
        isUrl: true,
      },
    },
    systemClassification: {
      type: DataTypes.INTEGER,
      allowNull: false,
      validate: {
        isIn: [[0, 1]],
      },
    },
    userClassification: {
      type: DataTypes.INTEGER,
      allowNull: false,
      validate: {
        isIn: [[0, 1]],
      },
    },
    userReason: {
      type: DataTypes.STRING(1024),
      allowNull: false,
      validate: {
        notEmpty: true,
      },
    },
    adminDecision: {
      type: DataTypes.INTEGER,
      allowNull: true,
      validate: {
        isIn: [[0, 1]],
      },
    },
    reportCount: {
      type: DataTypes.BIGINT,
      allowNull: false,
      defaultValue: 1,
    },
  },
  {
    tableName: 'user_reports',
    timestamps: true,
  }
);

module.exports = UserReports;
