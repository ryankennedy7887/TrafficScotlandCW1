package org.gcu.traffic;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class TrafficData {
    static final Point GLASGOW = new Point(55.8642, -4.2518);
    private static final Pattern ROUTE = Pattern.compile("(?i)\\b([ABM])\\s*(\\d{1,4})(?:\\s*\\(M\\))?\\b");
    private static final Pattern ROUTE_WITH_M = Pattern.compile("(?i)\\b([ABM])\\s*(\\d{1,4})\\s*\\(M\\)");

    enum Feed {
        INCIDENT("UnplannedEvents", "unplannedContentExample1.xml", "Incidents"),
        WORKS("CurrentRoadworks", "roadworksContentExample1.xml", "Roadworks");
        final String endpoint, asset, label;
        Feed(String endpoint, String asset, String label) {
            this.endpoint = endpoint; this.asset = asset; this.label = label;
        }
    }

    enum Kind {
        CLOSURE("Road closure", 0xffac293a),
        QUEUE("Traffic queue", 0xff825000),
        COLLISION("Collision", 0xffa32756),
        SEVERE_WORKS("Roadworks · very long delays", 0xff7a1634),
        LONG_WORKS("Roadworks · long delays", 0xffac293a),
        DELAY_WORKS("Roadworks · delays", 0xff825000),
        WORKS("Roadworks", 0xff14665b),
        INCIDENT("Other incident", 0xff375580);
        final String label;
        final int color;
        Kind(String label, int color) { this.label = label; this.color = color; }
    }

    static final class Point {
        final double lat, lon;
        Point(double lat, double lon) { this.lat = lat; this.lon = lon; }
        static Point parse(String lat, String lon) {
            try {
                Point p = new Point(Double.parseDouble(lat), Double.parseDouble(lon));
                return Math.abs(p.lat) <= 90 && Math.abs(p.lon) <= 180 ? p : null;
            } catch (RuntimeException e) { return null; }
        }
    }

    static final class Place {
        final String role, name;
        final Point point;
        Place(String role, String name, Point point) {
            this.role = role; this.name = name; this.point = point;
        }
    }

    static final class Location {
        final String direction, type;
        final List<Place> places;
        Location(String direction, String type, List<Place> places) {
            this.direction = direction; this.type = type;
            this.places = immutable(places);
        }
        Point at(String role) {
            for (Place p : places) if (role.equals(p.role) && p.point != null) return p.point;
            return null;
        }
    }

    static final class Impact {
        final String delays, operational, restricted, minimum, maximum, capacity;
        Impact(String delays, String operational, String restricted, String minimum, String maximum, String capacity) {
            this.delays = delays; this.operational = operational; this.restricted = restricted;
            this.minimum = minimum; this.maximum = maximum; this.capacity = capacity;
        }
    }

    static final class Record {
        final String id, type, subtype, description, source, certainty, compliance, urgent;
        final String start, end, validity, updated;
        final Impact impact;
        final List<Location> locations;
        final List<String> roadNames;
        Record(String id, String type, String subtype, String description, String source,
               String certainty, String compliance, String urgent, String start, String end,
               String validity, String updated, Impact impact, List<Location> locations, List<String> roadNames) {
            this.id = id; this.type = type; this.subtype = subtype; this.description = description;
            this.source = source; this.certainty = certainty; this.compliance = compliance; this.urgent = urgent;
            this.start = start; this.end = end; this.validity = validity; this.updated = updated;
            this.impact = impact; this.locations = immutable(locations); this.roadNames = immutable(roadNames);
        }
    }

    static final class Situation {
        final Feed feed;
        final String id, status;
        final List<Record> records;
        final Set<String> routes;
        final Kind category;
        Situation(Feed feed, String id, String status, List<Record> records) {
            this.feed = feed; this.id = id; this.status = status; this.records = immutable(records);
            Set<String> found = new LinkedHashSet<>();
            for (Record r : records) {
                for (String s : r.roadNames) found.addAll(routesIn(s));
                found.addAll(routesIn(r.description));
            }
            routes = Collections.unmodifiableSet(found);
            category = classify();
        }
        String key() { return feed.name() + ":" + id; }
        String comment() {
            for (Record r : records) if (!r.description.isEmpty()) return r.description;
            return "No public comment supplied.";
        }
        Kind kind() { return category; }
        private Kind classify() {
            if (feed == Feed.WORKS) {
                boolean longDelay = false, delay = false;
                for (Record r : records) {
                    if ("veryLongDelays".equalsIgnoreCase(r.impact.delays)) return Kind.SEVERE_WORKS;
                    longDelay |= "longDelays".equalsIgnoreCase(r.impact.delays);
                    delay |= !r.impact.delays.isEmpty() && !"noDelays".equalsIgnoreCase(r.impact.delays);
                }
                return longDelay ? Kind.LONG_WORKS : delay ? Kind.DELAY_WORKS : Kind.WORKS;
            }
            boolean collision = false, queue = false;
            for (Record r : records) {
                String type = (r.type + " " + r.subtype).toLowerCase(Locale.ROOT);
                if (type.contains("roadclosed") || type.contains("carriagewayclosed")) return Kind.CLOSURE;
                collision |= type.contains("accident") || type.contains("collision");
                queue |= type.contains("queue") || type.contains("queuing") || type.contains("abnormaltraffic");
            }
            return collision ? Kind.COLLISION : queue ? Kind.QUEUE : Kind.INCIDENT;
        }
        boolean matchesRoad(String input) {
            String q = input.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
            if (q.isEmpty()) return true;
            if (q.matches("[ABM]\\s*\\d{1,4}(?:\\s*\\(M\\))?")) return routes.contains(q.replace(" ", ""));
            for (Record r : records) {
                for (String road : r.roadNames) if (road.toUpperCase(Locale.ROOT).contains(q)) return true;
                for (Location l : r.locations) for (Place p : l.places)
                    if (p.name.toUpperCase(Locale.ROOT).contains(q)) return true;
                if (r.description.toUpperCase(Locale.ROOT).contains(q)) return true;
            }
            return false;
        }
        double distance(Point origin) {
            double distance = Double.POSITIVE_INFINITY;
            for (Record r : records) for (Location l : r.locations) {
                Point from = l.at("from"), to = l.at("to");
                if (from != null && to != null) distance = Math.min(distance, segmentDistance(origin, from, to));
                for (Place p : l.places) if (p.point != null) distance = Math.min(distance, distanceKm(origin, p.point));
            }
            return distance;
        }
    }

    static Set<String> routesIn(String text) {
        Set<String> routes = new LinkedHashSet<>();
        Matcher special = ROUTE_WITH_M.matcher(text);
        while (special.find()) routes.add(special.group(1).toUpperCase(Locale.ROOT) + special.group(2) + "(M)");
        Matcher m = ROUTE.matcher(text);
        while (m.find()) routes.add(m.group(1).toUpperCase(Locale.ROOT) + m.group(2));
        return routes;
    }

    static double distanceKm(Point a, Point b) {
        double dLat = Math.toRadians(b.lat - a.lat), dLon = Math.toRadians(b.lon - a.lon);
        double h = Math.pow(Math.sin(dLat / 2), 2) + Math.cos(Math.toRadians(a.lat))
                * Math.cos(Math.toRadians(b.lat)) * Math.pow(Math.sin(dLon / 2), 2);
        return 12742 * Math.asin(Math.sqrt(Math.min(1, h)));
    }

    // Approximate point-to-segment distance for Scottish road links; not driving distance.
    static double segmentDistance(Point origin, Point a, Point b) {
        double scale = Math.cos(Math.toRadians(origin.lat));
        double ax = (a.lon - origin.lon) * scale, ay = a.lat - origin.lat;
        double bx = (b.lon - origin.lon) * scale, by = b.lat - origin.lat;
        double dx = bx - ax, dy = by - ay, length2 = dx * dx + dy * dy;
        if (length2 == 0) return distanceKm(origin, a);
        double t = Math.max(0, Math.min(1, -(ax * dx + ay * dy) / length2));
        return distanceKm(origin, new Point(a.lat + (b.lat - a.lat) * t, a.lon + (b.lon - a.lon) * t));
    }

    static String friendly(String value) {
        if (value.isEmpty()) return value;
        String spaced = value.replaceAll("([a-z])([A-Z])", "$1 $2").replace('_', ' ');
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    static String date(String value) {
        if (value.isEmpty()) return "";
        try {
            return OffsetDateTime.parse(value).atZoneSameInstant(ZoneId.of("Europe/London"))
                    .format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm z", Locale.UK));
        } catch (RuntimeException e) { return value; }
    }

    static <T> List<T> immutable(List<T> values) {
        return Collections.unmodifiableList(new ArrayList<>(values));
    }
}
