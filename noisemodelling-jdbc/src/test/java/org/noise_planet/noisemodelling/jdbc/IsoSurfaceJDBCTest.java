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
import org.h2gis.functions.io.geojson.GeoJsonRead;
import org.h2gis.utilities.JDBCUtilities;
import org.h2gis.utilities.SpatialResultSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.noise_planet.noisemodelling.jdbc.input.SceneDatabaseInputSettings;
import org.noise_planet.noisemodelling.jdbc.utils.IsoSurface;
import org.noise_planet.noisemodelling.pathfinder.delaunay.LayerDelaunayError;
import org.noise_planet.noisemodelling.pathfinder.delaunay.LayerTinfour;
import org.noise_planet.noisemodelling.pathfinder.utils.profiler.RootProgressVisitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Paths;
import java.sql.*;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.noise_planet.noisemodelling.jdbc.Utils.getRunScriptRes;

public class IsoSurfaceJDBCTest {

    private Connection connection;
    private static Logger LOGGER = LoggerFactory.getLogger(IsoSurfaceJDBCTest.class);

    @BeforeEach
    public void tearUp() throws Exception {
        connection = JDBCUtilities.wrapConnection(H2GISDBFactory.createSpatialDataBase(IsoSurfaceJDBCTest.class.getSimpleName(), true, ""));
    }

    @AfterEach
    public void tearDown() throws Exception {
        if(connection != null) {
            connection.close();
        }
    }

    @Test
    public void testIsoSurface() throws SQLException, IOException {
        GeoJsonRead.importTable(connection, IsoSurfaceJDBCTest.class.getResource("lden_geom.geojson").getFile());
        GeoJsonRead.importTable(connection, IsoSurfaceJDBCTest.class.getResource("triangles.geojson").getFile());
        try(Statement st = connection.createStatement()) {
            st.execute("ALTER TABLE LDEN_GEOM ALTER COLUMN IDRECEIVER INTEGER NOT NULL");
            st.execute("ALTER TABLE LDEN_GEOM ADD PRIMARY KEY (IDRECEIVER)");
            st.execute("ALTER TABLE TRIANGLES ALTER COLUMN PK INTEGER NOT NULL");
            st.execute("ALTER TABLE TRIANGLES ADD PRIMARY KEY (PK)");
            st.execute("CREATE INDEX ON TRIANGLES(CELL_ID)");
        }

        long start = System.currentTimeMillis();
        IsoSurface isoSurface = new IsoSurface(IsoSurface.NF31_133_ISO, 2154);
        isoSurface.setPointTable("LDEN_GEOM");
        isoSurface.setPointTableField("LAEQ");
        isoSurface.setSmooth(true);
        isoSurface.createTable(connection);
        System.out.println("Contouring done in " + (System.currentTimeMillis() - start) + " ms");

        assertTrue(JDBCUtilities.tableExists(connection, "CONTOURING_NOISE_MAP"));

        List<String> fieldValues = JDBCUtilities.getUniqueFieldValues(connection, "CONTOURING_NOISE_MAP", "ISOLVL");
        assertTrue(fieldValues.contains("0"));
        assertTrue(fieldValues.contains("1"));
        assertTrue(fieldValues.contains("2"));
        assertTrue(fieldValues.contains("3"));
        assertTrue(fieldValues.contains("4"));
        assertTrue(fieldValues.contains("5"));
        assertTrue(fieldValues.contains("6"));
        assertTrue(fieldValues.contains("7"));
        assertTrue(fieldValues.contains("8"));
        assertTrue(fieldValues.contains("9"));

    }

