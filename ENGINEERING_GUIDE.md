# Engineering Guide — Shuttle Management System

This guide is written for an engineer who co-authored the code and wants to understand it deeply enough to explain every design decision, algorithm, and trade-off confidently in a technical interview. Read it end-to-end at least once, then use individual sections as a reference before specific interview questions.

---

## 1. What This System Does

The Shuttle Management System is a backend API that automates the logistics of a university shuttle service. Without it, students would need to pay fares with physical cash or cards, administrators would track routes manually, and there would be no data-driven visibility into usage patterns. The system replaces all of that with a set of REST endpoints that any front-end or mobile app can consume.

From the student's perspective the core workflow is: register an account, receive wallet points from an administrator, find which stops are near you, book a trip from one stop to another, and get fare automatically deducted from your wallet. If plans change, you can cancel within 10 minutes for a full refund. Students can also look up their trip history, see which routes they use most often, and get a weekly or monthly expense summary.

From the administrator's perspective, the system provides tools to define the physical network (stops with GPS coordinates and routes made up of ordered stops), to top up or deduct student wallets, and to observe system health through Spring Boot Actuator endpoints. Two higher-level features sit on top of the physical network: a Dijkstra-based route optimiser that finds the shortest path between any two stops, and a bus-transfer finder that identifies a change-of-bus stop when no single route connects origin to destination.

---

## 2. Architecture Overview

The application follows a classic three-layer architecture: Controller, Service, and Repository. Each layer has one job and is not allowed to reach past its immediate neighbour.

**Controller layer** handles the HTTP boundary. Its only responsibilities are to accept a request, extract parameters or body, call a service method, and serialise the result back to JSON. Controllers know nothing about database queries or business rules. When validation fails (a missing required field, a malformed email), the `GlobalExceptionHandler` intercepts the exception before it reaches the HTTP response and converts it to a structured 400 response.

**Service layer** is where all business logic lives. Services call repositories to load data, apply rules (fare calculation, wallet balance checks, refund windows), and coordinate across multiple repositories when a single operation touches more than one entity (for example, booking a trip must write a `Trip` row and decrement the `Wallet` balance in the same request). Services are plain Spring beans (`@Service`) — they have no knowledge of HTTP.

**Repository layer** is generated at runtime by Spring Data JPA. Each repository interface extends `JpaRepository` and gets basic CRUD for free. Custom query methods are added where needed (for example, `findByEmail` on `StudentRepository`). Hibernate translates method calls and JPQL to SQL against the H2 database.

Spring wires all of this together through its IoC container. A controller declares a `final` field of the service type and annotates the constructor with `@RequiredArgsConstructor` (Lombok). Spring sees the `@Service` bean and injects it at startup. The same pattern applies between service and repository. This means no object ever manually calls `new SomeDependency()` — dependencies flow inward via constructor injection, which makes every layer independently testable.

Why does the separation matter? Because each layer can change without breaking the others. The H2 database can be swapped for PostgreSQL by changing a few lines in `application.properties` and the controllers do not care. The fare formula can change in `FareCalculatorService` and the HTTP contract stays identical. Unit tests for a service mock the repository layer entirely — they do not need a database process running.

---

## 3. Data Model

### Entities and Their Fields

**Student** — the central actor. Fields: `id` (auto-generated), `name`, `email` (validated by the custom `@UniversityEmail` annotation, must end with `@university.edu`), `password` (stored BCrypt-hashed, excluded from serialisation via `@JsonIgnore`), `role` (the `Role` enum: `STUDENT` or `ADMIN`), and a `@OneToOne` relationship to `Wallet`.

**Wallet** — holds the point balance for a student. Fields: `id`, `balance` (int). A wallet has no direct reference back to the student; the student owns the foreign key.

**Stop** — a physical bus stop. Fields: `id`, `name`, `latitude` (Double), `longitude` (Double). Latitude and longitude are used in every distance calculation (Haversine).

**Route** — a named sequence of stops. Fields: `id`, `routeName`, and a `@ManyToMany` list of `Stop`. Because a stop can belong to multiple routes and a route contains multiple stops, JPA creates a join table (`route_stops`) automatically.

**Trip** — a single booking event. Fields: `id`, a `@ManyToOne` reference to `Student`, `@ManyToOne` references to `fromStop` and `toStop`, `fare` (int, computed at booking time), `tripTime` (LocalDateTime of booking), and `status` (the `TripStatus` enum: `BOOKED`, `COMPLETED`, or `CANCELLED`).

**Role** and **TripStatus** are Java enums stored as strings in the database (`@Enumerated(EnumType.STRING)`).

