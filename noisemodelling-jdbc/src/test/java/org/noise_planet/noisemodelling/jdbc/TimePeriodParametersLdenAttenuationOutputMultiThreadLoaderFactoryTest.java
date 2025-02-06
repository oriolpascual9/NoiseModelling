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
import org.h2gis.functions.io.dbf.DBFRead;
import org.h2gis.functions.io.shp.SHPRead;
import org.h2gis.utilities.JDBCUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.LineString;
import org.noise_planet.noisemodelling.emission.LineSource;
import org.noise_planet.noisemodelling.emission.railway.RailWayParameters;
import org.noise_planet.noisemodelling.emission.railway.cnossos.RailwayCnossos;
import org.noise_planet.noisemodelling.jdbc.input.DefaultTableLoader;
import org.noise_planet.noisemodelling.jdbc.input.SceneDatabaseInputSettings;
import org.noise_planet.noisemodelling.jdbc.railway.RailWayLWGeom;
import org.noise_planet.noisemodelling.jdbc.railway.RailWayLWIterator;
import org.noise_planet.noisemodelling.jdbc.utils.CellIndex;
import org.noise_planet.noisemodelling.pathfinder.utils.AcousticIndicatorsFunctions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.noise_planet.noisemodelling.pathfinder.utils.AcousticIndicatorsFunctions.sumArray;
import static org.noise_planet.noisemodelling.pathfinder.utils.AcousticIndicatorsFunctions.sumDbArray;

public class TimePeriodParametersLdenAttenuationOutputMultiThreadLoaderFactoryTest {

    static Logger LOGGER = LoggerFactory.getLogger(TimePeriodParametersLdenAttenuationOutputMultiThreadLoaderFactoryTest.class);
    RailwayCnossos railway = new RailwayCnossos();


    private Connection connection;

    @BeforeEach
    public void tearUp() throws Exception {
        connection = JDBCUtilities.wrapConnection(H2GISDBFactory.createSpatialDataBase(TimePeriodParametersLdenAttenuationOutputMultiThreadLoaderFactoryTest.class.getSimpleName(), true, ""));
    }

    @AfterEach
    public void tearDown() throws Exception {
        if(connection != null) {
            connection.close();
        }
    }
//
//    @Test
//    public void testNoiseEmission() throws SQLException, IOException {
//        SHPRead.importTable(connection, TimePeriodParametersLdenAttenuationOutputMultiThreadLoaderFactoryTest.class.getResource("roads_traff.shp").getFile());
//        LdenNoiseMapParameters ldenNoiseMapParameters = new LdenNoiseMapParameters(LdenNoiseMapParameters.INPUT_MODE.INPUT_MODE_TRAFFIC_FLOW);
//        ldenNoiseMapParameters.setPropagationProcessPathData(LdenNoiseMapParameters.TIME_PERIOD.DAY, new AttenuationCnossosParameters());
//        ldenNoiseMapParameters.setPropagationProcessPathData(LdenNoiseMapParameters.TIME_PERIOD.EVENING, new AttenuationCnossosParameters());
//        ldenNoiseMapParameters.setPropagationProcessPathData(LdenNoiseMapParameters.TIME_PERIOD.NIGHT, new AttenuationCnossosParameters());
//        ldenNoiseMapParameters.setCoefficientVersion(1);
//        LdenScene process = new LdenScene(null, ldenNoiseMapParameters);
//        try(Statement st = connection.createStatement()) {
//            double lv_speed = 70;
//            int lv_per_hour = 1000;
//            double mv_speed = 70;
//            int mv_per_hour = 1000;
//            double hgv_speed = 70;
//            int hgv_per_hour = 1000;
//            double wav_speed = 70;
//            int wav_per_hour = 1000;
//            double wbv_speed = 70;
//            int wbv_per_hour = 1000;
//            double Temperature = 15;
//            String RoadSurface = "NL01";
//            double Pm_stud = 0.5;
//            double Ts_stud = 4;
//            double Junc_dist = 200;
//            int Junc_type = 1;
//            StringBuilder qry = new StringBuilder("SELECT ");
//            qry.append(lv_speed).append(" LV_SPD_D, ");
//            qry.append(lv_per_hour).append(" LV_D, ");
//            qry.append(mv_speed).append(" MV_SPD_D, ");
//            qry.append(mv_per_hour).append(" MV_D, ");
//            qry.append(hgv_speed).append(" HGV_SPD_D, ");
//            qry.append(hgv_per_hour).append(" HGV_D, ");
//            qry.append(wav_speed).append(" WAV_SPD_D, ");
//            qry.append(wav_per_hour).append(" WAV_D, ");
//            qry.append(wbv_speed).append(" WBV_SPD_D, ");
//            qry.append(wbv_per_hour).append(" WBV_D, ");
//            qry.append(Temperature).append(" TEMP, ");
//            qry.append(Pm_stud).append(" PM_STUD, ");
//            qry.append(Ts_stud).append(" TS_STUD, ");
//            qry.append(Junc_dist).append(" JUNC_DIST, '");
//            qry.append(Junc_type).append("' JUNC_TYPE, '");
//            qry.append(RoadSurface).append("' PVMT ");
//            try(ResultSet rs = st.executeQuery(qry.toString())) {
//                assertTrue(rs.next());
//                double[] leq = process.getEmissionFromResultSet(rs, "D", 10);
//                assertEquals(77.67 , leq[leq.length - 1] , 0.1);
//            }
//        }
//    }

