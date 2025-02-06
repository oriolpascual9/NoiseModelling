/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */

package org.noise_planet.noisemodelling.jdbc;

import org.h2gis.api.EmptyProgressVisitor;
import org.h2gis.functions.factory.H2GISDBFactory;
import org.h2gis.utilities.JDBCUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.io.WKTWriter;
import org.noise_planet.noisemodelling.jdbc.input.DefaultTableLoader;
import org.noise_planet.noisemodelling.jdbc.input.SceneDatabaseInputSettings;
import org.noise_planet.noisemodelling.jdbc.input.SceneWithEmission;
import org.noise_planet.noisemodelling.jdbc.output.NoiseMapWriter;
import org.noise_planet.noisemodelling.jdbc.utils.CellIndex;
import org.noise_planet.noisemodelling.pathfinder.*;
import org.noise_planet.noisemodelling.propagation.cnossos.CnossosPath;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.GroundAbsorption;
import org.noise_planet.noisemodelling.pathfinder.utils.geometry.Orientation;
import org.noise_planet.noisemodelling.pathfinder.utils.profiler.RootProgressVisitor;
import org.noise_planet.noisemodelling.propagation.cnossos.AttenuationCnossosParameters;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.noise_planet.noisemodelling.jdbc.Utils.getRunScriptRes;

public class NoiseMapByReceiverMakerTest {

    private Connection connection;

    @BeforeEach
    public void tearUp() throws Exception {
        connection = JDBCUtilities.wrapConnection(H2GISDBFactory.createSpatialDataBase(NoiseMapByReceiverMakerTest.class.getSimpleName(), true, ""));
    }

    @AfterEach
    public void tearDown() throws Exception {
        if(connection != null) {
            connection.close();
        }
    }

    /**
     * Check if ground surface are split according to {@link GridMapMaker#groundSurfaceSplitSideLength}
     * @throws Exception
     */
    @Test
    public void testGroundSurface() throws Exception {
        try(Statement st = connection.createStatement()) {
            st.execute(String.format("CALL SHPREAD('%s', 'LANDCOVER2000')", NoiseMapByReceiverMakerTest.class.getResource("landcover2000.shp").getFile()));
            st.execute(getRunScriptRes("scene_with_landcover.sql"));
            NoiseMapByReceiverMaker noiseMapByReceiverMaker = new NoiseMapByReceiverMaker("BUILDINGS", "ROADS_GEOM", "RECEIVERS");
            noiseMapByReceiverMaker.setHeightField("HEIGHT");
            noiseMapByReceiverMaker.setSoilTableName("LAND_G");
            noiseMapByReceiverMaker.initialize(connection, new EmptyProgressVisitor());

            Set<Long> processedReceivers = new HashSet<>();
            Map<CellIndex, Integer> populatedCells = noiseMapByReceiverMaker.searchPopulatedCells(connection);
            double expectedMaxArea = Math.pow(noiseMapByReceiverMaker.getGroundSurfaceSplitSideLength(), 2);
            assertFalse(populatedCells.isEmpty());
            for (Map.Entry<CellIndex, Integer> indexIntegerEntry : populatedCells.entrySet()) {
                SceneWithEmission scene = noiseMapByReceiverMaker.prepareCell(connection, indexIntegerEntry.getKey(), processedReceivers);
                assertFalse(scene.profileBuilder.getGroundEffects().isEmpty());
                for(GroundAbsorption soil : scene.profileBuilder.getGroundEffects()) {
                    assertTrue(soil.getGeometry().getArea() < expectedMaxArea);
                }
            }
        }
    }

    @Test
    public void testNoiseMapBuilding() throws Exception {
        try(Statement st = connection.createStatement()) {
            st.execute(String.format("CALL SHPREAD('%s', 'LANDCOVER2000')", NoiseMapByReceiverMakerTest.class.getResource("landcover2000.shp").getFile()));
            st.execute(getRunScriptRes("scene_with_landcover.sql"));
            DelaunayReceiversMaker noisemap = new DelaunayReceiversMaker("BUILDINGS", "ROADS_GEOM");
            noisemap.setReceiverHasAbsoluteZCoordinates(false);
            noisemap.setSourceHasAbsoluteZCoordinates(false);
            noisemap.setHeightField("HEIGHT");
            noisemap.initialize(connection, new EmptyProgressVisitor());

            AtomicInteger pk = new AtomicInteger(0);
            for(int i=0; i < noisemap.getGridDim(); i++) {
                for(int j=0; j < noisemap.getGridDim(); j++) {
                    noisemap.generateReceivers(connection, i, j, "NM_RECEIVERS", "TRIANGLES", pk);
                }
            }
            assertNotSame(0, pk.get());
        }
    }