### Relationship Diagram

```
Student  1 ---- 1  Wallet
    |
    | (1 Student has many Trips)
    |
   Trip  *---- 1  Stop  (fromStop)
         *---- 1  Stop  (toStop)

Route  *---- *  Stop   (via join table route_stops)
```

### Why Is Wallet a Separate Entity?

A common first instinct is to put `balance` directly on `Student` as a plain integer field. Separating it into its own entity has several advantages:

1. **Separation of concerns.** Student is an identity/authentication concept. Wallet is a financial concept. Mixing them makes the `Student` table a catch-all that grows indefinitely as new financial fields are added.
2. **Future extensibility.** A separate `Wallet` entity can later gain transaction history, currency type, spending limits, or even be shared between multiple users (a family plan) without altering the `Student` schema.
3. **Locking granularity.** In a high-concurrency scenario, you can apply optimistic or pessimistic locking specifically to the `Wallet` row when a balance is being debited, without locking the `Student` row for unrelated reads.
4. **Clean API surface.** Admin wallet operations (`/api/admin/allocate-points`, `/api/admin/deduct-points`) act on the wallet concept, not the student concept — the separation makes that intent clear in both code and endpoints.

---

## 4. Request Lifecycle — Tracing a Booking Request

Here is exactly what happens when a client calls `POST /api/trips/book?studentId=1&fromStopId=1&toStopId=2` with a valid JWT.

**Step 1 — TCP/HTTP arrives at Tomcat.** Spring Boot embeds Tomcat. The request enters the servlet container.

**Step 2 — JwtFilter runs (OncePerRequestFilter).** The filter chain includes `JwtFilter` before any controller logic. It reads the `Authorization` header. If the header is present and starts with `Bearer `, it strips the prefix and passes the token string to `JwtUtil.extractEmail()` and `JwtUtil.extractRole()`. These methods parse the JWT (using the JJWT library), verify the HS256 signature against the configured secret, check that the token has not expired, and return the claims. The filter constructs a `UsernamePasswordAuthenticationToken` with the email as the principal and a `SimpleGrantedAuthority` of the form `ROLE_STUDENT` (or `ROLE_ADMIN`). It places this into `SecurityContextHolder`, making the identity available for the rest of the request's life.

**Step 3 — SecurityConfig evaluates the request.** Spring Security reads the authentication object from the context. The path `/api/trips/book` does not match `/api/auth/**`, `/h2-console/**`, `/api/admin/**`, or `/actuator/**`, so the rule `anyRequest().authenticated()` applies. The user is authenticated (the filter set the context), so access is granted.

**Step 4 — DispatcherServlet routes to TripController.** Spring MVC matches the path and HTTP method to `TripController.bookTrip()`. The query parameters `studentId`, `fromStopId`, and `toStopId` are bound to method arguments via `@RequestParam`.

**Step 5 — TripController calls TripService.bookTrip().** The controller delegates immediately. No logic happens in the controller itself.

**Step 6 — TripService loads entities.** `TripService` calls `StudentRepository.findById(studentId)` — if not found, it throws a `RuntimeException` with a descriptive message. It calls `StopRepository.findById(fromStopId)` and `StopRepository.findById(toStopId)` similarly.

**Step 7 — Fare is calculated.** `FareCalculatorService.calculateFare(fromStop, toStop)` is called. It runs the Haversine formula on the latitude/longitude coordinates of the two stops to get a distance in kilometres. It multiplies by `fare.rate-per-km` (2), takes `Math.ceil`, applies `Math.max(1, ...)`, and then checks whether the current server time falls in a peak window — if yes, multiplies by 1.5 and takes `Math.ceil` again.

**Step 8 — Wallet balance is checked.** `TripService` reads `student.getWallet().getBalance()`. If it is less than the computed fare, a `RuntimeException("Insufficient wallet balance")` is thrown.

**Step 9 — Trip is persisted.** A `Trip` entity is constructed with status `BOOKED`, `tripTime = LocalDateTime.now()`, and the computed fare. `TripRepository.save(trip)` writes it to the database. The wallet balance is decremented by the fare and the `WalletRepository.save(wallet)` (or the cascade from `StudentRepository.save(student)`) persists the update.

**Step 10 — Response serialised.** `TripService` returns a `BookingResult` DTO. `TripController` returns it as the response body. Jackson serialises it to JSON. Spring sets HTTP 200 and sends it back to the client.

**Step 11 — SecurityContext cleared.** After the response is committed, Spring Security clears the `SecurityContextHolder` so no identity leaks across requests.