    @Test
    public void testNoiseEmissionRailWay() throws SQLException, IOException {
        SHPRead.importTable(connection, TimePeriodParametersLdenAttenuationOutputMultiThreadLoaderFactoryTest.class.getResource("RailTrack.shp").getFile());
        DBFRead.importTable(connection, TimePeriodParametersLdenAttenuationOutputMultiThreadLoaderFactoryTest.class.getResource("RailTrain.dbf").getFile());
        int expectedNumberOfRows;
        try(ResultSet rs = connection.createStatement().executeQuery("SELECT COUNT(*) FROM RAILTRACK")) {
            assertTrue(rs.next());
            expectedNumberOfRows = rs.getInt(1);
        }
        RailWayLWIterator railWayLWIterator = new RailWayLWIterator(connection,"RAILTRACK", "RAILTRAIN","RailwayVehiclesCnossos.json","RailwayTrainsets.json", "RailwayCnossosSNCF_2021.json");

        int numberOfRows = 0;
        while (railWayLWIterator.hasNext()) {
            RailWayLWGeom v = railWayLWIterator.next();
            assertNotNull(v);
            numberOfRows++;
        }
        assertEquals(expectedNumberOfRows, numberOfRows);
    }

    @Test
    public void testNoiseEmissionRailWayTwoGeoms() throws SQLException, IOException {
        SHPRead.importTable(connection, TimePeriodParametersLdenAttenuationOutputMultiThreadLoaderFactoryTest.class.getResource("RailTrack.shp").getFile());
        DBFRead.importTable(connection, TimePeriodParametersLdenAttenuationOutputMultiThreadLoaderFactoryTest.class.getResource("RailTrain.dbf").getFile());

        // Test with two track only
        connection.createStatement().execute("DELETE FROM RAILTRACK WHERE PK NOT IN (SELECT PK FROM RAILTRACK LIMIT 2)");

        int expectedNumberOfRows;
        try(ResultSet rs = connection.createStatement().executeQuery("SELECT COUNT(*) FROM RAILTRACK")) {
            assertTrue(rs.next());
            expectedNumberOfRows = rs.getInt(1);
        }
        RailWayLWIterator railWayLWIterator = new RailWayLWIterator(connection,"RAILTRACK", "RAILTRAIN");
        int numberOfRows = 0;
        while (railWayLWIterator.hasNext()) {
            RailWayLWGeom v = railWayLWIterator.next();
            assertNotNull(v);
            numberOfRows++;
        }
        assertEquals(expectedNumberOfRows, numberOfRows);
    }
//