    private static String createSource(Geometry source, double lvl, Orientation sourceOrientation, int directivityId) {
        StringBuilder sb = new StringBuilder("CREATE TABLE ROADS_GEOM(PK SERIAL PRIMARY KEY, THE_GEOM GEOMETRY, YAW REAL, PITCH REAL, ROLL REAL, DIR_ID INT");
        StringBuilder values = new StringBuilder("(row_number() over())::int, ST_SETSRID('");
        values.append(new WKTWriter(3).write(source));
        values.append("', 2154) THE_GEOM, ");
        values.append(sourceOrientation.yaw);
        values.append(" YAW, ");
        values.append(sourceOrientation.pitch);
        values.append(" PITCH, ");
        values.append(sourceOrientation.roll);
        values.append(" ROLL, ");
        values.append(directivityId);
        values.append(" DIR_ID");
        AttenuationCnossosParameters data = new AttenuationCnossosParameters(false);
        for(String period : new String[] {"D", "E", "N"}) {
            for (int freq : data.getFrequencies()) {
                String fieldName = "LW" + period + freq;
                sb.append(", ");
                sb.append(fieldName);
                sb.append(" real");
                values.append(", ");
                values.append(String.format(Locale.ROOT, "%.2f", lvl));
                values.append(" ");
                values.append(fieldName);
            }
        }
        sb.append(") AS select ");
        sb.append(values.toString());
        return sb.toString();
    }


    @Test
    public void testPointDirectivity() throws Exception {
        try (Statement st = connection.createStatement()) {
            st.execute("CREATE TABLE BUILDINGS(pk serial  PRIMARY KEY, the_geom geometry, height real)");
            st.execute(createSource(new GeometryFactory().createPoint(new Coordinate(223915.72,6757480.22,0.0 )),
                    91,
                    new Orientation(90,15,0),
                    4));
            st.execute("create table receivers(id serial PRIMARY KEY, the_geom GEOMETRY(POINTZ));\n" +
                    "insert into receivers(the_geom) values ('POINTZ (223915.72 6757490.22 0.0)');" +
                    "insert into receivers(the_geom) values ('POINTZ (223925.72 6757480.22 0.0)');");
            NoiseMapByReceiverMaker noiseMapByReceiverMaker = new NoiseMapByReceiverMaker("BUILDINGS",
                    "ROADS_GEOM", "RECEIVERS");
            noiseMapByReceiverMaker.setComputeHorizontalDiffraction(false);
            noiseMapByReceiverMaker.setComputeVerticalDiffraction(false);
            noiseMapByReceiverMaker.setSoundReflectionOrder(0);
            noiseMapByReceiverMaker.setMaximumPropagationDistance(1000);
            noiseMapByReceiverMaker.setHeightField("HEIGHT");
            noiseMapByReceiverMaker.setInputMode(SceneDatabaseInputSettings.INPUT_MODE.INPUT_MODE_LW_DEN);
            noiseMapByReceiverMaker.getNoiseMapDatabaseParameters().setCoefficientVersion(1);

            // Use train directivity functions instead of discrete directivity
            ((DefaultTableLoader) noiseMapByReceiverMaker.getPropagationProcessDataFactory()).insertTrainDirectivity();

            noiseMapByReceiverMaker.run(connection, new EmptyProgressVisitor());

            NoiseMapDatabaseParameters parameters = noiseMapByReceiverMaker.getNoiseMapDatabaseParameters();

            try(ResultSet rs = st.executeQuery("SELECT LW63 FROM " + parameters.receiversLevelTable + " WHERE PERIOD='DEN' ORDER BY IDRECEIVER")) {
                assertTrue(rs.next());
                assertEquals(73.3, rs.getDouble(1), 0.1);
                assertTrue(rs.next());
                assertEquals(53.3, rs.getDouble(1), 0.1);
                assertFalse(rs.next());
            }
        }
    }