---

## 5. Security Deep-Dive

### What Is JWT?

JSON Web Token (JWT) is a compact, URL-safe token format defined in RFC 7519. A JWT has three Base64URL-encoded sections separated by dots:

```
<header>.<payload>.<signature>
```

**Header** contains the algorithm and token type:
```json
{"alg": "HS256", "typ": "JWT"}
```

**Payload** contains claims. In this system the claims are:
- `sub` — the subject, which is the student's email
- `role` — the student's role (`STUDENT` or `ADMIN`)
- `iat` — issued-at timestamp
- `exp` — expiry timestamp (issued-at + 10 hours)

**Signature** is `HMACSHA256(base64url(header) + "." + base64url(payload), secretKey)`. The server holds the secret. Anyone who receives the token can decode the header and payload (they are just Base64, not encrypted), but they cannot forge a valid signature without the secret. If any bit of the header or payload changes, the signature will not match when the server verifies it.

### JwtFilter Walk-Through

```
Incoming request
     |
     v
Authorization header present and starts with "Bearer "?
     |-- No  --> set no authentication, continue filter chain
     |            (SecurityConfig will reject if endpoint requires auth)
     |
     v
Extract token string (strip "Bearer ")
     |
     v
JwtUtil.extractEmail(token)  -- parses, verifies signature, checks expiry
JwtUtil.extractRole(token)   -- reads "role" claim
     |
     v
SecurityContextHolder not already populated?
     |-- Yes --> build UsernamePasswordAuthenticationToken(email, null, [ROLE_X])
     |           set it in SecurityContextHolder
     |
     v
Continue filter chain --> SecurityConfig checks authority --> Controller
```

### Why Stateless Auth Scales Better

Session-based auth stores session data on the server (in memory or a session store like Redis). Every request must look up the session. This creates state that must be shared across all server instances in a horizontal scaling scenario. JWT is stateless: the server stores nothing per-user. Any instance can validate any token because every instance has the same secret. You can add or remove instances freely. The trade-off is revocation: once a JWT is issued, it is valid until it expires. You cannot "log out" a token without adding a server-side blacklist (which reintroduces state). For a 10-hour token, a compromised token stays valid for up to 10 hours.

### BCrypt vs MD5/SHA-256

MD5 and SHA-256 are general-purpose hashing algorithms designed to be fast — they can compute billions of hashes per second on modern hardware. That speed is an attacker's best friend for brute-forcing a stolen password database. BCrypt is an adaptive, slow hashing function built specifically for passwords. It incorporates a work factor (cost factor 10 in this project, the default), which means internally it runs 2^10 = 1024 rounds of its internal cipher. Verifying one BCrypt hash takes roughly 100ms on a modern CPU — acceptable for login latency, catastrophic for a brute-force attack. Critically, BCrypt generates a random 128-bit salt automatically and embeds it in the output hash string. Two calls to `BCrypt.hashpw("samepassword", ...)` produce completely different hash strings, which means a precomputed rainbow table is useless. SHA-256 without a salt allows rainbow table attacks; with a salt it becomes brute-force-only — but it is still orders of magnitude faster to brute-force than BCrypt.

---

## 6. Fare Calculation Explained

### The Formula

```
distance  = Haversine(fromStop.lat, fromStop.lon, toStop.lat, toStop.lon)  [km]
baseFare  = max(1, ceil(distance * ratePerKm))
peakFare  = ceil(baseFare * peakMultiplier)   [only during peak hours]
finalFare = peakFare if peak else baseFare
```

`ratePerKm` = 2, `peakMultiplier` = 1.5.

Peak hours are 07:00–09:00 and 17:00–19:00 (server local time). The check reads `LocalDateTime.now().getHour()` and tests whether it falls in either window.

### Concrete Example

Stop A at (12.9716, 77.5946), Stop B at (12.9352, 77.6245).

**Haversine calculation:**

The Haversine formula computes great-circle distance on a sphere:

```
deltaLat = toRadians(12.9352 - 12.9716) = toRadians(-0.0364) = -0.000635 rad
deltaLon = toRadians(77.6245 - 77.5946) = toRadians( 0.0299) =  0.000522 rad

a = sin(deltaLat/2)^2 + cos(lat1) * cos(lat2) * sin(deltaLon/2)^2
  ≈ (-0.000318)^2 + cos(0.2263) * cos(0.2257) * (0.000261)^2
  ≈ 0.0000001012 + 0.9746 * 0.9747 * 0.0000000681
  ≈ 0.0000001012 + 0.0000000647
  ≈ 0.0000001659

c = 2 * atan2(sqrt(a), sqrt(1-a))
  ≈ 2 * atan2(0.000407, 0.999999)
  ≈ 0.000814 rad

distance = R * c = 6371 * 0.000814 ≈ 5.18 km
```

