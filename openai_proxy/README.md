# OpenAI Proxy Server

A secure Node.js Express server that acts as a proxy for OpenAI API requests. This project is designed to keep your OpenAI API key safe on the server side, while providing endpoints for analyzing messages (such as URLs or text) for phishing risk using OpenAI's models.

---

## Features
- **Secure API Key Handling:** The OpenAI API key is stored in a `.env` file and never exposed to the client.
- **Phishing Risk Analysis:** Analyze messages or URLs for phishing risk using a dedicated `/analyze-url` endpoint.
- **Health Check:** Simple `/health` endpoint for monitoring server status.
- **Modular Code:** All routes and OpenAI logic are separated for easy maintenance and extension.
- **JSDoc Documentation:** All code is documented for clarity and maintainability.

---

## Getting Started

### 1. Clone the Repository
```bash
# Clone this repository (if needed)
git clone <your-repo-url>
cd openai_proxy
```

### 2. Install Dependencies
```bash
npm install
```

### 3. Configure Environment Variables
Create a `.env` file in the project root with the following content:
```env
OPENAI_API_KEY=your_openai_api_key_here
PORT=3000
```
Replace `your_openai_api_key_here` with your actual OpenAI API key.

### 4. Start the Server
```bash
node src/index.js
```
You should see:
```
OpenAI Proxy server running on port 3000
```

---

## API Endpoints

### Health Check
- **GET** `/health`
- **Response:** `{ "status": "OK" }`

### Analyze Message/URL for Phishing Risk
- **POST** `/analyze-url`
- **Request Body:**
  ```json
  {
    "message": "Check this link: https://www.google.com"
  }
  ```
- **Response:**
  ```json
  {
    "phishing_score": 0.10,
    "suspicion_reasons": [
      "This is Google's official website",
      "The address is well-known and normal"
    ],
    "recommended_actions": []
  }
  ```
  *The response format may vary depending on the message and OpenAI's analysis.*

---

## Project Structure
```
openai_proxy/
├── src/
│   ├── index.js           # Main server entry point
│   ├── route.js           # All Express route definitions
│   ├── openaiClient.js    # Handles OpenAI API communication
│   └── prompt.js          # Contains the phishing analysis prompt
├── package.json           # Project metadata and dependencies
├── .env                   # Environment variables (not committed)
└── README.md              # Project documentation
```

---

## Security Notes
- **Never expose your OpenAI API key to the client or commit it to version control.**
- All sensitive logic and API keys remain on the server side.

---

## Example cURL Requests

**Health Check:**
```bash
curl http://localhost:3000/health
```

**Analyze Message:**
```bash
curl -X POST http://localhost:3000/analyze-url \
  -H "Content-Type: application/json" \
  -d '{ "message": "Check this link: https://www.google.com" }'
```

---

## License
This project is provided as-is for educational and demonstration purposes.

---

## Author
Created by sneakyLinky
