# Roam

> **Discover the story behind every place.**

Roam is an Android application that turns a smartphone into a personal, AI-powered tourist guide. Travelers can simply take a picture of a monument or landmark and receive a concise guide explaining what it is, its history, interesting facts, and its cultural significance.

Instead of searching the web, reading long articles, or joining a guided tour, Roam makes discovering a place as simple as taking a photo.

---

## Overview

Roam is designed for travelers who want to explore and learn at their own pace.

The application primarily targets:

- **Curious explorers**, who enjoy learning about the history and cultural significance of the places they visit.
- **Independent travelers**, who prefer exploring without relying on organized guided tours.
- **Casual tourists**, who want quick and accessible information without reading lengthy articles.

The core interaction is intentionally simple:

1. The user takes a picture of a monument or landmark.
2. Roam optionally retrieves the user's approximate location.
3. The image, location, and user preferences are sent to a vision-capable AI model.
4. The monument is identified.
5. Roam generates a concise and personalized tourist guide.
6. The user can save the discovery to their personal collection.

---

## Core Features

### Monument Discovery

Users can photograph a monument or landmark directly from the application.

Roam uses the photograph as the main input for monument identification. If the user grants location permission, approximate GPS coordinates are also provided to improve identification, particularly for visually similar landmarks.

Once the monument has been identified, Roam generates a tourist guide containing information such as:

- Monument name
- Historical background
- Interesting facts
- Cultural context

The generated content can be adapted to the user's preferences.

### Personal Collection

Users can save discoveries to their personal collection.

A saved discovery contains:

- The monument name
- The generated guide
- The monument photograph
- Location information, when available
- Discovery date
- Favorite status

Users can browse their collection and revisit previously discovered monuments.

### Personalization

Users can configure preferences reflecting the type of information they are interested in.

These preferences are included when generating guides so that Roam can adapt its content to different types of travelers.

### Social Features

Roam supports multiple users and allows travelers to share discoveries with friends.

Users can:

- Add friends using a username or share link
- Share selected discoveries with selected friends
- Browse discoveries shared by friends
- Like shared discoveries
- Comment on shared discoveries
- Save a friend's shared discovery to their own collection

A user's collection is **private by default**. Sharing is always an explicit action by the user.

When a monument is shared, only city-level location information is exposed to friends.

### Offline Mode

Roam is designed to remain useful while traveling with limited or unavailable connectivity.

Previously saved discoveries remain available offline, including their:

- Photos
- Generated guides
- Locations
- Dates
- Favorite status

Users can browse their collection and modify supported local data while offline. Changes are synchronized once connectivity returns.

Features that inherently require cloud access are unavailable offline, including:

- Scanning and identifying a new monument
- Generating a new AI guide
- Loading the social feed
- Sharing discoveries
- Likes and comments

---

## Architecture

Roam follows an Android client + managed cloud services architecture and does not require a custom application backend.

The Android application communicates with Firebase services for authentication, persistence, synchronization, and AI access.

### Technology Stack

| Component | Technology |
|---|---|
| Platform | Android |
| Cloud platform | Firebase |
| Authentication | Firebase Authentication |
| Database | Cloud Firestore |
| AI | Gemini via Firebase AI Logic |
| Location | Android location services / GPS |
| Image input | Smartphone camera |
| Offline persistence | Cloud Firestore offline persistence |
| Future audio guide | Android Text-to-Speech |

### AI Integration

Roam uses a vision-capable Gemini model through the **Firebase AI Logic SDK**.

The AI request can contain:

- The monument photograph
- GPS coordinates, if permission has been granted
- The user's preference profile

The model uses this context to identify the monument and generate an appropriate tourist guide.

AI access is performed through Firebase rather than by embedding a Gemini API key directly in the Android application. Firebase App Check is used to restrict access to legitimate application instances.

---

## Data Storage

Cloud Firestore stores application data such as:

- User profiles
- Saved monuments
- Friend relationships
- Shared discoveries
- Likes
- Comments

### Images

