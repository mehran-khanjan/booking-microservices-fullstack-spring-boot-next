# PRD: Booking App

**Version:** 1.0

## 1. Problem & Goal
Users need a simple and reliable way to search, compare, and book flights end-to-end (search → select → pay → confirmed booking), along with an account to manage past and upcoming trips.

**Goal:** Ship a production-ready MVP that supports real flight search and real payment processing.

## 2. Users
- **User (primary):** Searches for flights, books tickets, and manages their own trips.
- **Admin:** Manages the application (users, content, configurations, etc.).

## 3. Core Features
- **Auth:** Email/password, phone/password, and Google login; JWT session management; email verification; phone verification; password reset.
- **Flight Search:** Origin/destination, travel dates, number of passengers, and cabin class.
- **Checkout:** Passenger details entry and fare re-validation (price/availability check) prior to payment.
- **Payment:** Stripe integration.
- **My Trips:** List upcoming and past bookings, view booking details, and cancel bookings.

## 4. Tech Stack
- **Frontend:** Next.js (App Router), TypeScript.
- **Backend:** Spring Boot 4.x (Java 21), Keycloak and Spring Security (JWT), Spring Data JPA.
- **DB:** PostgreSQL.
- **Cache/Queue:** Redis (search cache, idempotency keys), RabbitMQ.
- **Infra:** Docker, CI/CD (GitHub Actions), deployed on AWS/GCP behind HTTPS/CDN.

## 5. Commenting
Use Javadoc for code documentation. Run the following command to generate the output:

```bash
mvn javadoc:javadoc
```

## 6. Git Rules
- A PR is required to merge into `master` (no direct pushes).
- CI (build + test) must pass before the merge.
- Squash merge only.

**Valid Branch Names:**

- `feature/` – New user story or functionality.
- `bugfix/` – Fixing a defect found in testing.
- `hotfix/` – Emergency fix for production (branched from `main`/`master`).
- `refactor/` – Code cleanup that changes no behavior (important for Spring Boot upgrades) or adds new tests.
- `chore/` – Tooling, dependency updates, CI config, or build scripts (no product code change).
- `docs/` – Documentation-only changes.
- `master`/`main` – Production branch.

**Branch Name Structure:**
Use the following structure for branch naming:
`feature/[jira-name]-[jira-ticket-id]-[user-story-title]`

**Example:**
`feature/booking-100-user-registration`

```bash
# Create a branch
git checkout -b feature/booking-99-user-registration

# Push to a branch
git push -u origin feature/booking-99-user-registration
```

**Valid Commit Types:**
- `feat` – New feature for the user (triggers a minor version bump).
- `fix` – Bug fix for the user (triggers a patch version bump).
- `refactor` – Code restructuring that introduces no external behavioral changes (no bug fix, no new feature).
- `chore` – Maintenance tasks that don’t modify source code (e.g., dependency updates, configs).
- `docs` – Documentation changes only (README, Javadoc, Swagger/OpenAPI specs).
- `test` – Adding or correcting tests (unit, integration). No production code change.
- `perf` – Performance improvement (faster, less memory). A specialized refactor.
- `ci` – Changes to CI/CD pipeline scripts (GitHub Actions, Jenkins, GitLab CI).
- `build` – Changes affecting the build system or dependencies (Maven/Gradle plugins, Dockerfile).
- `style` – Code formatting/whitespace changes. No logic affected (linters, semicolons, spaces).

**Commit Message Structure:**
Use this structure for each commit message:
`[commit-type(commit-context): commit-title]

[commit explanation]

[Refs: user-story-code, acceptance-criteria-code]`

**Example:**
`feat(auth): implement sign-up with email/phone

Adds registration endpoint, validation, and password hashing.
Refresh token TTL configurable via application.yml (5-15 min).

Refs: us-1.1, ac-1.1.1`

**Tagging Structure:**
Tag each service release as follows: `<service-name>-vMAJOR.MINOR.PATCH`

## 7. CI/CD
Using GitHub Actions with AWS.

## 8. Non-Functional Requirements
- **Architecture:** Microservices.

## 9. Functional Requirements

### 1. Backend User Stories

#### EPIC 1: User Management Service

### User Stories

**Story 1.1: User Registration (Ticket ID 99)**
- **As a** new user,
- **I want to** create an account using email or phone,
- **So that** I can book flights and manage my reservations.

**Acceptance Criteria:**
- 1.1.1 User can sign up with email.
- 1.1.2 User can sign up with phone
- 1.1.3 User can sign in by email
- 1.1.4 User can sign in by phone
- 1.1.5 User can sign in and sing up by Google
- 1.1.6 User can sign out
- 1.1.7 User can forget password
- 1.1.8 User can change password
- 1.1.9 Refresh token
- 1.1.10 Email/SMS verification required before account activation

#### EPIC 2: Flight Search Service

### User Stories

**Story 2.1: Basic Flight Search (Ticket ID 100)**
- **As a** user
- **I want to** search for flights by origin, destination, and date
- **So that** I can find available flights for my trip

**Acceptance Criteria:**
- 2.1.1 Search with origin airport, destination airport, departure date
- 2.1.2 Support for return date (round-trip) or one-way
- 2.1.3 Cache popular routes for performance

#### EPIC 3: Booking Service

### User Stories

**Story 3.1: Create Flight Booking (Ticket ID 101)**
- **As a** user
- **I want to** book selected flight(s)
- **So that** I can secure my seat on the flight

**Acceptance Criteria:**
- 3.1.1 Create booking with selected flight details
- 3.1.2 Hold reservation for 15 minutes before payment
- 3.1.3 Generate unique booking reference (PNR)
- 3.1.4 Validate seat availability in real-time
- 3.1.5 Support single and round-trip bookings
- 3.1.6 Integration with payment gateways (Stripe, PayPal, Razorpay)
- 3.1.7 Support credit/debit cards (Visa, Mastercard, Amex)
