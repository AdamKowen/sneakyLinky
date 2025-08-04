/**
 * @file Domain.js
 * @description Sequelize model definition for the **Domain** table.
 *              Each record represents a fully-qualified domain name (FQDN)
 *              that was analysed by the application.  
 *              The domain itself is stored as the **primary key** so it
 *              must be unique and case-sensitive.
 *
 * Columns
 * --------
 * - **name** `STRING(253)` – primary key.  
 *   · Must not be empty, 3-253 chars, valid FQDN.  
 * - **suspicious** `INTEGER` – whether the domain is considered risky.  
 *   · Defaults to `false`.
 *
 * The model also includes `createdAt` and `updatedAt` timestamps
 * (enabled by `timestamps: true`).
 */

const { DataTypes } = require('sequelize');
const { sequelize } = require('../config/db');
const validator     = require('validator');

/**
 * Sequelize model for domains under scrutiny.
 *
 * @type {import('sequelize').Model}
 */
const Domain = sequelize.define(
  'Domain',
  {
    /**
     * Fully-qualified domain name (FQDN).  
     * Acts as primary key.
     *
     * @example "example.com"
     */
    name: {
      type: DataTypes.STRING,
      allowNull: false,
      primaryKey: true,

      /** Field-level validators */
      validate: {
        /** Must contain at least one non-whitespace character */
        notEmpty: true,

        /** RFC 1035: 3 to 253 characters including dots */
        len: [3, 253],

        /**
         * Custom validator: ensure the value is a valid FQDN.
         *
         * @param {string} value – domain name provided by the user
         * @throws {Error}       – when the domain is not a valid FQDN
         */
        isFqdn(value) {
          if (!validator.isFQDN(value, { require_tld: true })) {
            throw new Error('Invalid domain name');
          }
        },
      },

      /**
       * Setter – trims surrounding whitespace before persistence.
       *
       * @param {string} value – raw domain string
       */
      set(value) {
        this.setDataValue('name', value.trim().toLowerCase());
      },
    },

    /**
     * Flag indicating whether the domain has been marked as suspicious
     * by the analysis pipeline.
     *
     */
    suspicious: {
      type: DataTypes.INTEGER,
      allowNull: false,
    },
    
    /**
     * Counts how many times this domain was fetched / analysed.
     * Increment with:  await Domain.increment('access_count', { by: 1, where: { name } });
     */
    access_count: {
      type: DataTypes.BIGINT,
      allowNull: false,
      defaultValue: 0,
    },
  },
  {
    /** Adds `createdAt` and `updatedAt` columns */
    timestamps: true,
  }
);

module.exports = Domain;
