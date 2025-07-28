# OpenAI Proxy Server

A secure Node.js Express server that acts as a proxy for OpenAI API requests. This project keeps your OpenAI API key safe on the server side, while providing endpoints for analyzing URLs and messages for phishing risk using OpenAI's models and local DB.

---

## Features
- **Secure API Key Handling:** The OpenAI API key is stored in a `.env` file and never exposed to the client.
- **Phishing Risk Analysis:** Analyze URLs and messages for phishing risk using `/v1/analyze-url` and `/v1/analyze-message` endpoints.
- **Health Check:** Simple `/health` endpoint for monitoring server status.
- **Modular Code:** All routes, services, repositories, models, middleware, and utilities are separated for easy maintenance and extension.
- **JSDoc Documentation:** All code is documented for clarity and maintainability.
- **Comprehensive Testing:** Unit and integration tests for all major components, with coverage reports.
- **Docker & Cloud Ready:** Includes Dockerfile, .dockerignore, and app.yaml for Google Cloud Run deployment.

---

## Getting Started

### 1. Clone the Repository
```powershell
# Clone this repository
git clone <your-repo-url>
cd openai_proxy
```

### 2. Install Dependencies
```powershell
npm install
```

### 3. Configure Environment Variables
Create a `.env` file in the project root with the following content:
```env
OPENAI_API_KEY=your_openai_api_key_here
PORT=3000
DATABASE_URL=your_postgres_url_here
```
Replace `your_openai_api_key_here` and `your_postgres_url_here` with your actual keys.

### 4. Start the Server
```powershell
node src/index.js
```
You should see:
```
Server running on port 3000
```

---

## API Endpoints

### Health Check
- **GET** `/health`
- **Response:** `{ "status": "OK" }`

### 1. Analyze URL
**POST** `/v1/analyze-url`

Analyze a URL for phishing risk using local DB, external DB (future), and OpenAI.

**Request Body:**
```json
{
  "url": "https://example.com"
}
```

**Response:**
```json
{
  "phishing_score": 0.85,
  "suspicion_reasons": ["Known suspicious domain"],
  "recommended_actions": ["Do not click"],
  "source": "local-db | openai | external-db"
}
```

**Errors:**
- `400 Bad Request` if url is missing or invalid
- `500 Internal Server Error` for unexpected errors

### 2. Analyze Message
**POST** `/v1/analyze-message`

Analyze a full message (text) for phishing risk using OpenAI and DBs.

**Request Body:**
```json
{
  "message": "You won a prize! Click here: http://phish.com"
}
```

**Response:**
```json
{
  "phishing_score": 0.92,
  "suspicion_reasons": ["Suspicious link", "Urgency"],
  "recommended_actions": ["Ignore message"],
  "source": "openai"
}
```

**Errors:**
- `400 Bad Request` if message is missing or too long
- `400 Bad Request` for invalid JSON
- `500 Internal Server Error` for unexpected errors

---

## Example cURL Requests

**Health Check:**
```powershell
curl http://localhost:3000/health
```

**Analyze URL:**
```powershell
curl -X POST http://localhost:3000/v1/analyze-url \
  -H "Content-Type: application/json" \
  -d '{"url": "https://example.com"}'
```

**Analyze Message:**
```powershell
curl -X POST http://localhost:3000/v1/analyze-message \
  -H "Content-Type: application/json" \
  -d '{"message": "You won a prize! Click here: http://phish.com"}'
```

---

## Project Structure
```
openai_proxy/
├── Dockerfile
├── .dockerignore
├── app.yaml
├── package.json
├── .env
├── README.md
├── coverage/
│   └── ...
├── logs/
│   └── dev.log
├── src/
│   ├── index.js
│   ├── config/
│   │   └── db.js
│   ├── middleware/
│   │   └── openai/
│   │       ├── openaiClient.js
│   │       └── prompt.js
│   ├── models/
│   │   └── Domain.js
│   ├── repositories/
│   │   └── domainRepository.js
│   ├── routes/
│   │   └── v1/
│   │       ├── analyzeUrlRoutes.js
│   │       └── analyzeMessageRoutes.js
│   ├── services/
│   │   └── domainService.js
│   └── utils/
│       ├── logger.js
│       └── parseDomain.js
├── tests/
│   ├── analyzeUrlRoutes.test.js
│   ├── analyzeMessageRoutes.test.js
│   ├── domainRepository.test.js
│   ├── domainService.test.js
│   ├── Domain.test.js
│   └── parseDomain.test.js
└── prompt.js
```

---

## Security Notes
- **Never expose your OpenAI API key to the client or commit it to version control.**
- All sensitive logic and API keys remain on the server side.

---

## Docker & Cloud Deployment
- Build and run with Docker:
  ```powershell
  docker build -t openai-proxy .
  docker run --env-file .env -p 3000:3000 openai-proxy
  ```
- Deploy to Google Cloud Run using `app.yaml` and Google Cloud CLI.

---

## Testing
- Run all tests:
  ```powershell
  npm test
  ```
- Run with coverage:
  ```powershell
  npm run test:coverage
  ```

---

## License
This project is provided as-is for educational and demonstration purposes.

---

## Author
Created by sneakyLinky
