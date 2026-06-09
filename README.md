# Construction HRMS: Attendance & Overtime Settlement Engine

## 1. Project Overview & Fork Information
This repository is an extension of the **Amigoscode Spring Boot Fullstack Professional** base Employee Management System architecture. It has been customized to fulfill the unique scheduling and high-concurrency needs of blue-collar construction workforce management—specifically handling shift-heavy attendance logs, real-time tracking, and automated transactional payroll settlement structures.

---

## 2. Core Architectural Design Decisions & Tradeoffs

### Data Integrity & Schema Normalization
* **Managed Constraints:** Constraints are managed rigorously at the database layer using JPA/Hibernate mapping rules.
* **Cascading Foreign Keys:** Implemented cascading foreign key constraints from the `attendance_logs` table to the `overtime_entries` table. This prevents isolated or dangling financial tracking records, ensuring that every cent computed for overtime maps explicitly to an authenticated, physical shift entry.
* **Edge Case Validation:** Handled empty dataset edge cases explicitly by mapping structured domain exceptions (e.g., throwing a `NO_ENTRIES_FOUND` exception instead of a generic 500 error) when executing settlements across intervals with no active data.

### Dual Data-Storage & Graceful Cache Resilience
* **High-Concurrency Reads:** Leveraged Redis caching for hot endpoints such as `GET /api/attendance/active` so site supervisors managing large crews on physical locations can check clock-in states instantly with ultra-low latency.
* **Cache Fallback Security Net:** Configured custom `CacheErrorHandler` hooks alongside standard Spring Cache operations. If the Redis server experiences structural downtime or network timeout drops on-site, the application gracefully degrades to querying the cloud Supabase Postgres instance directly. This ensures the application remains online to clock workers in without throwing unexpected errors.
* **Missed Clock-Out Protection:** Configured a strict 16-hour Time-To-Live (TTL) constraint on active attendance cache slots to automatically purge stale tracking states from workers who forgot to clock out at the end of their shift.

### Decoupled Notification & Transaction Boundaries
* **Preventing Duplicate Notification Leakage:** Implemented an event-driven mechanism for handling external SMS notifications.
* **Lifecycle Synchronization:** Using Spring's `ApplicationEventPublisher` and binding listeners to the transactional lifecycle via `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`, the system guarantees that worker notification events fire only after the database successfully commits and locks the financial changes into Supabase. If a database rollback occurs midway through settlement processing, no premature or inaccurate SMS notifications are sent out.

---

## 3. Cloud Database Configuration Setup (Supabase)
The application connects to a managed, cloud-hosted Supabase PostgreSQL instance. Depending on your local network environment and DNS stability to AWS container hubs, update your `src/main/resources/application.properties` with one of the following validated layouts:

### Option A: Direct Infrastructure Connection (Port 5432)
*Best for standard setups where local DNS routing handles direct DB handshakes seamlessly.*
```properties
spring.datasource.url=jdbc:postgresql://db.xvwvjzfvwuxrkdkdjjds.supabase.co:5432/postgres
spring.datasource.username=postgres
spring.datasource.password=P0stgr3@sqlttle/101?month=2026-06"
```

### Option B: Best For Routing Applications (Port 6543)
```
spring.datasource.url=jdbc:postgresql://[aws-1-ap-south-1.pooler.supabase.com:6543/postgres](https://aws-1-ap-south-1.pooler.supabase.com:6543/postgres)
spring.datasource.username=postgres.xvwvjzfvwuxrkdkdjjds
spring.datasource.password=P0stgr3@sql
```

## 4. Documentation and AI Tool Usage
During this development sprint, **Gemini** was utilized as an AI pair-programming assistant to accelerate systemic auditing and debugging:
* **Stack Trace Resolution:** Assisted in analyzing and resolving complex runtime Hibernate exceptions, including handling direct vs. pooled Postgres authentication credential parsing (`PSQLException: FATAL: password authentication failed`) and mapping module environment-specific variables.
* **Architectural Isolation:** Provided code structure guidance on setting up decoupled application event models to cleanly isolate external I/O tasks from transactional business boundaries.

### REST API Endpoints & `curl` Examples
* ** Worker clock in
 `curl -X POST http://localhost:8081/api/attendance/clock-in \
  -H "Content-Type: application/json" \
  -d '{"workerId": 101, "siteId": 5}'
  `
 * ** Worker clock out
  `
  curl -X POST http://localhost:8081/api/attendance/clock-out \
  -H "Content-Type: application/json" \
  -d '{"workerId": 101}'
  `
 * ** Fetch Active Workers (Low Latency Cache)
  `
  curl -X GET http://localhost:8081/api/attendance/active
  `
 * ** Fetch Overtime Summary
  `
  curl -X GET "http://localhost:8081/api/overtime/summary/101?month=2026-06"
  `
  * *** Settlement Overtime Processing(Atomic Transaction)
  `
  curl -X POST "http://localhost:8081/api/overtime/settle/101?month=2026-06"
  `