    @Test
    public void testLineDirectivity() throws Exception {
        try (Statement st = connection.createStatement()) {
            st.execute("CREATE TABLE BUILDINGS(pk serial  PRIMARY KEY, the_geom geometry, height real)");
            st.execute(createSource(new GeometryFactory().createLineString(
                    new Coordinate[]{new Coordinate(223915.72,6757480.22 ,5),
                            new Coordinate(223920.72,6757485.22, 5.1 )}), 91,
                    new Orientation(0,0,0),4));
            st.execute("create table receivers(id serial PRIMARY KEY, the_geom GEOMETRY(pointZ));\n" +
                    "insert into receivers(the_geom) values ('POINTZ (223922.55 6757495.27 4.0)');" +
                    "insert into receivers(the_geom) values ('POINTZ (223936.42 6757471.91 4.0)');");
            NoiseMapByReceiverMaker noiseMapByReceiverMaker = new NoiseMapByReceiverMaker("BUILDINGS",
                    "ROADS_GEOM", "RECEIVERS");
            noiseMapByReceiverMaker.setComputeHorizontalDiffraction(false);
            noiseMapByReceiverMaker.setComputeVerticalDiffraction(false);
            noiseMapByReceiverMaker.setSoundReflectionOrder(0);
            noiseMapByReceiverMaker.setMaximumPropagationDistance(1000);
            noiseMapByReceiverMaker.setHeightField("HEIGHT");
            noiseMapByReceiverMaker.setInputMode(SceneDatabaseInputSettings.INPUT_MODE.INPUT_MODE_LW_DEN);
            noiseMapByReceiverMaker.getNoiseMapDatabaseParameters().setCoefficientVersion(1);
            noiseMapByReceiverMaker.getNoiseMapDatabaseParameters().exportRaysMethod = NoiseMapDatabaseParameters.ExportRaysMethods.TO_RAYS_TABLE;
            noiseMapByReceiverMaker.getNoiseMapDatabaseParameters().exportCnossosPathWithAttenuation = true;
            noiseMapByReceiverMaker.getNoiseMapDatabaseParameters().exportAttenuationMatrix = true;
            noiseMapByReceiverMaker.getNoiseMapDatabaseParameters().mergeSources = true;
            noiseMapByReceiverMaker.setBodyBarrier(true);

            // Use train directivity functions instead of discrete directivity
            DefaultTableLoader defaultTableLoader = ((DefaultTableLoader) noiseMapByReceiverMaker.getPropagationProcessDataFactory());
            defaultTableLoader.insertTrainDirectivity();
            AttenuationCnossosParameters daySettings = new AttenuationCnossosParameters();
            daySettings.setTemperature(20);
            AttenuationCnossosParameters eveningSettings = new AttenuationCnossosParameters();
            eveningSettings.setTemperature(18);
            AttenuationCnossosParameters nightSettings = new AttenuationCnossosParameters();
            nightSettings.setTemperature(16);
            defaultTableLoader.cnossosParametersPerPeriod.put("D", daySettings);
            defaultTableLoader.cnossosParametersPerPeriod.put("E", eveningSettings);
            defaultTableLoader.cnossosParametersPerPeriod.put("N", nightSettings);

            noiseMapByReceiverMaker.run(connection, new EmptyProgressVisitor());

            NoiseMapDatabaseParameters parameters = noiseMapByReceiverMaker.getNoiseMapDatabaseParameters();

            try(ResultSet rs = st.executeQuery("SELECT IDRECEIVER, LW63 FROM " + parameters.receiversLevelTable + " WHERE PERIOD='DEN' ORDER BY IDRECEIVER")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1));
                assertEquals(68.3, rs.getDouble(2), 1);
                assertTrue(rs.next());
                assertEquals(2, rs.getInt(1));
                assertEquals(70.8, rs.getDouble(2), 1);
                assertFalse(rs.next());
            }

