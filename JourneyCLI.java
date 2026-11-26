import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Interactive route query tool.
 *
 * Commands:
 *   route R1 1 2 7          add (or replace) a route
 *   load network.txt        load routes from a file (one "R1: 1 2 7" per line)
 *   close R2 [R3...]        mark routes inactive (persists across queries)
 *   open R2 | open all      re-activate routes
 *   go 1 6 [-x R2 R3]       query; -x adds extra closures for this query only
 *   show                    list routes and closures
 *   help | quit
 */
public class JourneyCLI {
    private final BusNetwork net = new BusNetwork();
    private final Set<String> closed = new TreeSet<>();
    private final PrintStream out;

    public JourneyCLI(PrintStream out) { this.out = out; }

    public static void main(String[] args) throws IOException {
        JourneyCLI cli = new JourneyCLI(System.out);
        if (args.length > 0) cli.exec("load " + args[0]);
        cli.out.println("Journey Recommendation System - type 'help' for commands.");
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        String line;
        while (true) {
            cli.out.print("> "); cli.out.flush();
            if ((line = in.readLine()) == null) break;
            if (!cli.exec(line)) break;
        }
    }

    /** Executes one command; returns false on quit. */
    public boolean exec(String line) {
        String[] t = line.trim().split("[\\s,\\[\\]=:]+");
        if (t.length == 0 || t[0].isEmpty() || t[0].startsWith("#")) return true;
        try {
            switch (t[0].toLowerCase()) {
                case "route" -> {
                    if (t.length < 3) { out.println("usage: route NAME stop1 stop2 ..."); break; }
                    net.addRoute(t[1], Arrays.asList(t).subList(2, t.length));
                    out.println("Route " + t[1] + " = " + net.stopsOf(t[1]));
                }
                case "load" -> {
                    int count = 0;
                    for (String l : Files.readAllLines(Path.of(t[1]))) {
                        String[] p = l.trim().split("[\\s,\\[\\]=:]+");
                        if (p.length >= 2 && !p[0].isEmpty() && !p[0].startsWith("#")) {
                            net.addRoute(p[0], Arrays.asList(p).subList(1, p.length)); count++;
                        }
                    }
                    out.println("Loaded " + count + " routes from " + t[1]);
                }
                case "close" -> { for (int i = 1; i < t.length; i++) closeRoute(t[i]); out.println("Inactive: " + closed); }
                case "open" -> {
                    if (t.length > 1 && t[1].equalsIgnoreCase("all")) closed.clear();
                    else for (int i = 1; i < t.length; i++) closed.remove(t[i]);
                    out.println("Inactive: " + closed);
                }
                case "go", "query" -> {
                    if (t.length < 3) { out.println("usage: go SOURCE DEST [-x ROUTE ...]"); break; }
                    Set<String> inactive = new TreeSet<>(closed);
                    int x = Arrays.asList(t).indexOf("-x");
                    if (x > 0) for (int i = x + 1; i < t.length; i++) {
                        if (!net.hasRoute(t[i])) out.println("(warning: unknown route " + t[i] + ")");
                        inactive.add(t[i]);
                    }
                    if (!inactive.isEmpty()) out.println("Ignoring inactive routes: " + inactive);
                    long t0 = System.nanoTime();
                    BusNetwork.Journey j = net.query(t[1], t[2], inactive);
                    out.println(j.describe());
                    out.printf("  (searched in %.2f ms)%n", (System.nanoTime() - t0) / 1e6);
                }
                case "show" -> {
                    for (String r : net.routeNames())
                        out.println("  " + r + (closed.contains(r) ? " [INACTIVE]" : "") + " = " + net.stopsOf(r));
                }
                case "help" -> out.println("""
                    route R1 1 2 7      add a route
                    load FILE           load routes ('R1: 1 2 7' per line)
                    close R2            mark a route inactive
                    open R2 | open all  reactivate
                    go 1 6 [-x R2]      find fewest-bus journey
                    show                list network
                    quit""");
                case "quit", "exit" -> { return false; }
                default -> out.println("Unknown command. Type 'help'.");
            }
        } catch (Exception e) {
            out.println("Error: " + e.getMessage());
        }
        return true;
    }

    private void closeRoute(String r) {
        if (!net.hasRoute(r)) out.println("(warning: unknown route " + r + ")");
        closed.add(r);
    }
}
