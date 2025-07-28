// tests/analyzeMessageRoutes.test.js
const request = require('supertest');

// ── mock layer dependencies ──────────────────────────────────────────
jest.mock('../src/middleware/openai/openaiClient', () => ({
    analyzeMessage: jest.fn(),
}));

const { analyzeMessage } = require('../src/middleware/openai/openaiClient');

// ── import the express app AFTER mocks ──────────────────────────────
const app = require('../src/index');

const MESSAGE_MAX_TOKENS = parseInt(process.env.MESSAGE_MAX_TOKENS || '1000', 10);

describe('/v1/analyze-message integration', () => {
    afterEach(() => jest.clearAllMocks());

    test('happy path: message analyzed by OpenAI', async () => {
        // Arrange
        const mockResponse = {
            phishing_score: 0.87,
            suspicion_reasons: ['Suspicious link'],
            recommended_actions: ['Do not click'],
            source: 'openai',
        };
        analyzeMessage.mockResolvedValue(mockResponse);
        const message = 'Click here to claim your prize now!';

        // Act
        const res = await request(app)
            .post('/v1/analyze-message')
            .send({ message });

        // Assert
        expect(res.status).toBe(200);
        expect(analyzeMessage).toHaveBeenCalledWith(message);
        expect(res.body).toMatchObject({
            phishing_score: 0.87,
            source: 'openai',
        });
    });

    test('400 when message field missing', async () => {
        // Arrange
        const payload = {};

        // Act
        const res = await request(app)
            .post('/v1/analyze-message')
            .send(payload);

        // Assert
        expect(res.status).toBe(400);
        expect(res.body.error).toMatch(/Missing message/);
    });

    test('400 when message too long', async () => {
        // Arrange
        const longMsg = 'a'.repeat(MESSAGE_MAX_TOKENS + 1);
        const payload = { message: longMsg };

        // Act
        const res = await request(app)
            .post('/v1/analyze-message')
            .send(payload);

        // Assert
        expect(res.status).toBe(400);
        expect(res.body.error).toMatch(/up to/);
    });

    test('500 when OpenAI throws error', async () => {
        // Arrange
        analyzeMessage.mockRejectedValue(new Error('OpenAI error'));
        const payload = { message: 'legit message' };

        // Act
        const res = await request(app)
            .post('/v1/analyze-message')
            .send(payload);

        // Assert
        expect(res.status).toBe(500);
        expect(res.body.error).toMatch(/Internal server error/);
    });

    test('400 invalid JSON payload', async () => {
        // Arrange
        const invalidJson = '{ message: "bad" }';

        // Act
        const res = await request(app)
            .post('/v1/analyze-message')
            .set('Content-Type', 'application/json')
            .send(invalidJson); // malformed JSON

        // Assert
        expect(res.status).toBe(400);
        expect(res.body.error).toMatch(/Invalid JSON/i);
    });
});

// Optional: close DB connections if needed
afterAll(async () => {
    try {
        const { sequelize } = require('../src/config/db');
        if (sequelize && sequelize.close) {
            await sequelize.close();
        }
    } catch (e) {
        // ignore if not relevant
    }
});