    @Test
    public void testNoiseEmissionRailWaySingleGeom() throws SQLException, IOException {
        SHPRead.importTable(connection, TimePeriodParametersLdenAttenuationOutputMultiThreadLoaderFactoryTest.class.getResource("RailTrack.shp").getFile());
        DBFRead.importTable(connection, TimePeriodParametersLdenAttenuationOutputMultiThreadLoaderFactoryTest.class.getResource("RailTrain.dbf").getFile());

        // Test with two track only
        connection.createStatement().execute("DELETE FROM RAILTRACK WHERE PK NOT IN (SELECT PK FROM RAILTRACK LIMIT 1)");

        int expectedNumberOfRows;
        try(ResultSet rs = connection.createStatement().executeQuery("SELECT COUNT(*) FROM RAILTRACK")) {
            assertTrue(rs.next());
            expectedNumberOfRows = rs.getInt(1);
        }
        RailWayLWIterator railWayLWIterator = new RailWayLWIterator(connection,"RAILTRACK", "RAILTRAIN");
        int numberOfRows = 0;
        while (railWayLWIterator.hasNext()) {
            RailWayLWGeom v = railWayLWIterator.next();
            assertNotNull(v);
            numberOfRows++;
        }
        assertEquals(expectedNumberOfRows, numberOfRows);
    }


    @Test
    public void testNoiseEmissionRailWaySingleGeomSingleTrain() throws SQLException, IOException {
        SHPRead.importTable(connection, TimePeriodParametersLdenAttenuationOutputMultiThreadLoaderFactoryTest.class.getResource("RailTrack.shp").getFile());
        DBFRead.importTable(connection, TimePeriodParametersLdenAttenuationOutputMultiThreadLoaderFactoryTest.class.getResource("RailTrain.dbf").getFile());

        // Test with two track only
        connection.createStatement().execute("DELETE FROM RAILTRACK WHERE PK NOT IN (SELECT PK FROM RAILTRACK LIMIT 1)");
        connection.createStatement().execute("DELETE FROM RAILTRAIN WHERE PK NOT IN (SELECT R1.PK FROM RAILTRAIN R1, RAILTRACK R2 WHERE r1.IDSECTION = R2.IDSECTION LIMIT 1)");

        int expectedNumberOfRows;
        try(ResultSet rs = connection.createStatement().executeQuery("SELECT COUNT(*) FROM RAILTRACK")) {
            assertTrue(rs.next());
            expectedNumberOfRows = rs.getInt(1);
        }
        RailWayLWIterator railWayLWIterator = new RailWayLWIterator(connection,"RAILTRACK", "RAILTRAIN");
        int numberOfRows = 0;
        while (railWayLWIterator.hasNext()) {
            RailWayLWGeom v = railWayLWIterator.next();
            assertNotNull(v);
            numberOfRows++;
        }
        assertEquals(expectedNumberOfRows, numberOfRows);
    }

    @Test
    public void testNoiseEmissionRailWay_OC5() throws SQLException, IOException {
        SHPRead.importTable(connection, TimePeriodParametersLdenAttenuationOutputMultiThreadLoaderFactoryTest.class.getResource("Test/OC/RailTrack.shp").getFile());
        DBFRead.importTable(connection, TimePeriodParametersLdenAttenuationOutputMultiThreadLoaderFactoryTest.class.getResource("Test/OC/RailTrain.dbf").getFile());

        RailWayLWIterator railWayLWIterator = new RailWayLWIterator(connection,"RAILTRACK", "RAILTRAIN");
        RailWayLWGeom v = railWayLWIterator.next();
        assertNotNull(v);
        v.setNbTrack(2);
        RailWayParameters railWayLW = v.getRailWayLW();
        List<LineString> geometries = v.getRailWayLWGeometry();

        v = railWayLWIterator.next();
        assertFalse(railWayLWIterator.hasNext());

    }