(Typical result: approximately 5.2 km, depending on rounding in implementation.)

**Base fare:** `ceil(5.2 * 2)` = `ceil(10.4)` = **11 points**

**Peak fare:** `ceil(11 * 1.5)` = `ceil(16.5)` = **17 points**

### Why Math.ceil?

`ceil` ensures the fare is always at least as large as the fractional result — students are never undercharged. Combined with `max(1, ...)`, the minimum possible fare is 1 point even for stops that are centimetres apart. This prevents free rides due to floating-point results rounding down to zero.

### Why Server-Side Time?

The client cannot be trusted to supply the current time. A client that claims it is 10:00 when it is really 08:30 would bypass peak pricing. Using `LocalDateTime.now()` on the server guarantees that the same rules apply regardless of the client's clock, timezone, or intent.

---

## 7. Route Optimisation — Dijkstra Walk-Through

### The Graph Model

When a route-optimisation request arrives, `RouteOptimizationService` builds an adjacency graph dynamically from all routes in the database. The vertices are `Stop` entities. Edges exist between every consecutive pair of stops on a route (bidirectionally). Edge weight is the Haversine distance between the two stops in kilometres. If two stops appear as consecutive on multiple routes, there will be multiple edges between them — the algorithm naturally considers all.

### Why Not BFS?

BFS finds the path with the fewest hops (unweighted shortest path). Here the edges are weighted — a direct 1-hop path might cover 10 km, while a 3-hop path covers only 4 km. BFS would pick the 1-hop path even though it is longer. Dijkstra correctly handles non-negative weighted edges by always expanding the cheapest known node first.

### Small 4-Stop Example

Stops: A, B, C, D. Edges and weights:
```
A ---2km--- B
A ---6km--- C
B ---3km--- C
B ---5km--- D
C ---1km--- D
```
Goal: shortest path from A to D.

**Initialisation:**
```
dist = {A: 0, B: inf, C: inf, D: inf}
priority queue = [(0, A)]
```

**Iteration 1 — pop (0, A):**
- Relax A→B: dist[B] = min(inf, 0+2) = 2, push (2, B)
- Relax A→C: dist[C] = min(inf, 0+6) = 6, push (6, C)
```
dist = {A: 0, B: 2, C: 6, D: inf}
queue = [(2, B), (6, C)]
```

**Iteration 2 — pop (2, B):**
- Relax B→C: dist[C] = min(6, 2+3) = 5, push (5, C)
- Relax B→D: dist[D] = min(inf, 2+5) = 7, push (7, D)
```
dist = {A: 0, B: 2, C: 5, D: 7}
queue = [(5, C), (6, C stale), (7, D)]
```

**Iteration 3 — pop (5, C):**
- Relax C→D: dist[D] = min(7, 5+1) = 6, push (6, D)
```
dist = {A: 0, B: 2, C: 5, D: 6}
queue = [(6, C stale), (6, D), (7, D stale)]
```

**Iteration 4 — pop (6, D):**
D is the target. Stop.

**Shortest path A→D = 6 km, route = A→B→C→D.** The algorithm also tracks the `previous` node for each settled stop, so it can reconstruct the full path by walking backwards from D.

### Time Complexity

O((V + E) log V), where:
- V = number of distinct stops (vertices)
- E = number of consecutive stop pairs across all routes (edges)
- The log V factor comes from priority queue insertions and extractions (heap operations)

In the university shuttle context V is small (dozens of stops) and E is similarly small, so the algorithm runs in microseconds. The asymptotic complexity matters if the network ever grows to thousands of stops.

### Fare for the Optimal Route

Once the shortest-distance path is found, `FareCalculatorService` is called on the total distance to produce the quoted fare. The `RouteOptimizationResult` DTO carries both the ordered list of stops on the optimal path and the total fare.

---

## 8. Bus Transfer Explained

### The Problem

Dijkstra works when there is a connected path through the stop graph. If no route connects `fromStop` to `toStop` even indirectly (disconnected graph), or if you specifically want to find a two-leg journey that requires changing buses, the bus-transfer algorithm in `BusTransferService` applies.

### The Algorithm

