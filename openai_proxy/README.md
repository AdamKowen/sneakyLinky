# App Server

A Node.js Express server that providing phishing detection services for URLs and messages.

## Features

- **URL Analysis**: Analyze URLs for phishing risks using OpenAI's models
- **Message Analysis**: Analyze full messages (email, SMS) for phishing content
- **Domain Management**: CRUD operations for domain whitelists/blacklists
- **User Reports System**: Collect and manage user-reported suspicious URLs
- **External Integrations**: Google Safe Browsing API integration
- **Admin Authentication**: JWT-based authentication for admin operations
- **Scheduled Tasks**: Weekly hotset updates for domain lists
- **Database**: SQLite with Sequelize ORM
- **Logging**: Comprehensive logging with Winston
- **Testing**: test suite with Jest

## API Endpoints

### URL Analysis
```
POST /v1/analyze-url
Content-Type: application/json

{
  "url": "https://example.com"
}
```
Analyzes a URL for phishing risks using OpenAI and returns a detailed assessment.

### Message Analysis
```
POST /v1/analyze-message
Content-Type: application/json

{
  "message": "Click here to claim your prize: http://suspicious-site.com"
}
```
Analyzes full message content for phishing indicators.

### Domain Management
```
GET /v1/domain/stats           # Get domain statistics
GET /v1/domain/:name           # Search domain by name
POST /v1/domain                # Create new domain entry
PATCH /v1/domain/:name         # Update domain suspicious flag
DELETE /v1/domain/:name        # Delete domain
GET /v1/domain?limit=N         # Get domains with limit
```

### User Reports
```
GET /v1/userReports/stats                    # Get reports statistics
GET /v1/userReports?limit=N                  # Get top unreviewed reports
GET /v1/userReports/:id                      # Get specific report
POST /v1/userReports                         # Create new report
PATCH /v1/userReports/:id/adminDecision      # Update admin decision
DELETE /v1/userReports                       # Delete reports by IDs
```

### Authentication
```
POST /v1/auth/login           # Admin login
```

## Example Usage

**Analyze URL:**
```bash
curl -X POST http://localhost:3000/v1/analyze-url \
  -H "Content-Type: application/json" \
  -d '{"url": "https://example.com"}'
```

**Analyze Message:**
```bash
curl -X POST http://localhost:3000/v1/analyze-message \
  -H "Content-Type: application/json" \
  -d '{"message": "You won a prize! Click here: http://phish.com"}'
```


## Project Structure

```
src/
├── config/
│   └── db.js                    # Database configuration
├── middleware/
│   ├── openai/
│   │   ├── openaiClient.js      # OpenAI API integration
│   │   └── prompt.js            # Analysis prompts
│   └── externalDB/
│       └── gsbClient.js         # Google Safe Browsing
├── models/
│   ├── Admin.js                 # Admin user model
│   ├── Domain.js                # Domain model
│   ├── DomainHotset.js         # Domain hotset model
│   └── UserReports.js          # User reports model
├── repositories/
│   ├── adminRepository.js       # Admin data layer
│   ├── domainRepository.js      # Domain data layer
│   ├── domainHotsetRepository.js
│   └── userReportsRepository.js
├── routes/
│   └── v1/
│       ├── analysis/
│       │   ├── analyzeUrlRoutes.js
│       │   └── analyzeMessageRoutes.js
│       ├── auth/
│       │   └── authRoutes.js
│       ├── domain/
│       │   └── domainRoutes.js
│       └── userReports/
│           └── userReportsRoutes.js
├── services/
│   ├── adminService.js          # Admin business logic
│   ├── domainService.js         # Domain business logic
│   ├── domainHotsetService.js   # Hotset management
│   └── userReportsService.js    # Reports business logic
├── utils/
│   ├── logger.js                # Winston logger setup
│   └── parseDomain.js           # Domain parsing utilities
├── HotsetScheduler.js           # Weekly scheduled tasks
└── index.js                     # Main application entry point
```


## Database Models

### Domain
- `name`: Domain name (unique)
- `suspicious`: Boolean flag for suspicious domains
- `access_count`: Counts how many times this domain was fetched
- `createdAt/updatedAt`: Timestamps

### UserReports
- `url`: Reported URL
- `systemClassification`: AI classification (0=safe, 1=phishing)
- `userClassification`: User classification (0=safe, 1=phishing)
- `userReason`: User's reason for reporting
- `adminDecision`: Admin review decision (0=safe, 1=phishing)
- `reportCount`: Number of times reported

### Admin
- `email`: Admin email (unique)
- `password`: Hashed password
- `createdAt/updatedAt`: Timestamps

## Testing

Run the test suite:
```bash
npm test
```

Run tests with coverage:
```bash
npm run test:coverage
```