    @Test
    public void testNoiseEmissionRailWay_BM() throws SQLException, IOException {
        double[] dBA = new double[]{-30,-26.2,-22.5,-19.1,-16.1,-13.4,-10.9,-8.6,-6.6,-4.8,-3.2,-1.9,-0.8,0,0.6,1,1.2,1.3,1.2,1,0.5,-0.1,-1.1,-2.5};

        SHPRead.importTable(connection, TimePeriodParametersLdenAttenuationOutputMultiThreadLoaderFactoryTest.class.getResource("Test/BM/RailTrack.shp").getFile());
        DBFRead.importTable(connection, TimePeriodParametersLdenAttenuationOutputMultiThreadLoaderFactoryTest.class.getResource("Test/BM/RailTrain.dbf").getFile());

        HashMap<String, double[]> Resultats = new HashMap<>();

        RailWayLWIterator railWayLWIterator = new RailWayLWIterator(connection,"RAILTRACK", "RAILTRAIN","RailwayVehiclesCnossos.json","RailwayTrainsets.json", "RailwayCnossosSNCF_2021.json");
        double resD,resE,resN;

        while (railWayLWIterator.hasNext()) {
            RailWayLWGeom v = railWayLWIterator.next();

            RailWayParameters railWayLW = v.getRailWayLWDay();
            double[] lW = new double[24];
            Arrays.fill(lW, -99.00);

            if (!railWayLW.getRailwaySourceList().isEmpty()){
                for (Map.Entry<String, LineSource> railwaySourceEntry : railWayLW.getRailwaySourceList().entrySet()) {
                    double[]  lW1 = railwaySourceEntry.getValue().getlW();
                    lW = AcousticIndicatorsFunctions.sumDbArray(lW, lW1);
                }
            }

            double[] LWA = sumArray(lW, dBA);
            resD = sumDbArray(LWA);

            railWayLW = v.getRailWayLWEvening();
            Arrays.fill(lW, -99.00);
            if (!railWayLW.getRailwaySourceList().isEmpty()){
                for (Map.Entry<String, LineSource> railwaySourceEntry : railWayLW.getRailwaySourceList().entrySet()) {
                    double[]  lW1 = railwaySourceEntry.getValue().getlW();
                    lW = AcousticIndicatorsFunctions.sumDbArray(lW, lW1);
                }
            }
            LWA = sumArray(lW, dBA);
            resE = sumDbArray(LWA);

            railWayLW = v.getRailWayLWNight();
            Arrays.fill(lW, -99.00);
            if (!railWayLW.getRailwaySourceList().isEmpty()){
                for (Map.Entry<String, LineSource> railwaySourceEntry : railWayLW.getRailwaySourceList().entrySet()) {
                    double[]  lW1 = railwaySourceEntry.getValue().getlW();
                    lW = AcousticIndicatorsFunctions.sumDbArray(lW, lW1);
                }
            }
            LWA = sumArray(lW, dBA);
            resN = sumDbArray(LWA);

            String idSection = v.getIdSection();

            Resultats.put(idSection,new double[]{resD, resE, resN});

        }
    }

