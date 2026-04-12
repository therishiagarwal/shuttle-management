# Shuttle Management System

A RESTful Spring Boot API for managing university shuttle bookings — routes, stops, trips, fare calculation, and wallet-based payments with role-based access control.

---

## Tech Stack

- **Java 17** / **Spring Boot 3.5** / **Maven**
- **Spring Security** + **JWT** (JJWT 0.11.5) — HS256, role-based (ADMIN / STUDENT)
- **H2** file-based database (`jdbc:h2:file:./data/shuttledb`) + **JPA / Hibernate**
- **Lombok** + **Bean Validation** (jakarta.validation)
- **BCrypt** password hashing
- **Caffeine** in-process cache (5-minute TTL, 500 entries max) on routes and stops
- **Spring Boot Actuator** (health, metrics, loggers — ADMIN only)
- **SLF4J** structured logging + logback-spring.xml (console + rolling file appender)

---

## Prerequisites

- Java 17+
- Maven 3.8+

---

## Running the Application

```bash
# Clone the repository
git clone <repo-url>
cd shuttle-management

# Run with Maven
./mvnw spring-boot:run
```

The application starts on **http://localhost:8080**.

### H2 Console

The H2 web console is available at **http://localhost:8080/h2-console** (no JWT required).

| Field    | Value                           |
|----------|---------------------------------|
| JDBC URL | `jdbc:h2:file:./data/shuttledb` |
| Username | `sa`                            |
| Password | *(empty)*                       |

---

## Running Tests

```bash
./mvnw test
```

22 tests total, all green:
- `TripServiceTest` — 10 unit tests (pure Mockito)
- `StudentServiceTest` — 6 unit tests (pure Mockito)
- `BookingFlowIntegrationTest` — 5 integration tests (@SpringBootTest, in-memory H2)
- `ShuttleManagementApplicationTests` — 1 context load test

---

## Project Structure

```
src/main/java/com/movinsync/shuttlemanagement/
├── config/          SecurityConfig.java
├── controller/      AuthController, StudentController, StopController,
│                    RouteController, TripController, AdminController
├── dto/             LoginRequest, BookingResult, RouteOptimizationResult,
│                    TransferResult, NearestStopResult, FrequentRouteResult,
│                    ExpenseReportResult (+ inner TripSummary)
├── exception/       GlobalExceptionHandler
├── model/           Student, Wallet, Stop, Route, Trip, Role (enum), TripStatus (enum)
├── repository/      StudentRepository, WalletRepository, StopRepository,
│                    RouteRepository, TripRepository
├── service/         StudentService, FareCalculatorService, StopService,
│                    RouteService, TripService, RouteOptimizationService,
│                    BusTransferService
├── util/            JwtUtil, JwtFilter
└── validation/      UniversityEmail (annotation), UniversityEmailValidator
```

---

## Configuration

Key properties in `src/main/resources/application.properties`:

| Property                                    | Default / Notes                      |
|---------------------------------------------|--------------------------------------|
| `fare.rate-per-km`                          | `2` (points per km)                  |
| `fare.peak.morning-start`                   | `7`                                  |
| `fare.peak.morning-end`                     | `9`                                  |
| `fare.peak.evening-start`                   | `17`                                 |
| `fare.peak.evening-end`                     | `19`                                 |
| `fare.peak.multiplier`                      | `1.5`                                |
| `trip.cancellation.full-refund-minutes`     | `10`                                 |
| `university.email.domain`                   | `@university.edu`                    |
| `jwt.secret`                                | Set via env var `JWT_SECRET` in prod |
| `jwt.expiration-ms`                         | `36000000` (10 hours)                |
| `spring.cache.type`                         | `caffeine`                           |
| `cache.ttl-seconds`                         | `300` (5 minutes)                    |
| `management.endpoints.web.exposure.include` | `health,metrics,loggers`             |

---

## API Reference

All endpoints except `/api/auth/**` and `/h2-console/**` require an `Authorization: Bearer <token>` header.
Endpoints under `/api/admin/**` and `/actuator/**` additionally require the ADMIN role.

---

### Authentication

#### Login
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"student@university.edu","password":"password123"}'
```
Response:
```json
{"token": "eyJhbGciOiJIUzI1NiJ9..."}
```

---

### Students

#### Create a student
```bash
curl -X POST http://localhost:8080/api/students \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{"name":"Alice","email":"alice@university.edu","password":"pass123","role":"STUDENT"}'
```

#### Get all students
```bash
curl http://localhost:8080/api/students \
  -H "Authorization: Bearer <token>"