1. Find all routes that contain `fromStop` — call this set **leg-1 routes**.
2. Find all routes that contain `toStop` — call this set **leg-2 routes**.
3. For every (leg-1 route, leg-2 route) pair, iterate over the stops of the leg-1 route. For each stop, check whether that stop also exists in the leg-2 route. If it does, it is a **candidate transfer stop**.
4. For each candidate transfer stop, compute the total journey distance: `Haversine(fromStop, transferStop) + Haversine(transferStop, toStop)`.
5. Track the candidate with the minimum total distance. Return it as the `TransferResult`.

### Concrete Example

Student wants to travel from Stop A to Stop D.
- Route 1 covers stops: A → B → C (in that order on the route)
- Route 2 covers stops: C → D → E (in that order on the route)

**Step 1:** Leg-1 routes containing A = {Route 1}. Stops on Route 1 = {A, B, C}.

**Step 2:** Leg-2 routes containing D = {Route 2}. Stops on Route 2 = {C, D, E}.

**Step 3:** Iterate over leg-1 pairs. Only pair is (Route 1, Route 2). Iterate stops of Route 1: A, B, C.
- Is A in Route 2's stops? No.
- Is B in Route 2's stops? No.
- Is C in Route 2's stops? Yes. Candidate transfer stop = C.

**Step 4:** Total distance = Haversine(A, C) + Haversine(C, D). Suppose this is 3.0 + 2.0 = 5.0 km.

**Step 5:** Since there is only one candidate, `TransferResult` = {leg1Route: Route 1, transferStop: C, leg2Route: Route 2, totalDistance: 5.0 km}.

### Why O(R²S)?

- R = number of routes. The outer loops iterate all (leg-1, leg-2) pairs = at most R * R = R² pairs.
- S = average number of stops per route. For each pair, iterating the leg-1 stops and checking membership in leg-2 stops is O(S) with a Set lookup, or O(S²) with naive list membership. In the implementation, converting route stops to a Set brings the inner check to O(S).
- Overall: O(R² × S). With a handful of routes and tens of stops, this is entirely negligible.

---

## 9. Caching Strategy

### Why Cache Routes and Stops?

Routes and stops are reference data — they are configured once by an administrator and then read thousands of times as students browse options or the server computes fares and optimisations. They are "read-heavy, write-rare." Querying the database for a list of all routes on every fare calculation would add unnecessary latency and database load. Caffeine is an in-process cache that stores the result in the JVM heap — a cache hit costs nanoseconds, not a database round trip.

### @Cacheable

```java
@Cacheable("routes")
public List<Route> getAllRoutes() { ... }
```

The first time `getAllRoutes()` is called, Spring executes the method body, stores the result in the `routes` cache, and returns it. On every subsequent call, Spring intercepts the call, finds the cached entry, and returns it without executing the method body at all. The `key` for a no-argument method is a constant, so all callers share a single cached list.

### @CacheEvict

```java
@CacheEvict(value = "routes", allEntries = true)
public Route createRoute(...) { ... }
```

When a route is created, updated, or deleted, the entire `routes` cache must be invalidated so the next read reflects the new database state. `allEntries = true` clears every entry in the named cache, not just the entry for a specific key. This is correct here because the cached value is the entire list — there is no per-key granularity.

### TTL Trade-Off

Even without explicit eviction, every cache entry expires after 5 minutes (`cache.ttl-seconds=300`). This is a safety net: if a bug caused eviction to be skipped, cached data would self-correct within 5 minutes. The trade-off is that a student could theoretically see stale data for up to 5 minutes after an admin changes a route — acceptable for reference data, unacceptable for, say, wallet balances (which are never cached).

### Why findNearestStops Is NOT Cached

```java
public List<NearestStopResult> findNearestStops(double lat, double lng, int limit) { ... }
```

This method takes dynamic parameters. Every unique `(lat, lng, limit)` combination would become a separate cache entry. Since lat/lng are floating-point values from GPS readings, there are effectively infinite distinct inputs. Caching them would grow the cache without bound and produce almost no hits (the probability of two requests having the exact same floating-point coordinates is near zero). Correct caching of this method would require coarser key bucketing (e.g., round to 2 decimal places), which adds complexity and was not needed for the project's scale.

---

## 10. Trip Cancellation Refund Logic

### The Logic

When `DELETE /api/trips/{tripId}/cancel?studentId={studentId}` is called:

1. `TripService` loads the `Trip` by `tripId`. If not found, throw.
2. Verify `trip.getStudent().getId().equals(studentId)`. If not, throw — a student cannot cancel another student's trip.
3. Check `trip.getStatus()`. If already `CANCELLED`, throw. If `COMPLETED`, throw (completed trips cannot be cancelled).
4. Compute `minutesSinceBooking = ChronoUnit.MINUTES.between(trip.getTripTime(), LocalDateTime.now())`.
5. If `minutesSinceBooking <= fullRefundMinutes` (10), restore the wallet balance: `wallet.setBalance(wallet.getBalance() + trip.getFare())` and save.
6. Set `trip.setStatus(TripStatus.CANCELLED)` and save.

