# Design Decisions & Trade-offs

This document explains the key architectural and algorithmic choices made in the
Shuttle Management System, along with their trade-offs and complexity analysis.

---

## 1. Distance Calculation — Haversine Formula

**Where:** `FareCalculatorService`, `StopService`, `BusTransferService`, `RouteOptimizationService`

**Decision:** Use the Haversine great-circle formula to compute straight-line distance
between two GPS coordinates instead of a fixed per-stop flat rate.

**Why:**
- Shuttle stops have real latitude/longitude coordinates — Euclidean (flat-earth) distance
  is inaccurate over the curved Earth surface, especially for stops several kilometres apart.
- Haversine is O(1) per pair and has no external dependency.

**Trade-offs:**
| | Haversine | Flat Euclidean | External Maps API |
|---|---|---|---|
| Accuracy | Good (~0.3% error) | Poor at scale | Exact road distance |
| Cost | Free | Free | Paid / rate-limited |
| Latency | O(1), in-process | O(1), in-process | Network call |

**Complexity:** O(1) per stop pair.

---

## 2. Route Optimisation — Dijkstra's Algorithm

**Where:** `RouteOptimizationService.findOptimalPath()`

**Decision:** Model stops as graph nodes and consecutive stops within a route as
bidirectional weighted edges (weight = Haversine distance). Run Dijkstra to find the
shortest path between any two stops.

**Graph construction:**
- Each `Route` contributes `(n - 1)` edges for `n` stops.
- Edges are bidirectional so shuttles can be boarded from either direction.

**Complexity:**
- Build graph: O(R × S) — R routes, S stops per route
- Dijkstra with binary heap: **O((V + E) log V)** — V = total stops, E = total edges
- Path reconstruction: O(V)

**Trade-offs:**
| | Dijkstra | BFS | Bellman-Ford |
|---|---|---|---|
| Negative edges | No | No | Yes |
| Time | O((V+E)logV) | O(V+E) | O(VE) |
| Appropriate here | ✅ distances > 0 | ❌ unweighted | ❌ overkill |

**Limitation:** The graph is rebuilt on every request. For a larger deployment, the
adjacency list could be cached and invalidated on route mutations (which is already done
via `@CacheEvict` on route writes).

---

## 3. Bus Transfer — Shared-Stop Strategy

**Where:** `BusTransferService.findBestTransfer()`

**Decision:** Find the best two-leg journey by:
1. Finding all routes containing the source stop (leg-1 candidates).
2. Finding all routes containing the destination stop (leg-2 candidates).
3. For every (leg-1, leg-2) route pair, finding shared stops as transfer candidates.
4. Picking the transfer stop that minimises total Haversine distance.

**Complexity:** O(R² × S) — R routes, S stops per route.

**Trade-offs:**
- Simple and correct for a small network (dozens of routes, tens of stops per route).
- Does not find multi-hop transfers (3+ legs). For a city-scale network,
  Dijkstra with a layered graph (stop × route) would generalise this cleanly.

---

## 4. Dynamic Pricing — Peak/Off-Peak Multiplier

**Where:** `FareCalculatorService`

**Decision:** Apply a configurable multiplier (`fare.peak.multiplier`, default 1.5×) during
configurable morning and evening peak windows. Base fare = ⌈distanceKm × ratePerKm⌉ with a
minimum of 1 point.

**Why `Math.ceil`:** Ensures a non-zero fare even for very short distances. Avoids
fractional point charges which the integer wallet balance cannot represent.

**Configuration:**
```
fare.rate-per-km=2
fare.peak.morning-start=7  fare.peak.morning-end=9
fare.peak.evening-start=17 fare.peak.evening-end=19
fare.peak.multiplier=1.5
```

**Trade-off:** Time is read from the server clock (`LocalTime.now()`), not from
the client. This is intentional — client-supplied timestamps could be manipulated
to avoid peak surcharges.

---

## 5. Authentication — Stateless JWT over Sessions

**Where:** `JwtUtil`, `JwtFilter`, `SecurityConfig`

**Decision:** Issue signed HS256 JWTs on login. Each request is authenticated by
validating the token in a `OncePerRequestFilter` — no server-side session store.

**Why:**
- Stateless — scales horizontally without shared session storage.
- The `role` claim is embedded in the token, enabling zero-database role checks
  on every request.

