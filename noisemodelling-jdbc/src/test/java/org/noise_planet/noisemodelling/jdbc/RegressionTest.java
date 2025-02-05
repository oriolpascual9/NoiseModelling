package org.noise_planet.noisemodelling.jdbc;

import org.h2.value.ValueBoolean;
import org.h2gis.api.EmptyProgressVisitor;
import org.h2gis.api.ProgressVisitor;
import org.h2gis.functions.factory.H2GISDBFactory;
import org.h2gis.functions.io.asc.AscRead;
import org.h2gis.functions.io.fgb.FGBRead;
import org.h2gis.functions.io.fgb.fileTable.FGBDriver;
import org.h2gis.functions.io.geojson.GeoJsonRead;
import org.h2gis.functions.io.shp.SHPRead;
import org.h2gis.utilities.JDBCUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.noise_planet.noisemodelling.jdbc.input.SceneWithEmission;
import org.noise_planet.noisemodelling.jdbc.output.AttenuationOutputMultiThread;
import org.noise_planet.noisemodelling.jdbc.utils.CellIndex;
import org.noise_planet.noisemodelling.pathfinder.utils.AcousticIndicatorsFunctions;
import org.noise_planet.noisemodelling.pathfinder.utils.profiler.RootProgressVisitor;

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.noise_planet.noisemodelling.jdbc.Utils.getRunScriptRes;

public class RegressionTest {

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
     * Got reflection index out of bound exception in this scenario in the past (source->reflection->h diffraction->receiver)
     */
    @Test
    public void testScenarioOutOfBoundException() throws Exception {
        try(Statement st = connection.createStatement()) {
            st.execute(getRunScriptRes("regression_nan/lw_roads.sql"));


            NoiseMapByReceiverMaker noiseMapByReceiverMaker = new NoiseMapByReceiverMaker("BUILDINGS",
                    "LW_ROADS", "RECEIVERS");

            noiseMapByReceiverMaker.sceneDatabaseInputSettings.setInputMode(SceneWithEmission.SceneDatabaseInputSettings.INPUT_MODE.INPUT_MODE_LW_DEN);

            noiseMapByReceiverMaker.setMaximumPropagationDistance(500.0);
            noiseMapByReceiverMaker.setSoundReflectionOrder(1);
            noiseMapByReceiverMaker.setThreadCount(1);
            noiseMapByReceiverMaker.setComputeHorizontalDiffraction(true);
            noiseMapByReceiverMaker.setComputeVerticalDiffraction(true);


            noiseMapByReceiverMaker.run(connection, new EmptyProgressVisitor());

            NoiseMapDatabaseParameters parameters = noiseMapByReceiverMaker.getNoiseMapDatabaseParameters();

            try(ResultSet rs = st.executeQuery("SELECT LEQ FROM " + parameters.receiversLevelTable + " WHERE PERIOD='DEN'")) {
                assertTrue(rs.next());
                assertEquals(36.77, rs.getDouble(1), 0.1);
                assertFalse(rs.next());
            }
        }
    }


}