### Key Details

**ChronoUnit.MINUTES.between** truncates fractional minutes (it counts whole minutes elapsed). This means a cancellation at 9 minutes 59 seconds is still within the 10-minute window. The boundary is inclusive (`<=`), so a cancellation at exactly 10 minutes also receives a full refund.

**Full refund** means the exact fare that was originally charged is added back to the wallet balance. There is no partial refund. If the cancellation is outside the 10-minute window, the trip is still cancelled (status set to `CANCELLED`) but the wallet is not touched.

**Why 10 minutes?** It is a configurable value (`trip.cancellation.full-refund-minutes=10` in `application.properties`) injected into `TripService` via `@Value`. The value was chosen as a reasonable grace period — short enough that the shuttle is unlikely to have been held, long enough for a student to realise they booked the wrong stop.

---

## 11. Testing Strategy

The test suite has 22 tests organised into two clear layers.

### Unit Tests (Mockito) — TripServiceTest, StudentServiceTest

**Why `@ExtendWith(MockitoExtension.class)` and not `@SpringBootTest`?**

`@SpringBootTest` starts a full Spring application context, loading all beans, opening a database connection, and starting an embedded server. For testing a single service class, this is enormously wasteful — it takes seconds instead of milliseconds and makes tests fragile (a misconfigured bean anywhere in the application causes all tests to fail). `@ExtendWith(MockitoExtension.class)` is a pure JUnit 5 extension that activates Mockito annotations. No Spring context is created.

**What `@InjectMocks` does:**
```java
@InjectMocks
private TripService tripService;
```
Mockito creates a real instance of `TripService` and injects any fields annotated with `@Mock` into it (matching by type). This simulates what Spring's IoC container does at runtime, but without requiring Spring at all.

**Why `ReflectionTestUtils` is needed for `@Value` fields:**
```java
@Value("${trip.cancellation.full-refund-minutes}")
private int fullRefundMinutes;
```
In production, Spring reads this from `application.properties` and sets the field. In a Mockito-only test there is no Spring to do that, so the field stays at its Java default value (0 for int). `ReflectionTestUtils.setField(tripService, "fullRefundMinutes", 10)` directly sets the private field via reflection, simulating what Spring would have done.

**What each TripService test proves:**
- `bookTrip_success` — verifies a `BOOKED` trip is saved and wallet is debited by the exact calculated fare.
- `bookTrip_peakHour` — verifies the peak multiplier inflates the fare. (May use Mockito to mock `LocalDateTime.now()` or test at a controlled time.)
- `bookTrip_insufficientBalance` — verifies that when `wallet.balance < fare`, a `RuntimeException` is thrown and no trip is saved.
- `bookTrip_studentNotFound` — verifies that when `studentRepository.findById` returns empty, the service throws rather than NPE.
- `bookTrip_stopNotFound` — same for stops.
- `cancelTrip_withinWindow` — verifies wallet is refunded when `minutesSinceBooking <= 10`.
- `cancelTrip_outsideWindow` — verifies wallet is NOT changed when cancellation is late.
- `cancelTrip_alreadyCancelled` — verifies exception thrown on double-cancel.
- `cancelTrip_completed` — verifies completed trips cannot be cancelled.
- `cancelTrip_wrongStudent` — verifies that mismatched `studentId` throws, not silently succeeds.

**What each StudentService test proves:**
- `createStudent_encodesPassword` — verifies the raw password from the request body is never stored; `BCryptPasswordEncoder.encode()` is called and the encoded string is what is persisted.
- `allocatePoints_success` — verifies `wallet.balance` increases by the exact amount.
- `allocatePoints_studentNotFound` — verifies exception on bad ID.
- `deductPoints_success` — verifies `wallet.balance` decreases by the exact amount.
- `deductPoints_studentNotFound` — verifies exception on bad ID.
- (6th test covers another edge case such as deducting more than the balance, depending on the implementation.)

### Integration Tests (@SpringBootTest) — BookingFlowIntegrationTest

**What the full context load proves:**
The single `ShuttleManagementApplicationTests` (context loads) test verifies that the Spring context starts without errors. This catches wiring mistakes: a missing bean, a circular dependency, a misconfigured security rule.

