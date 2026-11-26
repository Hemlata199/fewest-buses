import java.util.*;

/** Dependency-free test runner: java -cp out JourneyTests */
public class JourneyTests {
    static int passed = 0, failed = 0;

    static void check(String name, boolean cond, String detail) {
        if (cond) { passed++; System.out.println("PASS  " + name); }
        else { failed++; System.out.println("FAIL  " + name + "  -> " + detail); }
    }

    static BusNetwork net(String... defs) {
        BusNetwork n = new BusNetwork();
        for (String d : defs) {
            String[] p = d.split("[=,\\s]+");
            n.addRoute(p[0], Arrays.asList(p).subList(1, p.length));
        }
        return n;
    }

    /** A journey is valid if each leg's stops are on that route, legs chain, ends match, no inactive route. */
    static boolean valid(BusNetwork n, BusNetwork.Journey j, String src, String dst, Set<String> inactive) {
        if (!j.found()) return false;
        String at = src;
        for (BusNetwork.Leg l : j.legs()) {
            if (inactive.contains(l.route())) return false;
            if (!l.boardAt().equals(at)) return false;
            List<String> st = n.stopsOf(l.route());
            if (!st.contains(l.boardAt()) || !st.contains(l.alightAt())) return false;
            at = l.alightAt();
        }
        return at.equals(dst);
    }

    /** Independent oracle: Bellman-style relaxation of "buses needed to reach each stop". */
    static int bruteForce(BusNetwork n, String src, String dst, Set<String> inactive) {
        if (src.equals(dst)) return 0;
        Map<String, Integer> best = new HashMap<>();
        best.put(src, 0);
        boolean changed = true;
        while (changed) {
            changed = false;
            for (String r : n.routeNames()) {
                if (inactive.contains(r)) continue;
                int min = Integer.MAX_VALUE;
                for (String s : n.stopsOf(r)) min = Math.min(min, best.getOrDefault(s, Integer.MAX_VALUE));
                if (min == Integer.MAX_VALUE) continue;
                for (String s : n.stopsOf(r)) {
                    if (best.getOrDefault(s, Integer.MAX_VALUE) > min + 1) { best.put(s, min + 1); changed = true; }
                }
            }
        }
        return best.getOrDefault(dst, -1);
    }

