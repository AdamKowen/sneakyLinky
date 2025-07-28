/**
 * @file db.js
 * Initializes the single Sequelize instance shared by all models.
 * Works both locally (TCP) and on Cloud Run (Unix‑socket to Cloud SQL).
 *
 * Required env vars (local dev):
 *  DB_HOST  – database host (e.g. 127.0.0.1)
 *  DB_PORT  – database port (default 5432)
 *  DB_USER  – database user
 *  DB_PASS  – database password
 *  DB_NAME  – database/schema name
 *
 * On Cloud Run you **omit DB_HOST** and instead provide:
 *  INSTANCE_CONNECTION_NAME – "project:region:instance"  (Cloud SQL socket)
 *  DB_USER / DB_PASS / DB_NAME (as before)
 */

require('dotenv').config();
const { Sequelize } = require('sequelize');

/** Build host: use Cloud SQL socket if running on Cloud Run */
const socketPath =
  process.env.INSTANCE_CONNECTION_NAME &&
  `/cloudsql/${process.env.INSTANCE_CONNECTION_NAME}`;

const host =
  socketPath ||                       // production (Cloud Run)
  process.env.DB_HOST || '127.0.0.1'; // local / CI

const sequelize = new Sequelize(
  process.env.DB_NAME,               // database
  process.env.DB_USER,               // user
  process.env.DB_PASS,               // password
  {
    host,
    port: process.env.DB_PORT || 5432,
    dialect: 'postgres',
    logging: false,
    pool: { max: 5, min: 0, idle: 10_000 },
    define: { freezeTableName: true }, // keep exact table names
  }
);

module.exports = { sequelize, Sequelize };