    @Test
    public void testContouring3D() throws SQLException, IOException, LayerDelaunayError {
        // Will create elevation iso from DEM table
        GeoJsonRead.importTable(connection, Paths.get(Paths.get(System.getProperty("user.dir")).getParent().toString(),
                "wps_scripts/src/test/resources/org/noise_planet/noisemodelling/wps/dem.geojson").toString());
        LayerTinfour delaunayTool = new LayerTinfour();
        try (PreparedStatement st = connection.prepareStatement(
                "SELECT the_geom FROM DEM")) {
            try (SpatialResultSet rs = st.executeQuery().unwrap(SpatialResultSet.class)) {
                while (rs.next()) {
                    Geometry pt = rs.getGeometry();
                    if(pt != null) {
                        delaunayTool.addVertex(pt.getCoordinate());
                    }
                }
            }
        }
        delaunayTool.processDelaunay();
        DelaunayReceiversMaker.generateResultTable(connection, "RECEIVERS", "TRIANGLES",
                new AtomicInteger(), delaunayTool.getVertices(), new GeometryFactory(), delaunayTool.getTriangles(),
                0, 0, 1);
        try(Statement st = connection.createStatement()) {
            st.execute("ALTER TABLE RECEIVERS ADD COLUMN HEIGHT FLOAT");
            st.execute("UPDATE RECEIVERS SET HEIGHT = ST_Z(THE_GEOM)");
        }
        long start = System.currentTimeMillis();
        IsoSurface isoSurface = new IsoSurface(Arrays.asList(0.,5.,10.,15.,20.,25.,30.,35.), 2154);
        isoSurface.setPointTable("RECEIVERS");
        isoSurface.setPointTableField("HEIGHT");
        isoSurface.setSmooth(false);
        isoSurface.setMergeTriangles(false);
        isoSurface.createTable(connection);
        System.out.println("Contouring done in " + (System.currentTimeMillis() - start) + " ms");

        assertTrue(JDBCUtilities.tableExists(connection, "CONTOURING_NOISE_MAP"));

        // Check Z values in CONTOURING_NOISE_MAP
        try(Statement st = connection.createStatement()) {
            try(ResultSet rs = st.executeQuery("SELECT MAX(ST_ZMAX(THE_GEOM)) MAXZ, MIN(ST_ZMIN(THE_GEOM)) MINZ FROM CONTOURING_NOISE_MAP")) {
                assertTrue(rs.next());
                assertEquals(33.2, rs.getDouble("MAXZ"), 0.01);
                assertEquals(-1.79, rs.getDouble("MINZ"), 0.01);
            }
        }
    }


