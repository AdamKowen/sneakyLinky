# 🔒 LinkShield – Smart Link Protection for Android

LinkShield is an Android application designed to protect users from phishing attempts and malicious links. It intercepts link clicks across the system, checks the URLs against a database of known threats, and provides real-time alerts before the link is opened.

## 🚀 Project Goals

- Provide an additional security layer against suspicious links on Android devices.
- Detect potentially dangerous links before they are opened in the browser.
- Allow users to make informed decisions about continuing or blocking a link.

## 🧩 Key Features

- **Link Interception**: Monitors and detects link clicks across various apps.
- **Link Analysis**: Compares URLs to a local or remote database of malicious links.
- **User Alerts**: Displays a warning when a suspicious link is detected, allowing users to proceed or block.
- **History Tracking**: Stores a record of intercepted links and user decisions.
- **Admin Interface**: Enables monitoring and adjusting detection logic (under development).
- **Custom Browser Handling**: Redirects safe links to the user’s chosen browser.

## 📱 User Experience

- Lightweight notifications during link checks.
- Warning screens for flagged URLs with clear choices for users.
- Simple, minimal UI for managing history and settings.

## 🛠️ Technologies

- **Language**: Kotlin / Java (Android)
- **Local Storage**: SQLite
- **Networking**: HTTPS, Retrofit
- **Optional Integration**: External APIs like VirusTotal or PhishTank

## 📂 Project Structure

client_app/ # Android source code
server_app/ # Backend (optional, for centralized URL analysis)
README.md # This file




## 📄 Specification

For more details, see the full [project specification PDF](https://drive.google.com/file/d/1vIAxRfvUESrdRe4pEfNF_BwLmoTj9tek/view?usp=sharing).
