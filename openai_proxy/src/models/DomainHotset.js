// models/DomainHotset.js
const { DataTypes } = require('sequelize');
const { sequelize } = require('../config/db');

// Configuration constants for list limits
const MAX_WHITE_LIST_SIZE = Number(process.env.MAX_WHITE_LIST_SIZE || 1000);
const MAX_BLACK_LIST_SIZE = Number(process.env.MAX_BLACK_LIST_SIZE || 1000);

const DomainHotset = sequelize.define('DomainHotset', {
  version: { type: DataTypes.INTEGER, primaryKey: true, allowNull: false ,autoIncrement: true },

  whiteSnapshot: { type: DataTypes.ARRAY(DataTypes.TEXT), allowNull: false, defaultValue: [] },
  blackSnapshot: { type: DataTypes.ARRAY(DataTypes.TEXT), allowNull: false, defaultValue: [] },

  whiteAdd:     { type: DataTypes.ARRAY(DataTypes.TEXT), allowNull: true, defaultValue: null },
  whiteRemove:  { type: DataTypes.ARRAY(DataTypes.TEXT), allowNull: true, defaultValue: null },
  blackAdd:     { type: DataTypes.ARRAY(DataTypes.TEXT), allowNull: true, defaultValue: null },
  blackRemove:  { type: DataTypes.ARRAY(DataTypes.TEXT), allowNull: true, defaultValue: null },
}, {
  tableName: 'domain_hotset',
  timestamps: true,
  indexes: [{ unique: true, fields: ['version'] }],
  validate: {
    checkListSizes() {
      if ((this.whiteSnapshot?.length ?? 0) > MAX_WHITE_LIST_SIZE) {
        throw new Error(`whiteSnapshot exceeds maximum size of ${MAX_WHITE_LIST_SIZE}`);
      }
      if ((this.blackSnapshot?.length ?? 0) > MAX_BLACK_LIST_SIZE) {
        throw new Error(`blackSnapshot exceeds maximum size of ${MAX_BLACK_LIST_SIZE}`);
      }
    },
  }
});

module.exports = DomainHotset;