**Trade-offs:**
| | JWT (stateless) | Server sessions |
|---|---|---|
| Logout / revocation | Hard (token lives until expiry) | Instant |
| Horizontal scaling | Easy | Requires shared store (Redis) |
| DB hit per request | None | Optional |

**Mitigation for revocation:** The expiry is set to 10 hours (`jwt.expiration-ms=36000000`).
For production, a short-lived access token + refresh token pattern is recommended.

---

## 6. Password Storage — BCrypt

**Where:** `StudentService.createStudent()`, `AuthController`

**Decision:** Hash passwords with `BCryptPasswordEncoder` (Spring Security default, cost
factor 10) before persisting. Plain-text passwords are never stored.

**Why BCrypt over SHA-256/MD5:**
- Adaptive cost factor makes brute-force exponentially more expensive as hardware improves.
- Built-in salt prevents rainbow-table attacks.

**Trade-off:** BCrypt is intentionally slow (~100 ms per hash at cost=10). For a
high-throughput registration endpoint this could be a bottleneck; Argon2id would be
a more modern choice but requires an additional dependency.

---

## 7. Caching — Caffeine In-Process Cache

**Where:** `RouteService.getAllRoutes()`, `StopService.getAllStops()`

**Decision:** Cache the full list of routes and stops in a Caffeine in-process cache
with a 5-minute TTL and 500-entry maximum. All write operations (`save`, `update`,
`delete`, `addStop`, `removeStop`) evict the entire cache region.

**Why Caffeine:**
- Routes and stops are read far more often than they are written (typical shuttle
  network changes rarely during operation).
- In-process cache eliminates serialization overhead and network round-trips.

**Trade-offs:**
| | Caffeine (in-process) | Redis (distributed) |
|---|---|---|
| Latency | Nanoseconds | ~1 ms |
| Multi-instance consistency | Stale until TTL | Immediate |
| Operational complexity | None | Requires Redis cluster |

**Limitation:** In a multi-instance deployment, each JVM has its own cache. A write on
instance A will not immediately evict the cache on instance B. Redis pub/sub or a
distributed cache would be needed for strict consistency.

---

## 8. Trip Cancellation — Time-Window Full Refund

**Where:** `TripService.cancelTrip()`

**Decision:** Grant a full wallet refund if cancellation occurs within
`trip.cancellation.full-refund-minutes` (default 10) of booking. No partial refund
outside this window.

**Why:** Binary refund logic is simple to reason about and audit. A partial-refund
schedule (e.g., 50% after 10 minutes) could be added by making the window and
percentage configurable without changing the algorithm.

---

## 9. Database — H2 File-Based Persistence

**Decision:** Use H2 in file mode (`jdbc:h2:file:./data/shuttledb`) with
`ddl-auto=update` in development and `create-drop` in tests.

**Why for development/assignment:**
- Zero setup — no external database process required.
- `file:` mode persists data across application restarts (unlike `mem:`).

**Production trade-off:** H2 is not suitable for concurrent multi-instance deployments.
Replacing the datasource URL and dialect with PostgreSQL or MySQL requires no code
changes — only `application.properties` updates.

---

## 10. Expense Reporting — In-Memory Stream Filtering

**Where:** `TripService.getExpenseReport()`

**Decision:** Load all non-cancelled trips for a student, then filter in memory by
the requested date window (weekly / monthly).

**Trade-off:** For a student with thousands of trips this is inefficient. A better
approach would add date-range parameters to the `TripRepository` query
(`BETWEEN :start AND :end`) to push filtering to the database. The current design
is acceptable at the scale of a university shuttle system (tens of trips per student).

---

## Summary Table

| Concern | Choice | Key Trade-off |
|---|---|---|
| Distance | Haversine | ~0.3% error vs. zero cost |
| Shortest path | Dijkstra O((V+E)logV) | Graph rebuilt per request |
| Transfer | Shared-stop O(R²S) | Single-transfer only |
| Pricing | Configurable peak multiplier | Server-side time (tamper-proof) |
| Auth | Stateless JWT HS256 | Hard revocation |
| Passwords | BCrypt cost=10 | Slow for bulk registration |
| Caching | Caffeine in-process TTL=5m | Stale in multi-instance |
| Cancellation | Binary refund window | No partial refund |
| Database | H2 file (dev) | Not production-grade |
| Reporting | In-memory filter | Inefficient at scale |