    @Test
    public void testNoiseEmissionRailWay_Section556() throws SQLException, IOException {
        double[] dBA = new double[]{-30,-26.2,-22.5,-19.1,-16.1,-13.4,-10.9,-8.6,-6.6,-4.8,-3.2,-1.9,-0.8,0,0.6,1,1.2,1.3,1.2,1,0.5,-0.1,-1.1,-2.5};

        SHPRead.importTable(connection, TimePeriodParametersLdenAttenuationOutputMultiThreadLoaderFactoryTest.class.getResource("Test/556/RAIL_SECTIONS.shp").getFile());
        DBFRead.importTable(connection, TimePeriodParametersLdenAttenuationOutputMultiThreadLoaderFactoryTest.class.getResource("Test/556/RAIL_TRAFIC.dbf").getFile());

        HashMap<String, double[]> Resultats = new HashMap<>();

        RailWayLWIterator railWayLWIterator = new RailWayLWIterator(connection,"RAIL_SECTIONS", "RAIL_TRAFIC");

        double resD,resE,resN;

       // RailWayLWIterator.RailWayLWGeom v = railWayLWIterator.current();

        while (railWayLWIterator.hasNext()) {
            RailWayLWGeom v = railWayLWIterator.next();

            RailWayParameters railWayLW = v.getRailWayLWDay();

            double[] lW = new double[24];
            Arrays.fill(lW, -99.00);

            if (!railWayLW.getRailwaySourceList().isEmpty()){
                for (Map.Entry<String, LineSource> railwaySourceEntry : railWayLW.getRailwaySourceList().entrySet()) {
                    double[]  lW1 = railwaySourceEntry.getValue().getlW();
                    lW = AcousticIndicatorsFunctions.sumDbArray(lW, lW1);
                }
            }

            double[] LWA = sumArray(lW, dBA);
            resD = sumDbArray(LWA);

            railWayLW = v.getRailWayLWEvening();
            Arrays.fill(lW, -99.00);
            if (!railWayLW.getRailwaySourceList().isEmpty()){
                for (Map.Entry<String, LineSource> railwaySourceEntry : railWayLW.getRailwaySourceList().entrySet()) {
                    double[]  lW1 = railwaySourceEntry.getValue().getlW();
                    lW = AcousticIndicatorsFunctions.sumDbArray(lW, lW1);
                }
            }
            LWA = sumArray(lW, dBA);
            resE = sumDbArray(LWA);

            railWayLW = v.getRailWayLWNight();
            Arrays.fill(lW, -99.00);
            if (!railWayLW.getRailwaySourceList().isEmpty()){
                for (Map.Entry<String, LineSource> railwaySourceEntry : railWayLW.getRailwaySourceList().entrySet()) {
                    double[]  lW1 = railwaySourceEntry.getValue().getlW();
                    lW = AcousticIndicatorsFunctions.sumDbArray(lW, lW1);
                }
            }
            LWA = sumArray(lW, dBA);
            resN = sumDbArray(LWA);

            String idSection = v.getIdSection();

            Resultats.put(idSection,new double[]{resD, resE, resN});

        }
    }