            try(ResultSet rs = st.executeQuery("SELECT IDRECEIVER, PATH FROM " + parameters.raysTable + " WHERE PERIOD='D' ORDER BY IDRECEIVER")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1));
                CnossosPath cnossosPath = NoiseMapWriter.jsonToPropagationPath(rs.getString(2));
                // This is source orientation, not relevant to receiver position
                assertOrientationEquals(new Orientation(45, 0.81, 0), cnossosPath.getSourceOrientation(), 0.01);
                assertOrientationEquals(new Orientation(330.2084079818916,-5.947213381005439,0.0), cnossosPath.raySourceReceiverDirectivity, 0.01);
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1));
                cnossosPath = NoiseMapWriter.jsonToPropagationPath(rs.getString(2));
                assertOrientationEquals(new Orientation(45, 0.81, 0), cnossosPath.getSourceOrientation(), 0.01);
                assertOrientationEquals(new Orientation(336.9922375343167,-4.684918495003125,0.0), cnossosPath.raySourceReceiverDirectivity, 0.01);
            }
        }
    }


    public static void assertOrientationEquals(Orientation orientationA, Orientation orientationB, double epsilon) {
        assertArrayEquals(new double[]{orientationA.yaw, orientationA.pitch, orientationA.roll},
                new double[]{orientationB.yaw, orientationB.pitch, orientationB.roll}, epsilon, orientationA+" != "+orientationB);
    }

    @Test
    public void testPointRayDirectivity() throws Exception {
        try (Statement st = connection.createStatement()) {
            st.execute("CREATE TABLE BUILDINGS(pk serial  PRIMARY KEY, the_geom geometry, height real)");
            // create source point direction east->90°
            st.execute(createSource(new GeometryFactory().createPoint(new Coordinate(3.5,3,1.0 )),
                    91, new Orientation(90,0,0),4));
            st.execute("create table receivers(id serial PRIMARY KEY, the_geom GEOMETRY(POINTZ));\n" +
                    "insert into receivers(the_geom) values ('POINTZ (4.5 3 1.0)');" + //front
                    "insert into receivers(the_geom) values ('POINTZ (2.5 3 1.0)');" + //behind
                    "insert into receivers(the_geom) values ('POINTZ (3.5 2 1.0)');" + //right
                    "insert into receivers(the_geom) values ('POINTZ (3.5 4 1.0)');"); //left
            NoiseMapByReceiverMaker noiseMapByReceiverMaker = new NoiseMapByReceiverMaker("BUILDINGS",
                    "ROADS_GEOM", "RECEIVERS");
            noiseMapByReceiverMaker.setComputeHorizontalDiffraction(false);
            noiseMapByReceiverMaker.setComputeVerticalDiffraction(false);
            noiseMapByReceiverMaker.setSoundReflectionOrder(0);
            noiseMapByReceiverMaker.setMaximumPropagationDistance(1000);
            noiseMapByReceiverMaker.setHeightField("HEIGHT");
            noiseMapByReceiverMaker.setInputMode(SceneDatabaseInputSettings.INPUT_MODE.INPUT_MODE_LW_DEN);
            noiseMapByReceiverMaker.getNoiseMapDatabaseParameters().setCoefficientVersion(1);
            noiseMapByReceiverMaker.getNoiseMapDatabaseParameters().exportRaysMethod = NoiseMapDatabaseParameters.ExportRaysMethods.TO_RAYS_TABLE;
            noiseMapByReceiverMaker.getNoiseMapDatabaseParameters().exportCnossosPathWithAttenuation = true;
            noiseMapByReceiverMaker.getNoiseMapDatabaseParameters().exportAttenuationMatrix = true;
            noiseMapByReceiverMaker.getNoiseMapDatabaseParameters().mergeSources = true;
            noiseMapByReceiverMaker.setBodyBarrier(true);

            // Use train directivity functions instead of discrete directivity
            DefaultTableLoader defaultTableLoader = ((DefaultTableLoader) noiseMapByReceiverMaker.getPropagationProcessDataFactory());
            defaultTableLoader.insertTrainDirectivity();
            AttenuationCnossosParameters daySettings = new AttenuationCnossosParameters();
            daySettings.setTemperature(20);
            AttenuationCnossosParameters eveningSettings = new AttenuationCnossosParameters();
            eveningSettings.setTemperature(18);
            AttenuationCnossosParameters nightSettings = new AttenuationCnossosParameters();
            nightSettings.setTemperature(16);
            defaultTableLoader.cnossosParametersPerPeriod.put("D", daySettings);
            defaultTableLoader.cnossosParametersPerPeriod.put("E", eveningSettings);
            defaultTableLoader.cnossosParametersPerPeriod.put("N", nightSettings);

            noiseMapByReceiverMaker.run(connection, new EmptyProgressVisitor());

            NoiseMapDatabaseParameters parameters = noiseMapByReceiverMaker.getNoiseMapDatabaseParameters();

            List<CnossosPath> pathsParameters = new ArrayList<>();
            try(ResultSet rs = st.executeQuery("SELECT IDRECEIVER, PATH FROM " + parameters.raysTable + " WHERE PERIOD='D' ORDER BY IDRECEIVER")) {
                while (rs.next()) {
                    CnossosPath cnossosPath = NoiseMapWriter.jsonToPropagationPath(rs.getString("PATH"));
                    pathsParameters.add(cnossosPath);
                }
            }
            assertEquals(4 , pathsParameters.size());
            CnossosPath pathParameters = pathsParameters.remove(0);
            assertEquals(1, pathParameters.getCutProfile().getReceiver().receiverPk);
            // receiver is front of source
            assertEquals(new Orientation(0, 0, 0), pathParameters.getRaySourceReceiverDirectivity());
            pathParameters = pathsParameters.remove(0);
            assertEquals(2, pathParameters.getCutProfile().getReceiver().receiverPk);
            // receiver is behind of the source
            assertEquals(new Orientation(180, 0, 0), pathParameters.getRaySourceReceiverDirectivity());
            pathParameters = pathsParameters.remove(0);
            assertEquals(3, pathParameters.getCutProfile().getReceiver().receiverPk);
            // receiver is on the right of the source
            assertEquals(new Orientation(90, 0, 0), pathParameters.getRaySourceReceiverDirectivity());
            pathParameters = pathsParameters.remove(0);
            assertEquals(4, pathParameters.getCutProfile().getReceiver().receiverPk);
            // receiver is on the left of the source
            assertEquals(new Orientation(360-90, 0, 0), pathParameters.getRaySourceReceiverDirectivity());

        }
    }
}