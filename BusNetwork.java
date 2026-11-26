import java.util.*;

/**
 * Journey Recommendation System — minimum-bus route planner.
 *
 * Model: BFS over ROUTES, not stops. One BFS level = one more bus boarded.
 *   - Level 1  = every active route that serves the source stop.
 *   - Level k+1 = every unvisited active route sharing a stop with a level-k route.
 * The first level containing a route that serves the destination is the minimum
 * number of buses (BFS explores in non-decreasing bus count).
 *
 * Efficiency on overlapping routes: each route is expanded at most once and
 * each stop's route list is scanned at most once per query, so a query costs
 * O(total stop appearances) = O(40,000) at the stated limits.
 *
 * The stop -> routes index is built once (incrementally as routes are added)
 * and reused for every query; closures are a per-query filter, never a rebuild.
 */
public class BusNetwork {

    private final List<String> routeNames = new ArrayList<>();
    private final List<List<String>> routeStops = new ArrayList<>();
    private final Map<String, Integer> routeIndex = new HashMap<>();
    private final Map<String, List<Integer>> stopToRoutes = new HashMap<>();

    /** Add a route (or replace one with the same name). Duplicate stops in a route are ignored. */
    public void addRoute(String name, List<String> stops) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Route name required");
        if (stops == null || stops.isEmpty()) throw new IllegalArgumentException("Route " + name + " has no stops");
        List<String> clean = new ArrayList<>(new LinkedHashSet<>(stops));
        Integer existing = routeIndex.get(name);
        if (existing != null) {                      // replace: unlink old stops only
            for (String s : routeStops.get(existing)) stopToRoutes.get(s).remove(existing);
            routeStops.set(existing, clean);
            for (String s : clean) stopToRoutes.computeIfAbsent(s, k -> new ArrayList<>()).add(existing);
            return;
        }
        int id = routeNames.size();
        routeNames.add(name);
        routeStops.add(clean);
        routeIndex.put(name, id);
        for (String s : clean) stopToRoutes.computeIfAbsent(s, k -> new ArrayList<>()).add(id);
    }

    public boolean hasRoute(String name) { return routeIndex.containsKey(name); }
    public boolean hasStop(String stop) { return stopToRoutes.containsKey(stop) && !stopToRoutes.get(stop).isEmpty(); }
    public int routeCount() { return routeNames.size(); }
    public Set<String> routeNames() { return new LinkedHashSet<>(routeNames); }
    public List<String> stopsOf(String route) { return Collections.unmodifiableList(routeStops.get(routeIndex.get(route))); }

    /** One boarded bus within a journey. */
    public record Leg(String route, String boardAt, String alightAt) {}

    /** Query result. found=false means no journey exists. */
    public record Journey(boolean found, List<Leg> legs, String message) {
        public int buses() { return found ? legs.size() : -1; }
        public List<String> routesUsed() { return legs.stream().map(Leg::route).toList(); }
        public List<String> changeStops() {
            List<String> c = new ArrayList<>();
            for (int i = 1; i < legs.size(); i++) c.add(legs.get(i).boardAt());
            return c;
        }
        public String describe() {
            if (!found) return "NO JOURNEY: " + message;
            if (legs.isEmpty()) return "0 buses - you are already at your destination.";
            StringBuilder sb = new StringBuilder();
            sb.append(legs.size()).append(legs.size() == 1 ? " bus" : " buses")
              .append(": ").append(String.join(" -> ", routesUsed())).append('\n');
            for (int i = 0; i < legs.size(); i++) {
                Leg l = legs.get(i);
                sb.append("  ").append(i + 1).append(". Board ").append(l.route())
                  .append(" at stop ").append(l.boardAt())
                  .append(", ride to stop ").append(l.alightAt());
                sb.append(i + 1 < legs.size() ? "  (change here)" : "  (destination)").append('\n');
            }
            List<String> c = changeStops();
            sb.append("  Changes at: ").append(c.isEmpty() ? "none" : String.join(", ", c));
            return sb.toString();
        }
    }

    public Journey query(String source, String destination) { return query(source, destination, Set.of()); }

    public Journey query(String source, String destination, Collection<String> inactiveRoutes) {
        if (source.equals(destination)) return new Journey(true, List.of(), "same stop");
        if (!hasStop(source)) return new Journey(false, List.of(), "source stop " + source + " is not served by any route");
        if (!hasStop(destination)) return new Journey(false, List.of(), "destination stop " + destination + " is not served by any route");

        int n = routeNames.size();
        boolean[] inactive = new boolean[n];
        for (String r : inactiveRoutes) { Integer id = routeIndex.get(r); if (id != null) inactive[id] = true; }

        int[] parentRoute = new int[n];       // route we came from (-1 = boarded at source)
        String[] boardStop = new String[n];   // stop where we boarded this route
        boolean[] visited = new boolean[n];
        Set<String> expandedStops = new HashSet<>();
        ArrayDeque<Integer> queue = new ArrayDeque<>();

        boolean[] servesDest = new boolean[n];
        for (int r : stopToRoutes.get(destination)) servesDest[r] = true;

        expandedStops.add(source);
        for (int r : stopToRoutes.get(source)) {
            if (inactive[r]) continue;
            visited[r] = true; parentRoute[r] = -1; boardStop[r] = source; queue.add(r);
        }

        while (!queue.isEmpty()) {
            int r = queue.poll();
            // Does this route reach the destination? BFS order => fewest buses.
            if (servesDest[r]) return reconstruct(r, destination, parentRoute, boardStop);
            for (String s : routeStops.get(r)) {
                if (!expandedStops.add(s)) continue;           // stop already explored by an earlier bus
                for (int next : stopToRoutes.get(s)) {
                    if (visited[next] || inactive[next]) continue;
                    visited[next] = true; parentRoute[next] = r; boardStop[next] = s; queue.add(next);
                }
            }
        }
        boolean anyDestOpen = false, anySrcOpen = false;
        for (int r : stopToRoutes.get(destination)) if (!inactive[r]) anyDestOpen = true;
        for (int r : stopToRoutes.get(source)) if (!inactive[r]) anySrcOpen = true;
        if (!anySrcOpen) return new Journey(false, List.of(), "every route serving source stop " + source + " is inactive");
        if (!anyDestOpen) return new Journey(false, List.of(), "every route serving destination stop " + destination + " is inactive");
        String why = inactiveRoutes.isEmpty() ? "the stops are in disconnected parts of the network"
                : "no path avoids the inactive route(s) " + inactiveRoutes;
        return new Journey(false, List.of(), why);
    }

    private Journey reconstruct(int last, String destination, int[] parentRoute, String[] boardStop) {
        LinkedList<Leg> legs = new LinkedList<>();
        String alight = destination;
        for (int r = last; r != -1; r = parentRoute[r]) {
            legs.addFirst(new Leg(routeNames.get(r), boardStop[r], alight));
            alight = boardStop[r];
        }
        return new Journey(true, new ArrayList<>(legs), "ok");
    }
}
