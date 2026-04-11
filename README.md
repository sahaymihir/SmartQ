# SmartQ Hospital Queue Management

## Project Overview

**SmartQ** is a comprehensive, full-stack Hospital Queue Management application built to optimize patient flow, reduce waiting room congestion, and empower hospital staff (doctors and admins) with granular control over their daily queues.

The system is composed of:
1. **Frontend:** A native Android Application (written in Java/XML).
2. **Backend:** A scalable Node.js + Express REST API backed by MongoDB.

---

## 🛠️ Technology Stack

### Backend
- **Framework:** Node.js with Express.js
- **Database:** MongoDB (with Mongoose ORM)
- **Authentication:** JSON Web Tokens (JWT) & bcryptjs for secure password hashing.
- **Tools:** CORS, dotenv, Nodemon.

### Frontend
- **Platform:** Android (Java / XML)
- **Architecture:** Activities & XML Layouts using Material Components.
- **Networking:** Retrofit2 for REST API communication.
- **Design:** Custom Drawables, Color-coded Priority Badges, CardViews.

---

## 🚀 Detailed Features List

### 1. Advanced Queue Management Algorithm (Backend Core)
- **Daily Doctor Queues:** Initializes separate daily queues for each doctor.
- **Dynamic ETA Calculation:** Employs a moving average algorithm. It calculates the live Estimated Time of Arrival (ETA) based on the exact duration of the last 10 completed consultations.
- **Smart Triage & Priority System:** 
  - Token priorities are assigned at generation (Normal, Medium, High).
  - Calculated dynamically upon user registration (e.g., Seniors age 60+ receive a base priority bump).
  - **Auto Queue Preemption:** High-priority patients are algorithmically inserted *ahead* of normal-priority patients in real-time, completely bypassing standard FIFO conventions.

### 2. Patient Experience (Android - `PatientHomeActivity`)
- **Real-Time Polling Dashboard:** Actively polls the server every 10 seconds so the patient always sees their *live* ETA, exact queue position, and token number without refreshing.
- **Doctor Selection:** Allows the patient to seamlessly choose which doctor's active queue they want to join.
- **"Snooze" Functionality:** 
  - If a patient is running late, they can hit "Snooze" to gracefully push themselves back by a set number of spots (e.g., 2 spots) instead of losing their ticket completely.
  - Capped at maximum 2 snoozes to prevent queue abuse.
- **Location Check-In:** Features an explicit "Check-In" mechanic (adaptable for implicit Geofencing) so patients can alert the hospital the moment they have physically arrived on the premises.
- **"Called" State:** Instantly updates the UI with a full-screen alert when the doctor officially calls their token.

### 3. Admin & Reception Control (Android - `AdminDashboardActivity`)
- **Bird's Eye Analytics:** Live stats showing the total number of waiting patients, total patients seen today, and the rolling average time per consultation.
- **Color-Coded Queue List:** Displays an intuitive, scrollable list of the entire waiting room. Tokens are color-coded by priority (Red = High, Yellow = Medium, Blue = Normal).
- **One-Tap "Next Patient":** Completes the current consultation and automatically marks the next person in line as "Called", smoothly shifting the entire waiting list forward by one.
- **"No-Show" Management:** Dedicated button to instantly drop unresponsive patients from the queue and instantly notify the back-end to recalculate ETAs for everyone else.
- **Queue Pausing (Break Time):** Allows admins to formally pause the queue (toggled with a badge) if a doctor takes a break or an emergency occurs.

### 4. Doctor View & Prescriptions (Android - `DoctorHomeActivity`)
- **Current Consultation Focus:** A minimalist dashboard strictly showing who is currently in their office (Token number & Name).
- **In-App Prescriptions:** Dedicated UI dialog for doctors to quickly write digital prescriptions comprising:
  - Diagnosis
  - Array of Medicines
  - Additional Notes
  - Automatically ties the prescription to the patient's Consultation History.
- **Availability Toggle:** A simple switch allowing the doctor to mark themselves as "Unavailable" / "Available", syncing directly with the queue's Paused state.

### 5. Security & Authentication
- **Role-Based Access Control:** Distinct roles (`patient`, `admin`, `doctor`) enforced at the MongoDB schema level. 
- **Protected API Routes:** All sensitive endpoints (Queue fetching, Admin actions, Prescriptions) are guarded by a custom JWT Authorization middleware (`protect` / `adminOnly`).

---

## 📂 Project Structure

### Backend (`/backend`)
*   `server.js`: The main entry point mounting express middleware, establishing DB connection, and defining root routes.
*   `models/`: Mongoose schemas.
    *   `User.js`: Base schema for Patients, Admins, Doctors.
    *   `Queue.js`: Contains both `QueueSchema` (daily session state) and `TokenSchema` (individual patient spots/states). Includes moving average logic.
*   `routes/`:
    *   `auth.js`: Registration & JWT Login.
    *   `queue.js`: Patient-facing endpoints (Join, Status, Snooze, Check-in).
    *   `admin.js`: Administrative endpoints (Call Next, No Show, Pause queue, Get full list).

### Frontend (`/frontend`)
*   `app/src/main/java/com/example/smartqueue/`
    *   `models/`: Plain Old Java Objects (POJOs) for API Requests/Responses.
    *   `network/`: Retrofit `ApiClient` instance and the `ApiService` interface containing all endpoint definitions.
    *   `ui/`: Organized by actor role (`patient/`, `admin/`, `doctor/`, `auth/`). Houses Activities & Adapters.
    *   `utils/`: Contains `SessionManager` (SharedPreferences wrapper for JWT & User data cache).
*   `app/src/main/res/`
    *   `layout/`: XML layouts for dashboards, list items, and prescription dialogs.
    *   `drawable/`: XML vector shapes mimicking color-coordinated badges for the priority system.

---

## ⚙️ How Priorities & Snoozes Work (Deep Dive)

**1. The Triage Offset:**
When a patient hits the `/join` endpoint, the server checks their `priorityScore`. If the score is $\ge 10$ (e.g. they are a senior citizen), a background helper function `reorderQueueForPriority()` is invoked. This function iterates through the queue, finds the first element whose priority is "normal", and aggressively slices the High Priority token immediately directly in front of them, effectively cutting the line.

**2. The Snooze Swap:**
When a user hits the `/snooze?positions=2` endpoint, the system modifies their designated queue position (e.g., $P \rightarrow P+2$). It then queries all active waiting tokens that are situated exactly between $P+1$ and $P+2$ and decrements their position by $1$. Finally, the snoozing user is slotted into the $P+2$ index.

---
*Created for personal documentation and system architecture mapping.*
