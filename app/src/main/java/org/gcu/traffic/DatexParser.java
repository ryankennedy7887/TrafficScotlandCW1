package org.gcu.traffic;

import static org.gcu.traffic.TrafficData.*;
import org.gcu.traffic.TrafficData.Record;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class DatexParser {
    private static final String XSI = "http://www.w3.org/2001/XMLSchema-instance";
    private static final String DATEX = "http://datex2.eu/schema/2/2_0";

    static final class Publication {
        final String time;
        final List<Situation> situations;
        Publication(String time, List<Situation> situations) {
            this.time = time; this.situations = immutable(situations);
        }
    }

    Publication parse(String xml, Feed feed) throws XmlPullParserException, IOException {
        // The coursework explicitly requires parsing the downloaded String on the device.
        if (xml.contains("<!DOCTYPE")) throw new XmlPullParserException("Document type declarations are not allowed.");
        XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
        factory.setNamespaceAware(true);
        XmlPullParser p = factory.newPullParser();
        if (p.getFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL))
            p.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false);
        p.setInput(new StringReader(xml));
        Deque<Tag> stack = new ArrayDeque<>();
        List<Situation> situations = new ArrayList<>();
        boolean publication = false;
        int publicationDepth = 0;
        String time = "", id = "", status = "";
        List<Record> records = null;
        int situationDepth = 0, recordDepth = 0, locationDepth = 0, placeDepth = 0;
        RecordBuilder record = null;
        LocationBuilder location = null;
        PlaceBuilder place = null, loose = null;

        for (int event = p.getEventType(); event != XmlPullParser.END_DOCUMENT; event = p.next()) {
            if (event == XmlPullParser.START_TAG) {
                String name = p.getName();
                int depth = p.getDepth();
                String parent = stack.isEmpty() ? "" : stack.peek().name;
                String language = attribute(p, null, "lang");
                if (language.isEmpty()) language = attribute(p, "http://www.w3.org/XML/1998/namespace", "lang");
                stack.push(new Tag(name, language));
                if (name.equals("payloadPublication") && "d2LogicalModel".equals(parent) && DATEX.equals(p.getNamespace())) {
                    publication = true; publicationDepth = depth;
                } else if (publicationDepth > 0 && depth == publicationDepth + 1 && name.equals("situation")) {
                    id = attribute(p, null, "id"); status = "";
                    records = new ArrayList<>(); situationDepth = depth;
                } else if (records != null && depth == situationDepth + 1 && name.equals("situationRecord")) {
                    record = new RecordBuilder(attribute(p, null, "id"), attribute(p, XSI, "type"));
                    recordDepth = depth;
                } else if (record != null && location == null && (name.equals("groupOfLocations")
                        || name.equals("tpegLinearLocation") || name.equals("tpegPointLocation"))) {
                    location = new LocationBuilder(attribute(p, XSI, "type")); locationDepth = depth;
                    loose = new PlaceBuilder("point");
                } else if (location != null && place == null
                        && (name.equals("from") || name.equals("to") || name.equals("tpegPoint"))) {
                    place = new PlaceBuilder(name.equals("tpegPoint") ? "point" : name); placeDepth = depth;
                }
            } else if ((event == XmlPullParser.TEXT || event == XmlPullParser.CDSECT) && !stack.isEmpty()) {
                stack.peek().text.append(p.getText());
            } else if (event == XmlPullParser.END_TAG) {
                Tag tag = stack.pop();
                String value = tag.text.toString().trim();
                int depth = p.getDepth();
                String parent = stack.isEmpty() ? "" : stack.peek().name;
                if (!value.isEmpty()) {
                    if (record != null) {
                        if (tag.name.equals("value") && has(stack, "generalPublicComment")) {
                            if (record.comment.isEmpty()) record.comment = value;
                            if (tag.language.toLowerCase(java.util.Locale.ROOT).startsWith("en"))
                                record.english = record.english.isEmpty() ? value : record.english + "\n" + value;
                        } else if (location != null) {
                            PlaceBuilder target = place == null ? loose : place;
                            switch (tag.name) {
                                case "latitude": target.lat = value; break;
                                case "longitude": target.lon = value; break;
                                case "tpegDirection":
                                case "directionBound": location.direction = value; break;
                                case "tpegLinearLocationType": location.type = value; break;
                                case "roadNumber":
                                case "roadName": target.names.add(value); record.roads.add(value); break;
                                case "value":
                                    if (has(stack, "descriptor") || has(stack, "roadName")) {
                                        target.names.add(value); record.roads.add(value);
                                    }
                                    break;
                                default: break;
                            }
                        } else {
                            record.read(tag.name, value);
                        }
                    } else if (records != null && tag.name.equals("informationStatus") && parent.equals("headerInformation")) {
                        status = value;
                    } else if (publicationDepth > 0 && depth == publicationDepth + 1 && tag.name.equals("publicationTime")) {
                        time = value;
                    }
                }
                if (place != null && depth == placeDepth) {
                    location.add(place); place = null;
                }
                if (location != null && depth == locationDepth) {
                    location.add(loose); record.locations.add(location.build()); location = null; loose = null;
                }
                if (record != null && depth == recordDepth) {
                    records.add(record.build()); record = null;
                }
                if (records != null && depth == situationDepth) {
                    if (!records.isEmpty()) situations.add(new Situation(feed,
                            id.isEmpty() ? "unnamed-" + (situations.size() + 1) : id, status, records));
                    records = null;
                }
                if (depth == publicationDepth) publicationDepth = 0;
            }
        }
        if (!publication) throw new XmlPullParserException("Response is not a DATEX II publication.");
        return new Publication(time, situations);
    }

    private static boolean has(Deque<Tag> stack, String name) {
        for (Tag t : stack) if (name.equals(t.name)) return true;
        return false;
    }

    private static String attribute(XmlPullParser p, String ns, String name) {
        // XmlPullParser implementations differ on the meaning of a null namespace.
        for (int i = 0; i < p.getAttributeCount(); i++) {
            String actual = p.getAttributeNamespace(i);
            if (name.equals(p.getAttributeName(i)) &&
                    (ns == null ? actual == null || actual.isEmpty() : ns.equals(actual)))
                return p.getAttributeValue(i);
        }
        return "";
    }

    private static final class Tag {
        final String name, language;
        final StringBuilder text = new StringBuilder();
        Tag(String name, String language) { this.name = name; this.language = language; }
    }
    private static final class PlaceBuilder {
        final String role;
        final Set<String> names = new LinkedHashSet<>();
        String lat = "", lon = "";
        PlaceBuilder(String role) { this.role = role; }
        Place build() { return new Place(role, String.join(" · ", names), Point.parse(lat, lon)); }
    }
    private static final class LocationBuilder {
        final List<Place> places = new ArrayList<>();
        String direction = "", type;
        LocationBuilder(String type) { this.type = type; }
        void add(PlaceBuilder builder) {
            Place p = builder.build();
            if (p.point != null || !p.name.isEmpty()) places.add(p);
        }
        Location build() { return new Location(direction, type, places); }
    }
    private static final class RecordBuilder {
        final String id, type;
        final List<Location> locations = new ArrayList<>();
        final Set<String> roads = new LinkedHashSet<>();
        String subtype = "", comment = "", english = "", source = "", certainty = "", compliance = "", urgent = "";
        String start = "", end = "", validity = "", updated = "", delays = "", open = "", restricted = "";
        String minimum = "", maximum = "", capacity = "";
        RecordBuilder(String id, String type) {
            this.id = id; this.type = type.contains(":") ? type.substring(type.indexOf(':') + 1) : type;
        }
        void read(String tag, String v) {
            switch (tag) {
                case "roadMaintenanceType":
                case "roadOrCarriagewayOrLaneManagementType":
                case "abnormalTrafficType":
                case "accidentType":
                case "obstructionType": subtype = v; break;
                case "overallStartTime": start = v; break;
                case "overallEndTime": end = v; break;
                case "validityStatus": validity = v; break;
                case "situationRecordVersionTime": updated = v; break;
                case "sourceIdentification": source = v; break;
                case "probabilityOfOccurrence": certainty = v; break;
                case "complianceOption": compliance = v; break;
                case "urgentRoadworks": urgent = v; break;
                case "delaysType": delays = v; break;
                case "numberOfOperationalLanes": open = v; break;
                case "numberOfLanesRestricted": restricted = v; break;
                case "delayTimeValue":
                case "minimumDelayTime": minimum = v; break;
                case "maximumDelayTime": maximum = v; break;
                case "capacityRemaining": capacity = v; break;
                case "roadNumber":
                case "roadName": roads.add(v); break;
                default: break;
            }
        }
        Record build() {
            return new Record(id, type, subtype, english.isEmpty() ? comment : english, source, certainty,
                    compliance, urgent, start, end, validity, updated,
                    new Impact(delays, open, restricted, minimum, maximum, capacity), locations, new ArrayList<>(roads));
        }
    }
}
