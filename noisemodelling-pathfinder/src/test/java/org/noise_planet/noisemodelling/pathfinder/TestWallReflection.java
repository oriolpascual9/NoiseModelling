/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */
package org.noise_planet.noisemodelling.pathfinder;

import org.h2.tools.Csv;
import org.junit.Test;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;
import org.locationtech.jts.io.WKTWriter;
import org.locationtech.jts.operation.buffer.BufferParameters;
import org.noise_planet.noisemodelling.pathfinder.cnossos.CnossosPath;
import org.noise_planet.noisemodelling.pathfinder.path.MirrorReceiver;
import org.noise_planet.noisemodelling.pathfinder.path.MirrorReceiversCompute;
import org.noise_planet.noisemodelling.pathfinder.path.PointPath;
import org.noise_planet.noisemodelling.pathfinder.path.Scene;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.ProfileBuilder;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.Wall;
import org.noise_planet.noisemodelling.pathfinder.utils.geometry.Orientation;

import java.io.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TestWallReflection {

    @Test
    public void testWideWall() {

        List<Wall> buildWalls = new ArrayList<>();
        Coordinate cA = new Coordinate(50, 100, 5);
        Coordinate cB = new Coordinate(150, 100, 5);
        buildWalls.add(new Wall(cA, cB, 0, ProfileBuilder.IntersectionType.WALL));

        Polygon polygon = MirrorReceiversCompute.createWallReflectionVisibilityCone(
                new Coordinate(100, 50, 0.1),
                new LineSegment(cA, cB), 100, 100);

        GeometryFactory factory = new GeometryFactory();
        assertTrue(polygon.intersects(factory.createPoint(new Coordinate(100, 145, 0))));
    }

    @Test
    public void testNReflexion() throws ParseException, IOException, SQLException {
        GeometryFactory factory = new GeometryFactory();

        //Create profile builder
        ProfileBuilder profileBuilder = new ProfileBuilder();
        Csv csv = new Csv();
        WKTReader wktReader = new WKTReader();
        try(ResultSet rs = csv.read(new FileReader(
                TestWallReflection.class.getResource("testNReflexionBuildings.csv").getFile()),
                new String[]{"geom", "id"})) {
            assertTrue(rs.next()); //skip column name
            while(rs.next()) {
                profileBuilder.addBuilding(wktReader.read(rs.getString(1)), 10, rs.getInt(2));
            }
        }
        profileBuilder.finishFeeding();
        assertEquals(5, profileBuilder.getBuildingCount());
        Scene inputData = new Scene(profileBuilder);
        inputData.addReceiver(new Coordinate(599093.85,646227.90, 4));
        inputData.addSource(factory.createPoint(new Coordinate(599095.21, 646283.77, 1)));
        inputData.setComputeHorizontalDiffraction(false);
        inputData.setComputeVerticalDiffraction(false);
        inputData.maxRefDist = 80;
        inputData.maxSrcDist = 180;
        inputData.setReflexionOrder(2);
        PathFinder computeRays = new PathFinder(inputData);
        computeRays.setThreadCount(1);


        Coordinate receiver = inputData.receivers.get(0);
        Envelope receiverPropagationEnvelope = new Envelope(receiver);
        receiverPropagationEnvelope.expandBy(inputData.maxSrcDist);
        List<Wall> buildWalls = inputData.profileBuilder.getWallsIn(receiverPropagationEnvelope);
        MirrorReceiversCompute receiverMirrorIndex = new MirrorReceiversCompute(buildWalls, receiver,
                inputData.reflexionOrder, inputData.maxSrcDist, inputData.maxRefDist);

        // Keep only mirror receivers potentially visible from the source(and its parents)
        List<MirrorReceiver> mirrorResults = receiverMirrorIndex.findCloseMirrorReceivers(inputData.
                sourceGeometries.get(0).getCoordinate());

        try {
            try (FileWriter fileWriter = new FileWriter("target/testNReflexion_testVisibilityCone.csv")) {
                StringBuilder sb = new StringBuilder();
                receiverMirrorIndex.exportVisibility(sb, inputData.maxSrcDist, inputData.maxRefDist,
                        0, mirrorResults, true);
                fileWriter.write(sb.toString());
            }
        } catch (IOException ex) {
            //ignore
        }

        assertEquals(4, mirrorResults.size());

        List<CnossosPath> CnossosPaths = computeRays.computeReflexion(receiver,
                inputData.sourceGeometries.get(0).getCoordinate(), false,
                new Orientation(), receiverMirrorIndex);

        // Only one second order reflexion propagation path must be found
        assertEquals(1, CnossosPaths.size());
        // Check expected values for the propagation path
        CnossosPath firstPath = CnossosPaths.get(0);
        var it = firstPath.getPointList().iterator();
        assertTrue(it.hasNext());
        PointPath current = it.next();
        assertEquals(PointPath.POINT_TYPE.SRCE ,current.type);
        assertEquals(0.0, current.coordinate.x, 1e-12);
        current = it.next();
        assertEquals(PointPath.POINT_TYPE.REFL ,current.type);
        assertEquals(38.68, current.coordinate.x, 0.02);
        current = it.next();
        assertEquals(PointPath.POINT_TYPE.REFL ,current.type);
        assertEquals(53.28, current.coordinate.x, 0.02);
        current = it.next();
        assertEquals(PointPath.POINT_TYPE.RECV ,current.type);
        assertEquals(61.14, current.coordinate.x, 0.02);
    }
    @Test
    public void testNReflexionWithDem() throws ParseException, IOException, SQLException {
        GeometryFactory factory = new GeometryFactory();

        //Create profile builder
        ProfileBuilder profileBuilder = new ProfileBuilder();
        profileBuilder.setzBuildings(false); // building Z is height not altitude
        Csv csv = new Csv();
        WKTReader wktReader = new WKTReader();
        try(ResultSet rs = csv.read(new FileReader(
                        TestWallReflection.class.getResource("testNReflexionBuildings.csv").getFile()),
                new String[]{"geom", "id"})) {
            assertTrue(rs.next()); //skip column name
            while(rs.next()) {
                profileBuilder.addBuilding(wktReader.read(rs.getString(1)), 10, rs.getInt(2));
            }
        }
        profileBuilder.addTopographicPoint(new Coordinate(598962.08,646370.83,500.00));
        profileBuilder.addTopographicPoint(new Coordinate(599252.92,646370.11,500.00));
        profileBuilder.addTopographicPoint(new Coordinate(599254.37,646100.19,500.00));
        profileBuilder.addTopographicPoint(new Coordinate(598913.00,646104.52,500.00));
        profileBuilder.finishFeeding();
        assertEquals(5, profileBuilder.getBuildingCount());
        Scene inputData = new Scene(profileBuilder);
        inputData.addReceiver(new Coordinate(599093.85,646227.90, 504));
        inputData.addSource(factory.createPoint(new Coordinate(599095.21, 646283.77, 501)));
        inputData.setComputeHorizontalDiffraction(false);
        inputData.setComputeVerticalDiffraction(false);
        inputData.maxRefDist = 80;
        inputData.maxSrcDist = 180;
        inputData.setReflexionOrder(2);
        PathFinder computeRays = new PathFinder(inputData);
        computeRays.setThreadCount(1);


        Coordinate receiver = inputData.receivers.get(0);
        Envelope receiverPropagationEnvelope = new Envelope(receiver);
        receiverPropagationEnvelope.expandBy(inputData.maxSrcDist);
        List<Wall> buildWalls = inputData.profileBuilder.getWallsIn(receiverPropagationEnvelope);
        MirrorReceiversCompute receiverMirrorIndex = new MirrorReceiversCompute(buildWalls, receiver,
                inputData.reflexionOrder, inputData.maxSrcDist, inputData.maxRefDist);

        // Keep only mirror receivers potentially visible from the source(and its parents)
        List<MirrorReceiver> mirrorResults = receiverMirrorIndex.findCloseMirrorReceivers(inputData.
                sourceGeometries.get(0).getCoordinate());

        assertEquals(4, mirrorResults.size());

        List<CnossosPath> CnossosPaths = computeRays.computeReflexion(receiver,
                inputData.sourceGeometries.get(0).getCoordinate(), false,
                new Orientation(), receiverMirrorIndex);

        // Only one second order reflexion propagation path must be found
        assertEquals(1, CnossosPaths.size());
        // Check expected values for the propagation path
        CnossosPath firstPath = CnossosPaths.get(0);
        var it = firstPath.getPointList().iterator();
        assertTrue(it.hasNext());
        PointPath current = it.next();
        assertEquals(PointPath.POINT_TYPE.SRCE ,current.type);
        assertEquals(0.0, current.coordinate.x, 1e-12);
        assertEquals(501.0, current.coordinate.y, 1e-12);
        current = it.next();
        assertEquals(PointPath.POINT_TYPE.REFL ,current.type);
        assertEquals(38.68, current.coordinate.x, 0.02);
        assertEquals(502.9, current.coordinate.y, 0.02);
        current = it.next();
        assertEquals(PointPath.POINT_TYPE.REFL ,current.type);
        assertEquals(53.28, current.coordinate.x, 0.02);
        assertEquals(503.61, current.coordinate.y, 0.02);
        current = it.next();
        assertEquals(PointPath.POINT_TYPE.RECV ,current.type);
        assertEquals(61.14, current.coordinate.x, 0.02);
        assertEquals(504, current.coordinate.y, 0.02);
    }
}