    @Test
    public void testNoiseEmissionRailWayForPropa() throws SQLException, IOException {
        SHPRead.importTable(connection, TimePeriodParametersLdenAttenuationOutputMultiThreadLoaderFactoryTest.class.getResource("PropaRail/Rail_Section2.shp").getFile());
        DBFRead.importTable(connection, TimePeriodParametersLdenAttenuationOutputMultiThreadLoaderFactoryTest.class.getResource("PropaRail/Rail_Traffic.dbf").getFile());

        EmissionTableGenerator.makeTrainLWTable(connection, "Rail_Section2", "Rail_Traffic",
                "LW_RAILWAY");

        // Get Class to compute LW
        RailWayLWIterator railWayLWIterator = new RailWayLWIterator(connection,"Rail_Section2", "Rail_Traffic");
        RailWayLWGeom v = railWayLWIterator.next();
        assertNotNull(v);
        List<LineString> geometries = v.getRailWayLWGeometry();
        assertEquals(geometries.size(),2);

        SHPRead.importTable(connection, TimePeriodParametersLdenAttenuationOutputMultiThreadLoaderFactoryTest.class.getResource("PropaRail/Recepteurs.shp").getFile());
        SHPRead.importTable(connection, TimePeriodParametersLdenAttenuationOutputMultiThreadLoaderFactoryTest.class.getResource("PropaRail/Buildings.shp").getFile());
        SHPRead.importTable(connection, TimePeriodParametersLdenAttenuationOutputMultiThreadLoaderFactoryTest.class.getResource("PropaRail/Rail_protect.shp").getFile());

        // ICI POUR CHANGER HAUTEUR ET G ECRAN
        connection.createStatement().execute("CREATE TABLE SCREENS AS SELECT ST_BUFFER(the_geom, 0.5, 'join=mitre endcap=flat') as the_geom, pk as pk, 3.0 as height, g as g FROM Rail_protect");

        // ICI HAUTEUR RECPTEUR
        connection.createStatement().execute("SELECT UpdateGeometrySRID('RECEPTEURS', 'THE_GEOM', 2154);");
        connection.createStatement().execute("SELECT UpdateGeometrySRID('LW_RAILWAY', 'THE_GEOM', 2154);");

        connection.createStatement().execute("UPDATE RECEPTEURS SET THE_GEOM = ST_UPDATEZ(THE_GEOM,4.0);");
        //connection.createStatement().execute("UPDATE LW_RAILWAY SET THE_GEOM = ST_SETSRID(ST_UPDATEZ(THE_GEOM,0.5),2154);");


        NoiseMapByReceiverMaker noiseMapByReceiverMaker = new NoiseMapByReceiverMaker("SCREENS", "LW_RAILWAY",
                "RECEPTEURS");

        noiseMapByReceiverMaker.setInputMode(SceneDatabaseInputSettings.INPUT_MODE.INPUT_MODE_LW_DEN);

        // Use train directivity functions instead of discrete directivity
        DefaultTableLoader defaultTableLoader = ((DefaultTableLoader) noiseMapByReceiverMaker.getPropagationProcessDataFactory());
        defaultTableLoader.insertTrainDirectivity();

        noiseMapByReceiverMaker.run(connection, new EmptyProgressVisitor());

        try(ResultSet rs = connection.createStatement().executeQuery("SELECT LAEQ FROM "
                + noiseMapByReceiverMaker.getNoiseMapDatabaseParameters().receiversLevelTable +
                "  WHERE PERIOD='D' ORDER BY IDRECEIVER")) {
            assertTrue(rs.next());
            assertEquals(41.53, rs.getDouble("LAEQ"), 2.0);
            assertTrue(rs.next());
            assertEquals(43.28, rs.getDouble("LAEQ"), 2.0);
            assertTrue(rs.next());
            assertEquals(46.39, rs.getDouble("LAEQ"), 2.0);
            assertTrue(rs.next());
            assertEquals(45.88, rs.getDouble("LAEQ"), 2.0);
            assertTrue(rs.next());
            assertEquals(42.94, rs.getDouble("LAEQ"), 2.0);
            assertTrue(rs.next());
            assertEquals(40.82, rs.getDouble("LAEQ"), 2.0);
            assertTrue(rs.next());
            assertEquals(38.90, rs.getDouble("LAEQ"), 2.0);
            assertTrue(rs.next());
            assertEquals(49.31, rs.getDouble("LAEQ"), 2.0);
            assertTrue(rs.next());
            assertEquals(49.2, rs.getDouble("LAEQ"), 2.0);
            assertTrue(rs.next());
            assertEquals(44.23, rs.getDouble("LAEQ"), 2.0);
            assertTrue(rs.next());
            assertEquals(42.36, rs.getDouble("LAEQ"), 2.0);
        }

    }


//    @Test
//    public void testReadFrequencies() throws SQLException, IOException {
//        SHPRead.importTable(connection, TimePeriodParametersLdenAttenuationOutputMultiThreadLoaderFactoryTest.class.getResource("lw_roads.shp").getFile());
//        SHPRead.importTable(connection, TimePeriodParametersLdenAttenuationOutputMultiThreadLoaderFactoryTest.class.getResource("buildings.shp").getFile());
//        SHPRead.importTable(connection, TimePeriodParametersLdenAttenuationOutputMultiThreadLoaderFactoryTest.class.getResource("receivers.shp").getFile());
//
//        LdenNoiseMapParameters ldenNoiseMapParameters = new LdenNoiseMapParameters(LdenNoiseMapParameters.INPUT_MODE.INPUT_MODE_LW_DEN);
//
//        NoiseMapMaker factory = new NoiseMapMaker(connection, ldenNoiseMapParameters);
//
//        LdenNoiseMapLoader ldenNoiseMapLoader = new LdenNoiseMapLoader("BUILDINGS", "LW_ROADS",
//                "RECEIVERS");
//
//        ldenNoiseMapLoader.setComputeRaysOutFactory(factory);
//        ldenNoiseMapLoader.setPropagationProcessDataFactory(factory);
//
//        ldenNoiseMapLoader.initialize(connection, new EmptyProgressVisitor());
//
//        assertNotNull(ldenNoiseMapParameters.attenuationCnossosParametersDay);
//        assertNotNull(ldenNoiseMapParameters.attenuationCnossosParametersEvening);
//        assertNotNull(ldenNoiseMapParameters.attenuationCnossosParametersNight);
//
//        assertEquals(8, ldenNoiseMapParameters.attenuationCnossosParametersDay.freq_lvl.size());
//
//        try(Statement st = connection.createStatement()) {
//            // drop all columns except 1000 Hz
//            st.execute("ALTER TABLE lw_roads drop column LWD63");
//            st.execute("ALTER TABLE lw_roads drop column LWD125");
//            st.execute("ALTER TABLE lw_roads drop column LWD250");
//            st.execute("ALTER TABLE lw_roads drop column LWD500");
//            st.execute("ALTER TABLE lw_roads drop column LWD2000");
//            st.execute("ALTER TABLE lw_roads drop column LWD4000");
//            st.execute("ALTER TABLE lw_roads drop column LWD8000");
//            st.execute("ALTER TABLE lw_roads drop column LWE63");
//            st.execute("ALTER TABLE lw_roads drop column LWE125");
//            st.execute("ALTER TABLE lw_roads drop column LWE250");
//            st.execute("ALTER TABLE lw_roads drop column LWE500");
//            st.execute("ALTER TABLE lw_roads drop column LWE1000");
//            st.execute("ALTER TABLE lw_roads drop column LWE2000");
//            st.execute("ALTER TABLE lw_roads drop column LWE4000");
//            st.execute("ALTER TABLE lw_roads drop column LWE8000");
//            st.execute("ALTER TABLE lw_roads drop column LWN63");
//            st.execute("ALTER TABLE lw_roads drop column LWN125");
//            st.execute("ALTER TABLE lw_roads drop column LWN250");
//            st.execute("ALTER TABLE lw_roads drop column LWN500");
//            st.execute("ALTER TABLE lw_roads drop column LWN1000");
//            st.execute("ALTER TABLE lw_roads drop column LWN2000");
//            st.execute("ALTER TABLE lw_roads drop column LWN4000");
//            st.execute("ALTER TABLE lw_roads drop column LWN8000");
//        }
//
//        ldenNoiseMapLoader.initialize(connection, new EmptyProgressVisitor());
//
//        assertEquals(1, ldenNoiseMapParameters.attenuationCnossosParametersDay.freq_lvl.size());
//
//        assertEquals(1000, (int) ldenNoiseMapParameters.attenuationCnossosParametersDay.freq_lvl.get(0));
//    }
//