```

---

### Stops

#### Create a stop
```bash
curl -X POST http://localhost:8080/api/stops \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{"name":"Main Gate","latitude":12.9716,"longitude":77.5946}'
```

#### Get all stops (cached)
```bash
curl http://localhost:8080/api/stops \
  -H "Authorization: Bearer <token>"
```

#### Update a stop
```bash
curl -X PUT http://localhost:8080/api/stops/1 \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{"name":"Main Gate Updated","latitude":12.9720,"longitude":77.5950}'
```

#### Delete a stop
Returns HTTP 409 if the stop is currently assigned to any route.
```bash
curl -X DELETE http://localhost:8080/api/stops/1 \
  -H "Authorization: Bearer <token>"
```

#### Find nearest stops
```bash
curl "http://localhost:8080/api/stops/nearest?lat=12.97&lng=77.59&limit=3" \
  -H "Authorization: Bearer <token>"
```

---

### Routes

#### Create a route
```bash
curl -X POST http://localhost:8080/api/routes \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{"routeName":"Route A","stops":[]}'
```

#### Get all routes (cached)
```bash
curl http://localhost:8080/api/routes \
  -H "Authorization: Bearer <token>"
```

#### Update a route
```bash
curl -X PUT http://localhost:8080/api/routes/1 \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{"routeName":"Route A Updated","stops":[]}'
```

#### Delete a route
```bash
curl -X DELETE http://localhost:8080/api/routes/1 \
  -H "Authorization: Bearer <token>"
```

#### Add a stop to a route
```bash
curl -X POST "http://localhost:8080/api/routes/1/stops/2" \
  -H "Authorization: Bearer <token>"
```

#### Remove a stop from a route
Enforces a minimum of 1 stop per route.
```bash
curl -X DELETE "http://localhost:8080/api/routes/1/stops/2" \
  -H "Authorization: Bearer <token>"
```

#### Optimise route between two stops (Dijkstra)
```bash
curl "http://localhost:8080/api/routes/optimize?fromStopId=1&toStopId=5" \
  -H "Authorization: Bearer <token>"
```

#### Find a transfer route (shared-stop algorithm)
```bash
curl "http://localhost:8080/api/routes/transfer?fromStopId=1&toStopId=5" \
  -H "Authorization: Bearer <token>"
```

---

### Trips

#### Book a trip
Fare is calculated server-side (Haversine distance + peak-hour multiplier). The wallet is debited automatically.
```bash
curl -X POST "http://localhost:8080/api/trips/book?studentId=1&fromStopId=1&toStopId=2" \
  -H "Authorization: Bearer <token>"
```

#### Cancel a trip
Full refund to wallet if cancelled within 10 minutes of booking; no refund after that window.
```bash
curl -X DELETE "http://localhost:8080/api/trips/3/cancel?studentId=1" \
  -H "Authorization: Bearer <token>"
```

#### Get all trips for a student
```bash
curl http://localhost:8080/api/trips/student/1 \
  -H "Authorization: Bearer <token>"
```

#### Get total fare spent by a student
```bash
curl http://localhost:8080/api/trips/student/1/total-fare \
  -H "Authorization: Bearer <token>"
```

#### Get most frequent routes for a student
```bash
curl "http://localhost:8080/api/trips/student/1/frequent-routes?limit=3" \
  -H "Authorization: Bearer <token>"
```

#### Get expense report
Supported periods: `monthly` (current calendar month), `weekly` (last 7 days).
```bash
curl "http://localhost:8080/api/trips/student/1/expense-report?period=monthly" \
  -H "Authorization: Bearer <token>"
```

---

### Admin (ADMIN role required)

#### Allocate points to a student's wallet
```bash
curl -X POST "http://localhost:8080/api/admin/allocate-points?studentId=1&points=100" \
  -H "Authorization: Bearer <admin-token>"
```

#### Deduct points from a student's wallet
```bash
curl -X POST "http://localhost:8080/api/admin/deduct-points?studentId=1&points=50" \
  -H "Authorization: Bearer <admin-token>"
```

---

### Actuator (ADMIN role required)

```bash
curl http://localhost:8080/actuator/health \
  -H "Authorization: Bearer <admin-token>"

curl http://localhost:8080/actuator/metrics \
  -H "Authorization: Bearer <admin-token>"

curl http://localhost:8080/actuator/loggers \
  -H "Authorization: Bearer <admin-token>"
```