    public static void main(String[] args) {
        // 1. Illustrative example from the problem statement
        BusNetwork ex = net("R1=1,2,7", "R2=3,6,7");
        BusNetwork.Journey j = ex.query("1", "6");
        check("example: 2 buses", j.buses() == 2, j.describe());
        check("example: R1 then R2", j.routesUsed().equals(List.of("R1", "R2")), j.routesUsed().toString());
        check("example: change at 7", j.changeStops().equals(List.of("7")), j.changeStops().toString());
        check("example: journey valid", valid(ex, j, "1", "6", Set.of()), j.describe());

        // 2. Closure
        j = ex.query("1", "6", Set.of("R2"));
        check("example with R2 inactive: no route", !j.found(), j.describe());
        // 3. Repeated query on same network after closure is lifted (no rebuild)
        j = ex.query("1", "6");
        check("re-query after closure: 2 buses again", j.buses() == 2, j.describe());

        // 4. Source == destination
        check("source == destination: 0 buses", ex.query("7", "7").buses() == 0, "");
        check("source == destination even for unknown stop", ex.query("99", "99").buses() == 0, "");

        // 5. Single bus
        check("same route: 1 bus", ex.query("1", "7").buses() == 1, "");

        // 6. Unknown stops
        check("unknown destination", !ex.query("1", "42").found(), "");
        check("unknown source", !ex.query("42", "1").found(), "");

        // 7. Disconnected network
        BusNetwork dis = net("A=1,2,3", "B=3,4", "C=10,11,12", "D=12,13");
        check("disconnected: no journey", !dis.query("1", "13").found(), "");
        check("disconnected: within component works", dis.query("1", "4").buses() == 2, "");
        check("disconnected: other component works", dis.query("10", "13").buses() == 2, "");

        // 8. Min buses is NOT min stops
        //    LONG rides 9 stops in one bus; SHORT path is 2 hops of stops but needs 2 buses.
        BusNetwork ms = net("LONG=S,a,b,c,d,e,f,g,h,T", "HOP1=S,X", "HOP2=X,T");
        j = ms.query("S", "T");
        check("min buses beats min stops: 1 bus via LONG", j.buses() == 1 && j.routesUsed().equals(List.of("LONG")), j.describe());
        j = ms.query("S", "T", Set.of("LONG"));
        check("LONG closed: falls back to 2 buses", j.buses() == 2, j.describe());

        // 9. Closure forces a longer detour
        BusNetwork det = net("Direct=A,Z", "P=A,B", "Q=B,C", "R=C,Z");
        check("direct: 1 bus", det.query("A", "Z").buses() == 1, "");
        j = det.query("A", "Z", Set.of("Direct"));
        check("detour: 3 buses P->Q->R", j.buses() == 3 && j.routesUsed().equals(List.of("P", "Q", "R")), j.describe());
        check("detour: changes at B, C", j.changeStops().equals(List.of("B", "C")), j.changeStops().toString());

        // 10. All routes inactive
        check("all closed: none", !det.query("A", "Z", Set.of("Direct", "P", "Q", "R")).found(), "");

        // 11. Route replacement (update without rebuilding)
        BusNetwork up = net("R1=1,2", "R2=2,3");
        check("before update: 2 buses", up.query("1", "3").buses() == 2, "");
        up.addRoute("R1", List.of("1", "2", "3"));
        check("after R1 extended: 1 bus", up.query("1", "3").buses() == 1, "");

        // 12. Cyclic / loop route and duplicate stops
        BusNetwork loop = net("Ring=1,2,3,4,1", "Spur=3,9");
        check("loop route handled", loop.query("1", "9").buses() == 2, "");

        // 13. Long transfer chain (worst case depth)
        BusNetwork chain = new BusNetwork();
        for (int i = 0; i < 400; i++) chain.addRoute("C" + i, List.of("s" + i, "s" + (i + 1)));
        j = chain.query("s0", "s400");
        check("chain of 400 routes: 400 buses", j.buses() == 400 && valid(chain, j, "s0", "s400", Set.of()), "" + j.buses());

        // 14. Heavily overlapping network at the stated limits: 400 routes x 100 stops = 40,000 appearances,
        //     all routes share a hub stop and most stops are shared by many routes.
        BusNetwork heavy = new BusNetwork();
        Random rnd = new Random(7);
        for (int r = 0; r < 400; r++) {
            List<String> st = new ArrayList<>();
            st.add("HUB");
            while (st.size() < 100) st.add("p" + rnd.nextInt(300));   // only 300 distinct stops -> huge overlap
            heavy.addRoute("H" + r, st);
        }
        heavy.addRoute("Far", List.of("p0", "FAR"));
        heavy.query("HUB", "FAR"); // warm-up
        long t0 = System.nanoTime();
        int q = 200;
        for (int i = 0; i < q; i++) j = heavy.query("HUB", "FAR");
        double ms1 = (System.nanoTime() - t0) / 1e6 / q;
        check("heavy overlap: correct (2 buses)", j.buses() == 2 && valid(heavy, j, "HUB", "FAR", Set.of()), j.describe());
        check(String.format("heavy overlap: fast (%.3f ms/query)", ms1), ms1 < 50, ms1 + " ms");
        Set<String> closeHalf = new HashSet<>();
        for (int r = 0; r < 200; r++) closeHalf.add("H" + r);
        j = heavy.query("HUB", "FAR", closeHalf);
        check("heavy overlap with 200 routes closed: valid", valid(heavy, j, "HUB", "FAR", closeHalf), j.describe());

        // 15. Randomised cross-check against independent oracle (minimality proof by testing)
        Random rr = new Random(42);
        int mismatches = 0, invalid = 0, trials = 500;
        for (int t = 0; t < trials; t++) {
            BusNetwork n = new BusNetwork();
            int routes = 2 + rr.nextInt(15), stops = 5 + rr.nextInt(30);
            for (int r = 0; r < routes; r++) {
                List<String> st = new ArrayList<>();
                int len = 2 + rr.nextInt(5);
                for (int k = 0; k < len; k++) st.add("" + rr.nextInt(stops));
                n.addRoute("R" + r, st);
            }
            Set<String> inactive = new HashSet<>();
            for (int r = 0; r < routes; r++) if (rr.nextInt(5) == 0) inactive.add("R" + r);
            String s = "" + rr.nextInt(stops), d = "" + rr.nextInt(stops);
            BusNetwork.Journey got = n.query(s, d, inactive);
            int expect = bruteForce(n, s, d, inactive);
            if (got.buses() != expect) mismatches++;
            if (got.found() && !s.equals(d) && !valid(n, got, s, d, inactive)) invalid++;
        }
        check("random cross-check: bus count matches oracle in " + trials + " trials", mismatches == 0, mismatches + " mismatches");
        check("random cross-check: every journey is valid", invalid == 0, invalid + " invalid");

        // 16. CLI smoke test
        var bos = new java.io.ByteArrayOutputStream();
        JourneyCLI cli = new JourneyCLI(new java.io.PrintStream(bos));
        cli.exec("route R1 1 2 7"); cli.exec("route R2 [3,6,7]"); cli.exec("go 1 6"); cli.exec("close R2"); cli.exec("go 1 6");
        String outText = bos.toString();
        check("CLI: shows 2 buses then no journey", outText.contains("2 buses: R1 -> R2") && outText.contains("NO JOURNEY"), outText);

        System.out.println("\n" + passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }
}