Saved monument photographs are stored as compressed binary data using Firestore's native `bytes` type.

Before storage, photographs are resized to approximately **1024 px on their longest side** and compressed as JPEG images.

Each photograph is stored separately from its associated monument data. This prevents browsing the collection from automatically downloading every stored image.

The application must ensure that stored documents remain below Firestore's document-size limit.

---

## Authentication and Privacy

Authentication is handled through Firebase Authentication.

Google Sign-In is the primary authentication method. Email/password authentication may also be supported.

Firestore Security Rules are used to isolate user data. Private collections must only be accessible to their respective owners unless the user explicitly shares a discovery.

Roam follows the following privacy principles:

- Collections are private by default.
- Sharing is opt-in.
- Location permission is optional.
- Monument scanning still works when location permission is denied.
- Shared discoveries expose only the location information intended for sharing.

---

## Sensors

Roam makes use of smartphone sensors to improve the discovery experience.

### Camera

The camera is the primary sensor used by Roam. A photograph of a monument or landmark provides the main input for AI-based identification.

### Location

With the user's permission, Roam retrieves an approximate GPS location when a photograph is taken.

Location information can:

- Improve monument identification
- Help distinguish visually similar landmarks
- Be associated with saved discoveries
- Support future location-based features

Location access is optional, and the core scanning flow remains available when permission is denied.

---

## Future Extensions

The initial version focuses on the core monument discovery, collection, personalization, offline, and social experiences.

Possible future extensions include:

### Audio Guides

Generated guides could be read aloud using Android's built-in Text-to-Speech capabilities, turning Roam into a personalized audio guide without requiring an additional cloud service.

### Discovery Map

Saved monument locations could be displayed on a map, allowing users to visualize their discoveries and potentially those shared by friends.

These features are considered extensions and are not part of the initial core functionality.

---

## Project Management

Development is organized using Scrum.

Our GitHub Project contains:

- **Product Backlog** — initial user stories and future functionality
- **Sprint Backlog** — tasks selected for the current Sprint
- **In Development** — tasks currently being implemented
- **In Review** — tasks awaiting or undergoing code review
- **Done in S1–S10** — completed tasks organized by Sprint

User stories are grouped into the following epics:

- Monument Discovery
- Collection
- Authentication
- Profile & Personalization
- Social
- Offline Experience

---

## Design

The user interface and user experience are designed collaboratively in Figma.

**Figma:** https://www.figma.com/files/folder/662421157

---

## Getting Started

### Prerequisites

Before building Roam, make sure you have:

- Android Studio
- A compatible Android SDK
- JDK configured for the project
- Access to the project's Firebase configuration

### Clone the Repository

```bash
git clone https://github.com/ROAM-SWENT/roam-android.git
cd roam-android
```

### Build the Project

```bash
./gradlew build
```

### Run Checks

Before submitting a pull request, run:

```bash
./gradlew check
```

The project can then be opened and run from Android Studio on a compatible emulator or Android device.

---

## Development Workflow

Development is performed through feature branches and pull requests.

Direct pushes to `main` are not allowed. Changes must be reviewed before being merged, and required CI checks must pass.

Detailed development conventions and instructions for coding agents are documented in [`AGENTS.md`](./AGENTS.md).

Team processes and project decisions are documented in the repository Wiki.

---

## Continuous Integration

The repository uses GitHub Actions for continuous integration.

CI verifies the project through automated checks and tests before changes are merged into `main`.

Code quality is additionally monitored using SonarCloud.

---

## Team

Roam is developed by a team of seven students as part of the **Software Enterprise (SwEnt)** course at EPFL.

| Team Member | GitHub |
|---|---|
| Valentin | @vwalendy |
| Augustin | @augustin168 |
| Florian | @FlorianSchreiber |
| Ermias Berhe | @ErmiasBRH |
| Hao Tuan Nguyen | @swyker |
| Alex | @Alex-epfl |
| Aurélien |  |

---

## Acknowledgements

This project is developed as part of the EPFL Software Enterprise course with guidance from the SwEnt teaching and coaching staff.
