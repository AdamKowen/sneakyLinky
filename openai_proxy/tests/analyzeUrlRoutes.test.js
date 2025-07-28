// tests/analyzeUrlRoutes.test.js
const request = require('supertest');

// ── mock layer dependencies ──────────────────────────────────────────
jest.mock('../src/services/domainService', () => ({
    checkDomainDB: jest.fn(),
    addDomainToDB: jest.fn(),
}));
jest.mock('../src/middleware/openai/openaiClient', () => ({
    analyzeUrl: jest.fn(),
}));

const {
    checkDomainDB,
    addDomainToDB,
} = require('../src/services/domainService');
const { analyzeUrl } = require('../src/middleware/openai/openaiClient');

// ── import the express app AFTER mocks ──────────────────────────────
const app = require('../src/index');          // app exported from src/index.js

// helper to build ISO dates
const daysAgoISO = (days) =>
    new Date(Date.now() - days * 24 * 60 * 60 * 1000).toISOString();

describe('/v1/analyze-url integration', () => {
    afterEach(() => jest.clearAllMocks());


    test('happy path: new domain analysed by OpenAI', async () => {
        // Arrange
        checkDomainDB.mockResolvedValue(null);
        analyzeUrl.mockResolvedValue({
            phishing_score: 0.42,
            suspicion_reasons: [],
            recommended_actions: []
        });
        addDomainToDB.mockResolvedValue({}); // pretend insert ok

        // Act
        const res = await request(app)
            .post('/v1/analyze-url')
            .send({ url: 'https://example.com' });

        // Assert
        expect(res.status).toBe(200);
        expect(analyzeUrl).toHaveBeenCalledWith('https://example.com');
        expect(res.body).toMatchObject({
            domain: 'example.com',
            source: 'openai',
            phishing_score: 0.42
        });
    });


    test('cache hit from local DB', async () => {
        // Arrange
        checkDomainDB.mockResolvedValue({
            name: 'example.com',
            suspicious: 1,
            updatedAt: daysAgoISO(10)
        });

        // Act
        const res = await request(app)
            .post('/v1/analyze-url')
            .send({ url: 'https://example.com' });

        // Assert
        expect(res.status).toBe(200);
        expect(res.body.source).toBe('local-db');
        expect(res.body.phishing_score).toBe(1);
    });


    test('stale row triggers OpenAI and DB update', async () => {
        // Arrange
        checkDomainDB.mockResolvedValue({
            name: 'old.com',
            suspicious: 0,
            updatedAt: daysAgoISO(366)
        });
        analyzeUrl.mockResolvedValue({
            phishing_score: 0.8,
            suspicion_reasons: [],
            recommended_actions: []
        });

        // Act
        const res = await request(app)
            .post('/v1/analyze-url')
            .send({ url: 'https://old.com' });

        // Assert
        expect(res.status).toBe(200);
        expect(res.body.source).toBe('openai');
        expect(addDomainToDB).toHaveBeenCalledWith('old.com', 1);
    });


    test('400 when URL field missing', async () => {
        const res = await request(app).post('/v1/analyze-url').send({});
        expect(res.status).toBe(400);
        expect(res.body.error).toMatch(/Missing URL/);
    });


    test('400 invalid JSON payload', async () => {
        const res = await request(app)
            .post('/v1/analyze-url')
            .set('Content-Type', 'application/json')
            .send('{ url: "bad" }'); // malformed JSON
        expect(res.status).toBe(400);
        expect(res.body.error).toMatch(/Invalid JSON/i);
    });


    test('400 invalid URL format', async () => {
        const res = await request(app)
            .post('/v1/analyze-url')
            .send({ url: 'example.com' });
        expect(res.status).toBe(400);
        expect(res.body.error).toMatch(/Invalid URL/);
    });


    test('400 non‑FQDN domain', async () => {
        const res = await request(app)
            .post('/v1/analyze-url')
            .send({ url: 'http://localhost' });
        expect(res.status).toBe(400);
        expect(res.body.error).toMatch(/Invalid domain format/);
    });


    test('OpenAI low score stores suspicious = 0', async () => {
        checkDomainDB.mockResolvedValue(null);
        analyzeUrl.mockResolvedValue({ phishing_score: 0.1 });
        const res = await request(app)
            .post('/v1/analyze-url')
            .send({ url: 'https://low.com' });

        expect(addDomainToDB).toHaveBeenCalledWith('low.com', 0);
        expect(res.body.phishing_score).toBe(0.1);
    });

    test('OpenAI high score stores suspicious = 1', async () => {
        checkDomainDB.mockResolvedValue(null);
        analyzeUrl.mockResolvedValue({ phishing_score: 0.92 });
        const res = await request(app)
            .post('/v1/analyze-url')
            .send({ url: 'https://high.com' });

        expect(addDomainToDB).toHaveBeenCalledWith('high.com', 1);
        expect(res.body.phishing_score).toBe(0.92);
    });


    test('400 invalid domain format (host w/o TLD)', async () => {
        const res = await request(app)
            .post('/v1/analyze-url')
            .send({ url: 'http://singlelabel' }); // valid URL, not FQDN
        expect(res.status).toBe(400);
        expect(res.body.error).toMatch(/Invalid domain format/);
    });
});

afterAll(async () => {
  // Try to close any open DB connections if use Sequelize
  try {
    const { sequelize } = require('../src/config/db');
    if (sequelize && sequelize.close) {
      await sequelize.close();
    }
  } catch (e) {
    // ignore if not relevant
  }
});
