# The City That Couldn't Find Its Way: Journey Recommendation System

Finds the journey that uses the **fewest boarded buses** and re-plans when routes are closed.

## Run

```bash
javac -d out src/*.java
java -cp out JourneyTests                       # 30 tests
java -cp out JourneyCLI demo_network.txt        # interactive tool
```

The web demo is in `web/index.html`. It is one file with no build step, so you can open it directly or host it on GitHub Pages.

### CLI commands
```
route R1 1 2 7        add or replace a route ("route R2 [3,6,7]" also works)
load FILE             one route per line:  R1: 1, 2, 7
go 1 6                fewest-bus journey
go 1 6 -x R2          close R2 for this query only
close R2 / open all   closures that stay across queries
show                  list routes
```

## Algorithm: BFS over routes, not stops

1. **Index once:** `stop -> [routes serving it]`. Adding a route updates the index in place, so later queries never rebuild the network.
2. **Ring 1** holds every *active* route that serves the source.
3. Pop a route. If it serves the destination, stop. Otherwise, for each of its stops not yet expanded, enqueue every unvisited active route through that stop. This is ring k+1.
4. Parent pointers (`previous route`, `boarding stop`) rebuild the ordered routes and the stops where you change.

**Overlap efficiency:** each route is enqueued at most once and each stop's route list is scanned at most once. A query therefore costs **O(total stop appearances)**, which is at most 40,000. Measured: about 1.6 ms per query on 400 heavily overlapping routes.

**Closures** are a per-query boolean mask, so the network is never rebuilt.

**Edge cases:** source equal to destination gives 0 buses. Unknown stops, disconnected networks, and a source or destination served only by closed routes each get a specific "no journey" message.

## Why this is the minimum number of *buses*, not stops

Each BFS level is one more boarding. BFS visits every journey of k buses before any journey of k+1 buses, so the first level that reaches the destination is optimal. If a shorter journey existed, its last route would have been found at an earlier level.

Stops are not the cost. On the *Buses vs stops trap* network, the shortest path by stops is 3 stops but needs **3 buses**. This planner returns **1 bus** riding 8 stops. A BFS over stops would minimise the wrong thing.

## Tests (`JourneyTests.java`)
- The problem's example (R1→R2 changing at 7), and the same example with R2 closed (no journey)
- Source equal to destination, unknown stops, a single-bus trip
- **Disconnected** network, plus queries inside each component
- **Heavily overlapping:** 400 routes × 100 stops all sharing a hub (40,000 appearances), with timing, and with half the routes closed
- A 400-route chain (deepest possible search)
- Closures that force a 3-bus detour, closing every route, replacing a route, a loop route
- **500 random networks** cross-checked against an independent brute-force oracle. Every bus count matches, and every journey is validated leg by leg.

## 2-minute demo script
1. Web demo → **Demo city**: Airport → Whitefield gives 2 buses (Red → Orange, change at Hebbal).
2. Close Orange: still 2 buses (Red → Green). Close Green: Express → Purple. Close Express: 3 buses.
3. Close Purple: "every route serving Whitefield is closed."
4. **Buses vs stops trap**: point at the comparison table, then at the "Why this is the minimum" rings.
5. **Stress: 400 routes**: an answer in a few ms.
