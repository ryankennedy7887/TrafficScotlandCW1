package org.gcu.traffic;

import static org.gcu.traffic.TrafficData.*;
import static org.junit.Assert.*;
import org.gcu.traffic.TrafficData.Record;

import org.junit.Test;
import org.xmlpull.v1.XmlPullParserException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class DatexParserTest {
    private final DatexParser parser = new DatexParser();
    private static final String START = "<d2LogicalModel xmlns='http://datex2.eu/schema/2/2_0' "
            + "xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance'><payloadPublication>";
    private static final String END = "</payloadPublication></d2LogicalModel>";

    private DatexParser.Publication fixture(String name, Feed feed) throws Exception {
        byte[] bytes = Files.readAllBytes(Paths.get("src/main/assets", name));
        return parser.parse(new String(bytes, StandardCharsets.UTF_8), feed);
    }

    @Test public void allFourCourseworkSnapshots() throws Exception {
        assertEquals(4, fixture("unplannedContentExample.xml", Feed.INCIDENT).situations.size());
        assertEquals(7, fixture("unplannedContentExample1.xml", Feed.INCIDENT).situations.size());
        assertEquals(405, fixture("roadworksContentExample.xml", Feed.WORKS).situations.size());
        assertEquals(452, fixture("roadworksContentExample1.xml", Feed.WORKS).situations.size());
    }

    @Test public void realClosureMatchesSource() throws Exception {
        DatexParser.Publication p = fixture("unplannedContentExample1.xml", Feed.INCIDENT);
        Situation s = p.situations.get(0);
        Record r = s.records.get(0);
        assertEquals("2026-10-26T13:46:39+00:00", p.time);
        assertEquals("I_20261004_357_S", s.id);
        assertEquals("I_20261004_357", r.id);
        assertEquals("RoadOrCarriagewayOrLaneManagement", r.type);
        assertEquals(Kind.CLOSURE, s.kind());
        assertEquals("real", s.status);
        assertEquals("0", r.impact.operational);
        assertEquals("2026-10-04T20:34:08+01:00", r.start);
        assertEquals("The A6091 is closed Eastbound at Galafoot Bridge to allow for essential roadworks.  Diversions are in place.", s.comment());
        assertEquals("eastBound", r.locations.get(0).direction);
        assertEquals(55.604505, r.locations.get(0).at("from").lat, 1e-7);
        assertEquals(-2.71775, r.locations.get(0).at("to").lon, 1e-7);
    }

    @Test public void realRoadworksMatchSource() throws Exception {
        Situation s = fixture("roadworksContentExample1.xml", Feed.WORKS).situations.get(0);
        Record r = s.records.get(0);
        assertEquals("D_159301_S", s.id);
        assertEquals("M8 WB Woodside - A804 Phoenix Rd - Closure", s.comment());
        assertEquals("2027-12-31T23:59:00+00:00", r.end);
        assertEquals("longDelays", r.impact.delays);
        assertEquals(Kind.LONG_WORKS, s.kind());
        assertEquals(Kind.DELAY_WORKS, fixture("roadworksContentExample1.xml", Feed.WORKS).situations.stream()
                .filter(item -> "delays".equals(item.records.get(0).impact.delays)).findFirst().get().kind());
        assertEquals(Kind.SEVERE_WORKS, fixture("roadworksContentExample1.xml", Feed.WORKS).situations.stream()
                .filter(item -> "veryLongDelays".equals(item.records.get(0).impact.delays)).findFirst().get().kind());
        assertTrue(s.routes.containsAll(List.of("M8", "A804")));
    }

    @Test public void preciseRoadNumberAndNamedRoadSearch() throws Exception {
        List<Situation> works = fixture("roadworksContentExample1.xml", Feed.WORKS).situations;
        Situation first = works.get(0);
        assertFalse(first.matchesRoad("M80"));
        assertTrue(first.matchesRoad(" m 8 "));
        assertTrue(first.matchesRoad("phoenix rd"));
        assertTrue(routesIn("Delays on A74(M) northbound").contains("A74(M)"));
        assertTrue(TrafficViewModel.results(works, new TrafficViewModel.Filter("M8", 1, 0)).isEmpty());
        assertTrue(TrafficViewModel.results(works, new TrafficViewModel.Filter("M8", 2, 0)).size() > 1);
    }

    @Test public void distanceMatchesSegmentAndRadius() throws Exception {
        Point center = new Point(55.864, -4.25), from = new Point(55.864, -4.35), to = new Point(55.864, -4.15);
        assertEquals(0, segmentDistance(center, from, to), 1e-7);
        assertTrue(distanceKm(center, from) > 5);
        assertEquals(111.2, distanceKm(new Point(0, 0), new Point(1, 0)), .1);
        List<Situation> works = fixture("roadworksContentExample1.xml", Feed.WORKS).situations;
        List<Situation> near = TrafficViewModel.results(works, new TrafficViewModel.Filter("M8", 2, 5));
        assertFalse(near.isEmpty());
        double last = 0;
        for (Situation s : near) {
            double d = s.distance(GLASGOW);
            assertTrue(d <= 5 && d >= last);
            last = d;
        }
    }

    @Test public void unknownFieldsMissingCoordinatesAndMultipleRecords() throws Exception {
        var p = parser.parse(START + "<situation id='1'>"
                + "<situationRecord id='r1' xsi:type='FutureType'><notYetKnown>value</notYetKnown></situationRecord>"
                + "<situationRecord id='r2' xsi:type='AbnormalTraffic'><generalPublicComment><comment><values>"
                + "<value lang='fr'>French</value><value lang='en'>English &amp; relevant</value>"
                + "</values></comment></generalPublicComment><groupOfLocations><pointCoordinates><latitude>101</latitude>"
                + "<longitude>-4</longitude></pointCoordinates></groupOfLocations></situationRecord></situation>" + END, Feed.INCIDENT);
        Situation row = p.situations.get(0);
        assertEquals(2, row.records.size());
        assertEquals("English & relevant", row.comment());
        assertEquals(Kind.QUEUE, row.kind());
        assertTrue(Double.isInfinite(row.distance(GLASGOW)));
    }

    @Test public void standalonePointIsGeocoded() throws Exception {
        var p = parser.parse(START + "<situation id='p'><situationRecord xsi:type='Accident'><groupOfLocations>"
                + "<tpegPointLocation><tpegPoint><pointCoordinates><latitude>55.8642</latitude>"
                + "<longitude>-4.2518</longitude></pointCoordinates></tpegPoint></tpegPointLocation>"
                + "</groupOfLocations></situationRecord></situation>" + END, Feed.INCIDENT);
        assertEquals(0, p.situations.get(0).distance(GLASGOW), 1e-7);
        assertEquals(Kind.COLLISION, p.situations.get(0).kind());
    }

    @Test public void emptyPublicationIsValid() throws Exception {
        assertTrue(parser.parse(START + END, Feed.INCIDENT).situations.isEmpty());
    }

    @Test(expected = XmlPullParserException.class) public void rejectsLoginHtml() throws Exception {
        parser.parse("<html><body>Log in</body></html>", Feed.WORKS);
    }

    @Test(expected = XmlPullParserException.class) public void rejectsDocumentTypeDeclarations() throws Exception {
        parser.parse("<!DOCTYPE d2LogicalModel [<!ENTITY name 'example'>]>" + START + END, Feed.WORKS);
    }

    @Test public void rejectsBrokenXml() throws Exception {
        try {
            parser.parse(START + "<situation></payloadPublication></d2LogicalModel>", Feed.WORKS);
            fail("Malformed response was accepted");
        } catch (XmlPullParserException | IOException expected) {
            assertNotNull(expected.getMessage());
        }
    }
}
