# SneakyLinky Web UI

A modern React-based admin dashboard for the SneakyLinky URL management system. Built with Vite for fast development.

## 🚀 Features

- **Authentication System** - JWT-based login with automatic token management
- **Domain Management** - Search, create, and manage domains with suspicious status tracking
- **User Reports Dashboard** - Review and classify user-reported URLs
- **Real-time Statistics** - Live stats for domains and user reports
- **Responsive Design** - Mobile-first design
- **Toast Notifications** - User-friendly feedback system
- **Protected Routes** - Secure navigation with authentication guards

## 🛠 Tech Stack

- **React 19** - Modern React with hooks and functional components
- **Vite** - Lightning-fast build tool and dev server
- **React Router DOM** - Client-side routing with protected routes
- **Axios** - HTTP client with request/response interceptors
- **React Icons** - Comprehensive icon library
- **CSS Modules** - Scoped styling with modern CSS features


### Vercel Deployment

The project includes `vercel.json` configuration for seamless SPA deployment:

```json
{
  "rewrites": [
    { "source": "/(.*)", "destination": "/index.html" }
  ]
}
```

## 📁 Project Structure

```
web_ui/
├── public/
│   └── logo.png                    # App logo/favicon
├── src/
│   ├── components/
│   │   └── ProtectedRoute.jsx      # Authentication guard
│   ├── config/
│   │   └── constants.js            # App constants
│   ├── hooks/
│   │   ├── useAuth.js              # Authentication hook
│   │   └── useUserInfo.js          # User info extraction
│   ├── layouts/
│   │   └── AdminLayout/            # Main app layout
│   │       ├── AdminLayout.jsx
│   │       ├── Sidebar.jsx
│   │       ├── Topbar.jsx
│   │       ├── ToastArea.jsx
│   │       ├── ToastContext.jsx
│   │       ├── adminLayout.css
│   │       └── toast.css
│   ├── pages/
│   │   ├── Dashboard/              # Domain management
│   │   │   ├── index.jsx
│   │   │   ├── dashboard.css
│   │   │   └── components/
│   │   │       ├── DashboardGrid.jsx
│   │   │       ├── DomainByNameCard.jsx
│   │   │       ├── DomainCreateCard.jsx
│   │   │       ├── DomainLimitCard.jsx
│   │   │       ├── DomainRow.jsx
│   │   │       ├── DomainStatsCard.jsx
│   │   │       ├── ResultsCard.jsx
│   │   │       └── results.css
│   │   ├── Login/                  # Authentication
│   │   │   ├── index.jsx
│   │   │   ├── login.css
│   │   │   └── components/
│   │   │       └── LoginCard.jsx
│   │   └── UserReports/            # User reports management
│   │       ├── index.jsx
│   │       ├── userreports.css
│   │       └── components/
│   │           ├── UserReportCard.jsx
│   │           ├── UserReportStatsCard.jsx
│   │           └── UserReportsGrid.jsx
│   ├── services/
│   │   ├── apiClient.js            # Axios configuration
│   │   ├── auth.js                 # Authentication API
│   │   ├── domain.js               # Domain management API
│   │   └── userReports.js          # User reports API
│   ├── AppRouter.jsx               # Route configuration
│   ├── main.jsx                    # App entry point
│   └── index.css                   # Global styles
├── .env                            # Environment variables
├── .gitignore
├── eslint.config.js               # ESLint configuration
├── index.html                     # HTML template
├── package.json                   # Dependencies and scripts
├── vercel.json                    # Vercel deployment config
└── vite.config.js                 # Vite configuration
```

## 🔐 Authentication Flow

1. **Login Process**
   - User submits credentials via `LoginCard` component
   - `useAuth` hook calls `/auth/login` endpoint
   - JWT token stored in localStorage
   - Automatic redirect to dashboard

2. **Token Management**
   - Axios interceptor attaches `Authorization: Bearer <token>` to all requests
   - Client-side JWT expiration checking
   - Cross-tab synchronization via storage events
   - Automatic cleanup of expired tokens

3. **Route Protection**
   - `ProtectedRoute` component guards authenticated routes
   - Redirects to login if not authenticated
   - Seamless navigation for authenticated users

## 🏗 Key Components

### Dashboard Features
- **Domain Search** - Find domains by name
- **Domain Creation** - Add new domains with suspicious status
- **Bulk Domain Fetching** - Get domains with sorting and filtering
- **Domain Statistics** - Real-time counts and status overview
- **Domain Management** - Toggle suspicious status, delete domains

### User Reports Features
- **Report Review** - View user-submitted URL reports
- **Admin Classification** - Mark reports as phishing or safe
- **Statistics Dashboard** - Track processing progress
- **Batch Processing** - Efficient report management workflow

### UI/UX Features
- **Responsive Grid Layout** - Adapts to all screen sizes
- **Toast Notifications** - Success, error, and info messages
- **Loading States** - User feedback during API calls
- **Error Handling** - Graceful error recovery
- **Accessibility** - ARIA labels and keyboard navigation

## 📱 Responsive Design

The application is fully responsive with:

- **Mobile-first CSS** - Optimized for mobile devices
- **Flexible Grid System** - Adapts to different screen sizes
- **Touch-friendly UI** - Large tap targets and intuitive gestures
- **Optimized Typography** - Readable text at all sizes


## 🚀 Deployment

### Vercel (Recommended)

1. Connect your Git repository to Vercel
2. Configure build settings:
   - **Build Command**: `npm run build`
   - **Output Directory**: `dist`
3. Set environment variables in Vercel dashboard
4. Deploy automatically on push to main branch

## 🐛 Troubleshooting

### Common Issues

1. **404 on Direct URL Access**
   - Ensure `vercel.json` rewrites are configured

2. **API Connection Issues**
   - Verify `VITE_API_BASE` in `.env`
   - Check CORS configuration on backend
   - Ensure API server is running