    @Test
    public void testContouring3DMerge() throws SQLException, IOException, LayerDelaunayError {
        // Will create elevation iso from DEM table
        GeoJsonRead.importTable(connection, Paths.get(Paths.get(System.getProperty("user.dir")).getParent().toString(),
                "wps_scripts/src/test/resources/org/noise_planet/noisemodelling/wps/dem.geojson").toString());
        LayerTinfour delaunayTool = new LayerTinfour();
        try (PreparedStatement st = connection.prepareStatement(
                "SELECT the_geom FROM DEM")) {
            try (SpatialResultSet rs = st.executeQuery().unwrap(SpatialResultSet.class)) {
                while (rs.next()) {
                    Geometry pt = rs.getGeometry();
                    if(pt != null) {
                        delaunayTool.addVertex(pt.getCoordinate());
                    }
                }
            }
        }
        delaunayTool.processDelaunay();
        DelaunayReceiversMaker.generateResultTable(connection, "RECEIVERS", "TRIANGLES",
                new AtomicInteger(), delaunayTool.getVertices(), new GeometryFactory(), delaunayTool.getTriangles(),
                0, 0, 1);
        try(Statement st = connection.createStatement()) {
            st.execute("ALTER TABLE RECEIVERS ADD COLUMN HEIGHT FLOAT");
            st.execute("UPDATE RECEIVERS SET HEIGHT = ST_Z(THE_GEOM)");
        }
        long start = System.currentTimeMillis();
        IsoSurface isoSurface = new IsoSurface(Arrays.asList(0.,5.,10.,15.,20.,25.,30.,35.), 2154);
        isoSurface.setPointTable("RECEIVERS");
        isoSurface.setPointTableField("HEIGHT");
        isoSurface.setSmooth(false);
        isoSurface.createTable(connection);
        System.out.println("Contouring done in " + (System.currentTimeMillis() - start) + " ms");

        assertTrue(JDBCUtilities.tableExists(connection, "CONTOURING_NOISE_MAP"));

        // Check Z values in CONTOURING_NOISE_MAP
        try(Statement st = connection.createStatement()) {
            try(ResultSet rs = st.executeQuery("SELECT MAX(ST_ZMAX(THE_GEOM)) MAXZ, MIN(ST_ZMIN(THE_GEOM)) MINZ FROM CONTOURING_NOISE_MAP")) {
                assertTrue(rs.next());
                assertEquals(33.2, rs.getDouble("MAXZ"), 0.01);
                assertEquals(-1.37, rs.getDouble("MINZ"), 0.01);
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

    @Test
    public void testGenerateReceiversAndPeriodIsoCountours() throws SQLException {
        try(Statement st = connection.createStatement()) {
            st.execute(String.format("CALL SHPREAD('%s', 'ROADS_TRAFF')", NoiseMapByReceiverMakerTest.class.getResource("roads_traff.shp").getFile()));
            st.execute(String.format("CALL SHPREAD('%s', 'BUILDINGS')", NoiseMapByReceiverMakerTest.class.getResource("buildings.shp").getFile()));

            int srid = org.h2gis.utilities.GeometryTableUtilities.getSRID(connection, "BUILDINGS");
            IsoSurface isoSurface = new IsoSurface(IsoSurface.NF31_133_ISO, srid);
            // Generate delaunay triangulation
            DelaunayReceiversMaker delaunayReceiversMaker = new DelaunayReceiversMaker("BUILDINGS", "ROADS_TRAFF");
            delaunayReceiversMaker.setMaximumArea(800);
            delaunayReceiversMaker.setGridDim(1);
            delaunayReceiversMaker.run(connection, "RECEIVERS" , isoSurface.getTriangleTable());

            // Create noise map for 4 periods
            NoiseMapByReceiverMaker noiseMapByReceiverMaker = new NoiseMapByReceiverMaker("BUILDINGS",
                    "ROADS_TRAFF", "RECEIVERS");

            noiseMapByReceiverMaker.setMaximumPropagationDistance(100);
            noiseMapByReceiverMaker.setSoundReflectionOrder(0);
            noiseMapByReceiverMaker.setComputeHorizontalDiffraction(false);
            noiseMapByReceiverMaker.getNoiseMapDatabaseParameters().exportReceiverPosition = true;
            noiseMapByReceiverMaker.setGridDim(1);
            noiseMapByReceiverMaker.getNoiseMapDatabaseParameters().setMaximumError(3);

            noiseMapByReceiverMaker.run(connection, new RootProgressVisitor(1, true, 5));

            int receiversRowCount = JDBCUtilities.getRowCount(connection, "RECEIVERS");

            int resultRowCount = JDBCUtilities.getRowCount(connection,
                    noiseMapByReceiverMaker.getNoiseMapDatabaseParameters().receiversLevelTable);

            // D E N and DEN, should be 4 more rows than receivers
            assertEquals(receiversRowCount * 4, resultRowCount);

            LOGGER.info("Create iso surface");
            // Create contouring noise map
            isoSurface.setPointTable(noiseMapByReceiverMaker.getNoiseMapDatabaseParameters().receiversLevelTable);
            isoSurface.setPointTableField("LAEQ");
            isoSurface.setSmooth(false); // faster
            isoSurface.setMergeTriangles(false); // faster
            isoSurface.createTable(connection, "IDRECEIVER");

            List<String> columnNames = JDBCUtilities.getColumnNames(connection, isoSurface.getOutputTable());
            assertTrue(columnNames.contains("PERIOD"));

            List<String> periods = JDBCUtilities.getUniqueFieldValues(connection, isoSurface.getOutputTable(),
                    "PERIOD");
            assertTrue(periods.contains("D"));
            assertTrue(periods.contains("E"));
            assertTrue(periods.contains("N"));
            assertTrue(periods.contains(EmissionTableGenerator.DEN_PERIOD));
        }

    }
}