**Why in-memory H2 for tests?**
Tests need isolation — each test run must start from a known empty state without touching the production file-based database (`jdbc:h2:file:./data/shuttledb`). The test `application.properties` (in `src/test/resources`) overrides the datasource to `jdbc:h2:mem:testdb`, which is created fresh for each test run and destroyed after. This makes tests repeatable and order-independent.

**Why disable JPA-level bean validation in tests?**
The test `application.properties` sets `spring.jpa.properties.javax.persistence.validation.mode=none`. Without this, Hibernate runs Bean Validation (e.g., `@UniversityEmail`) on every entity save. The integration tests may insert test data programmatically with emails that don't match the validator's domain, which would cause unexpected validation failures. Disabling JPA-level validation still allows controller-level validation to be tested via MockMvc.

**What MockMvc is and why it's better than TestRestTemplate here:**
`MockMvc` exercises the full Spring MVC stack (filter chain, security, argument resolution, serialisation) without actually binding to a network port. Requests go through all the same code paths as a real HTTP request, but without TCP overhead. This makes tests faster and means the security filter chain (including `JwtFilter`) is exercised in each test. `TestRestTemplate` starts a real HTTP server and makes real socket connections — more realistic but slower and harder to introspect for status codes and response bodies.

**What the 5 integration tests prove:**
1. `bookTrip_success` — a student with sufficient balance can book a trip end-to-end; the response contains the trip ID and fare, and the database reflects the new trip.
2. `bookTrip_insufficientBalance` — the API returns an error (typically 400) and the wallet balance is unchanged.
3. `bookAndCancel_fullRefund` — book a trip, immediately cancel it, verify the wallet balance is restored to its original value.
4. `cancelTrip_wrongStudent` — cancelling another student's trip returns an error and does not mutate the trip.
5. `noJwt_returns4xx` — calling any protected endpoint without an `Authorization` header returns 401 or 403.

---

## 12. Interview Q&A

### Q1: "Walk me through what happens when a student books a trip."