    // Check regression of finding cell i,j that contains receivers
    @Test
    public void testRegression1() throws SQLException, IOException {
        SHPRead.importTable(connection, TimePeriodParametersLdenAttenuationOutputMultiThreadLoaderFactoryTest.class.getResource("regression1/lw_roads_fence.shp").getFile());
        SHPRead.importTable(connection, TimePeriodParametersLdenAttenuationOutputMultiThreadLoaderFactoryTest.class.getResource("regression1/bati_fence.shp").getFile());
        SHPRead.importTable(connection, TimePeriodParametersLdenAttenuationOutputMultiThreadLoaderFactoryTest.class.getResource("regression1/receivers.shp").getFile());

        // Count receivers
        int nbReceivers = 0;
        try(ResultSet rs = connection.createStatement().executeQuery("SELECT COUNT(*) CPT FROM RECEIVERS")) {
            assertTrue(rs.next());
            nbReceivers = rs.getInt(1);
        }

        NoiseMapByReceiverMaker noiseMapByReceiverMaker = new NoiseMapByReceiverMaker("BATI_FENCE",
                "LW_ROADS_FENCE",
                "RECEIVERS");

        noiseMapByReceiverMaker.initialize(connection, new EmptyProgressVisitor());

        Map<CellIndex, Integer> populatedCells = noiseMapByReceiverMaker.searchPopulatedCells(connection);

        // Check if all receivers are found
        assertEquals(nbReceivers, populatedCells.values().stream().reduce(Integer::sum).orElse(0));
    }

}