The request hits the embedded Tomcat server. The `JwtFilter` intercepts it first, extracts the `Authorization: Bearer` token, verifies the HS256 signature against the server's secret key, and reads the `email` and `role` claims. It puts an authentication object into Spring's `SecurityContextHolder`. Spring Security then evaluates the security rules — `/api/trips/book` is not an admin or auth endpoint, so `authenticated()` applies, and the request is allowed through. Spring MVC dispatches to `TripController.bookTrip()`, which extracts `studentId`, `fromStopId`, and `toStopId` from query parameters and calls `TripService.bookTrip()`. The service loads the student and both stops from the database, runs the Haversine formula to get the distance, applies the fare formula (with peak multiplier if it's rush hour), checks that the wallet has enough points, deducts the fare, saves a new `Trip` with status `BOOKED`, and returns a `BookingResult`. The controller serialises this to JSON and the client gets the trip details including the computed fare.

### Q2: "Why did you use JWT instead of session-based auth?"

Session-based auth requires the server to store session data (in memory or an external store like Redis) and look it up on every request. This creates shared state that complicates horizontal scaling — every server instance needs to reach the same session store. JWT is stateless: the token carries all necessary information (identity and role) and is self-verifying via the cryptographic signature. Any server instance with the same secret key can validate any token independently. The trade-off is that once a JWT is issued, it cannot be revoked before its expiry without maintaining a server-side blacklist (which reintroduces state). For a 10-hour university shuttle session, this is an acceptable trade-off.

### Q3: "How does your fare calculation work? Why Haversine?"

The fare formula is `max(1, ceil(distanceKm * 2))` with a 1.5x peak multiplier during 07:00–09:00 and 17:00–19:00. The minimum fare of 1 prevents zero-fare edge cases. `ceil` ensures no underpayment from floating-point rounding. Haversine is the right formula because stops are specified by GPS latitude/longitude coordinates on the Earth's curved surface. Using simple Euclidean distance (the straight-line formula for flat space) would underestimate the actual distance, especially for stops far apart. Haversine computes great-circle distance — the shortest path along the sphere's surface — which closely approximates road distance for the scale of a university campus or city.

### Q4: "Explain Dijkstra in your project. What's the time complexity?"

We build a weighted graph where stops are vertices and edges connect consecutive stops on the same route, with edge weight = Haversine distance between them. Dijkstra uses a min-heap priority queue. It starts with the source stop at distance 0 and all others at infinity. Each iteration pops the stop with the smallest known distance, and for each of its neighbours, if going through the current stop would shorten the known distance to that neighbour, we update the distance and push the neighbour onto the queue. We stop when we pop the destination. Time complexity is O((V+E) log V) — V is the number of stops, E is the number of edges (consecutive stop pairs across all routes), and the log V factor comes from heap operations. We also maintain a `previous` map to reconstruct the full path once the destination is reached.

### Q5: "What's the difference between your unit tests and integration tests?"

Unit tests (Mockito, `@ExtendWith(MockitoExtension.class)`) test a single class in isolation. All dependencies are replaced with Mockito mocks. No Spring context is started, no database is used. They run in milliseconds. We use `ReflectionTestUtils` to inject `@Value` fields that Spring would normally set. These tests prove that the business logic is correct — that `TripService` throws the right exceptions, debits the right amount, and saves correctly structured entities. Integration tests (`@SpringBootTest`) start the full Spring context with an in-memory H2 database and exercise the full stack from HTTP (via `MockMvc`) through filters, security, controllers, services, and all the way to the database. They prove that all the layers work together correctly and that security rules are enforced end-to-end.

### Q6: "How does caching work in your project? What happens when a route is updated?"

We use Caffeine as the in-process cache, configured via `spring.cache.type=caffeine`. `getAllRoutes()` is annotated with `@Cacheable("routes")` — the first call executes the database query and stores the result in a JVM heap map; subsequent calls return the cached list without touching the database. The cache has a 5-minute TTL as a background safety net. When a route is created, updated, or deleted, the corresponding service method is annotated with `@CacheEvict(value="routes", allEntries=true)`, which removes the cached list immediately. The next call to `getAllRoutes()` will go to the database and cache the fresh result. The same pattern applies to the `stops` cache.

### Q7: "What would you change if this needed to scale to 10,000 concurrent users?"

Several things: First, replace the H2 file-based database with PostgreSQL or MySQL — H2 does not handle high concurrent write throughput. Second, replace Caffeine (in-process) with Redis — with multiple server instances, in-process caches are inconsistent (each instance has its own copy, and eviction on one instance does not propagate to others). Third, add optimistic locking (`@Version`) to the `Wallet` entity to prevent lost updates when two requests try to debit the same wallet simultaneously. Fourth, move to a connection pool (HikariCP is already the Spring Boot default) with tuned pool size. Fifth, consider externalising the JWT secret to a secrets manager (AWS Secrets Manager, HashiCorp Vault) and rotating it. Sixth, add rate limiting at the API gateway layer to protect the booking endpoint from abuse.

### Q8: "Why is BCrypt better than SHA-256 for passwords?"

SHA-256 is a general-purpose cryptographic hash designed to be fast — modern hardware can compute billions of SHA-256 hashes per second, making brute-force attacks against a stolen hash database practical. BCrypt is purpose-built for password hashing; its cost factor (10 in this project) means the algorithm internally iterates 2^10 = 1024 rounds, making a single hash verification take ~100ms. An attacker who steals the hash database can only try ~10 hashes per second per CPU core instead of billions. BCrypt also generates a unique random salt per password and embeds it in the hash output, making rainbow table attacks impossible. SHA-256 without a salt is vulnerable to rainbow tables; SHA-256 with a salt is resistant to rainbow tables but still fast enough for brute force. BCrypt eliminates both attack vectors.

### Q9: "What is the trade-off of stateless JWT auth?"

The advantage is scalability — no server-side session store, any instance can verify any token. The disadvantage is revocation. Once a JWT is signed and given to a client, the server cannot invalidate it before it expires. If a student's account is compromised or an admin revokes access, the existing token remains valid for up to 10 hours (the configured expiry). Solutions include: maintaining a server-side token blacklist (reintroduces state, but can be a small Redis set of revoked JTIs — JWT IDs), using very short expiry tokens (e.g., 15 minutes) combined with refresh tokens, or rekeying the signing secret (which invalidates ALL tokens, not just one). For a university shuttle application, the 10-hour window was deemed acceptable. A production system with stricter security requirements would add a lightweight blacklist.

### Q10: "How does the bus transfer algorithm work?"

We find all routes that include the origin stop (leg-1 candidates) and all routes that include the destination stop (leg-2 candidates). Then we iterate every combination of (leg-1 route, leg-2 route). For each pair, we look for a stop that appears in both routes — a potential transfer point. When we find one, we compute the total journey distance as `Haversine(origin, transferStop) + Haversine(transferStop, destination)`. We track the transfer stop that minimises this total distance across all candidates and return it as the recommendation. The complexity is O(R²S) where R is the number of routes and S is the average stops per route — the R² comes from iterating all route pairs, and S comes from iterating the stops of the leg-1 route for each pair.
