/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */

package org.noise_planet.noisemodelling.jdbc;

import org.h2gis.api.ProgressVisitor;
import org.h2gis.utilities.*;
import org.h2gis.utilities.dbtypes.DBTypes;
import org.h2gis.utilities.dbtypes.DBUtils;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.index.strtree.STRtree;
import org.locationtech.jts.io.WKTWriter;
import org.noise_planet.noisemodelling.jdbc.input.SceneWithEmission;
import org.noise_planet.noisemodelling.jdbc.output.DefaultCutPlaneProcessing;
import org.noise_planet.noisemodelling.jdbc.utils.CellIndex;
import org.noise_planet.noisemodelling.pathfinder.PathFinder;
import org.noise_planet.noisemodelling.pathfinder.IComputePathsOut;
import org.noise_planet.noisemodelling.pathfinder.utils.profiler.ProfilerThread;
import org.noise_planet.noisemodelling.propagation.AttenuationComputeOutput;
import org.noise_planet.noisemodelling.propagation.SceneWithAttenuation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * Compute noise propagation at specified receiver points.
 * @author Nicolas Fortin
 */
public class NoiseMapByReceiverMaker extends NoiseMapLoader {
    private final String receiverTableName;
    private PropagationProcessDataFactory propagationProcessDataFactory;
    private IComputeRaysOutFactory computeRaysOutFactory = new DefaultCutPlaneProcessing();
    private Logger logger = LoggerFactory.getLogger(NoiseMapByReceiverMaker.class);
    private int threadCount = 0;
    private ProfilerThread profilerThread;

    public NoiseMapByReceiverMaker(String buildingsTableName, String sourcesTableName, String receiverTableName) {
        super(buildingsTableName, sourcesTableName);
        this.receiverTableName = receiverTableName;
    }

    /**
     * Computation stacks and timing are collected by this class in order
     * to profile the execution of the simulation
     * @return Instance of ProfilerThread or null
     */
    public ProfilerThread getProfilerThread() {
        return profilerThread;
    }

    /**
     * @return Receiver table name
     */
    public String getReceiverTableName() {
        return receiverTableName;
    }

    /**
     * Computation stacks and timing are collected by this class in order
     * to profile the execution of the simulation
     * @param profilerThread Instance of ProfilerThread
     */
    public void setProfilerThread(ProfilerThread profilerThread) {
        this.profilerThread = profilerThread;
    }

    public void setComputeRaysOutFactory(IComputeRaysOutFactory computeRaysOutFactory) {
        this.computeRaysOutFactory = computeRaysOutFactory;
    }

    public void setPropagationProcessDataFactory(PropagationProcessDataFactory propagationProcessDataFactory) {
        this.propagationProcessDataFactory = propagationProcessDataFactory;
    }

    public int getThreadCount() {
        return threadCount;
    }

    public void setThreadCount(int threadCount) {
        this.threadCount = threadCount;
    }

    /**
     * Initialisation of data structures needed for sound propagation.
     * @param connection JDBC Connection
     * @param cellIndex Computation area index
     * @param progression Progression info
     * @return Data input for cell evaluation
     * @throws SQLException
     */
    public SceneWithEmission prepareCell(Connection connection, CellIndex cellIndex,
                                            ProgressVisitor progression, Set<Long> skipReceivers) throws SQLException, IOException {
        DBTypes dbType = DBUtils.getDBType(connection.unwrap(Connection.class));

        Envelope cellEnvelope = getCellEnv(cellIndex);

        if(verbose) {
            int ij = cellIndex.getLatitudeIndex() * gridDim + cellIndex.getLongitudeIndex() + 1;
            WKTWriter roundWKTWriter = new WKTWriter();
            roundWKTWriter.setPrecisionModel(new PrecisionModel(1.0));
            logger.info("Begin processing of cell {}/{} Compute domain is:\n {}", ij, gridDim * gridDim,
                    roundWKTWriter.write(geometryFactory.toGeometry(cellEnvelope)));
        }
        Envelope expandedCellEnvelop = new Envelope(cellEnvelope);
        expandedCellEnvelop.expandBy(maximumPropagationDistance + 2 * maximumReflectionDistance);

        SceneWithEmission scene;
        if(propagationProcessDataFactory != null) {
            scene = propagationProcessDataFactory.create(connection, cellIndex, expandedCellEnvelop);
        } else {
            scene = new SceneWithEmission();
        }

        // //////////////////////////////////////////////////////
        // feed freeFieldFinder for fast intersection query
        // optimization
        // Fetch buildings in extendedEnvelope
        fetchCellBuildings(connection, expandedCellEnvelop, scene.profileBuilder);
        //if we have topographic points data
        fetchCellDem(connection, expandedCellEnvelop, scene.profileBuilder);

        // Fetch soil areas
        fetchCellSoilAreas(connection, expandedCellEnvelop, scene.profileBuilder);

        scene.profileBuilder.finishFeeding();

        expandedCellEnvelop = new Envelope(cellEnvelope);
        expandedCellEnvelop.expandBy(maximumPropagationDistance);


        scene.reflexionOrder = soundReflectionOrder;
        scene.setBodyBarrier(bodyBarrier);
        scene.maxRefDist = maximumReflectionDistance;
        scene.maxSrcDist = maximumPropagationDistance;
        scene.setComputeVerticalDiffraction(computeVerticalDiffraction);
        scene.setComputeHorizontalDiffraction(computeHorizontalDiffraction);

        // Fetch all source located in expandedCellEnvelop
        fetchCellSource(connection, expandedCellEnvelop, scene, true);

        // Fetch receivers

        String receiverGeomName = GeometryTableUtilities.getGeometryColumnNames(connection,
                TableLocation.parse(receiverTableName)).get(0);
        int intPk = JDBCUtilities.getIntegerPrimaryKey(connection.unwrap(Connection.class), TableLocation.parse(receiverTableName, dbType));
        String pkSelect = "";
        if(intPk >= 1) {
            pkSelect = ", " + TableLocation.quoteIdentifier(JDBCUtilities.getColumnName(connection, receiverTableName, intPk), dbType);
        } else {
            throw new SQLException(String.format("Table %s missing primary key for receiver identification", receiverTableName));
        }
        try (PreparedStatement st = connection.prepareStatement(
                "SELECT " + TableLocation.quoteIdentifier(receiverGeomName, dbType ) + pkSelect + " FROM " +
                        receiverTableName + " WHERE " +
                        TableLocation.quoteIdentifier(receiverGeomName, dbType) + " && ?::geometry")) {
            st.setObject(1, geometryFactory.toGeometry(cellEnvelope));
            try (SpatialResultSet rs = st.executeQuery().unwrap(SpatialResultSet.class)) {
                while (rs.next()) {
                    long receiverPk = rs.getLong(2);
                    if(skipReceivers.contains(receiverPk)) {
                        continue;
                    } else {
                        skipReceivers.add(receiverPk);
                    }
                    Geometry pt = rs.getGeometry();
                    if(pt != null && !pt.isEmpty()) {
                        // check z value
                        if(pt.getCoordinate().getZ() == Coordinate.NULL_ORDINATE) {
                            throw new IllegalArgumentException("The table " + receiverTableName +
                                    " contain at least one receiver without Z ordinate." +
                                    " You must specify X,Y,Z for each receiver");
                        }
                        scene.addReceiver(receiverPk, pt.getCoordinate(), rs);
                    }
                }
            }
        }
        if(progression != null) {
            scene.cellProg = progression.subProcess(scene.receivers.size());
        }
        return scene;
    }

    /**
     * Retrieves the computation envelope based on data stored in the database tables.
     * @param connection the database connection.
     * @return the computation envelope containing the bounding box of the data stored in the specified tables.
     * @throws SQLException
     */
    @Override
    protected Envelope getComputationEnvelope(Connection connection) throws SQLException {
        DBTypes dbTypes = DBUtils.getDBType(connection);
        Envelope envelopeInternal = GeometryTableUtilities.getEnvelope(connection, TableLocation.parse(receiverTableName, dbTypes)).getEnvelopeInternal();
        envelopeInternal.expandBy(maximumPropagationDistance);
        return envelopeInternal;
    }

    /**
     * Fetch all receivers and compute cells that contains receivers
     * @param connection
     * @return Cell index with number of receivers
     * @throws SQLException
     */
    public Map<CellIndex, Integer> searchPopulatedCells(Connection connection) throws SQLException {
        if(mainEnvelope == null) {
            throw new IllegalStateException("Call initialize before calling searchPopulatedCells");
        }
        Map<CellIndex, Integer> cellIndices = new HashMap<>();
        List<String> geometryFields = GeometryTableUtilities.getGeometryColumnNames(connection, TableLocation.parse(receiverTableName));
        String geometryField;
        if(geometryFields.isEmpty()) {
            throw new SQLException("The table "+receiverTableName+" does not contain a Geometry field, then the extent " +
                    "cannot be computed");
        }
        logger.info("Collect all receivers in order to localize populated cells");
        geometryField = geometryFields.get(0);
        ResultSet rs = connection.createStatement().executeQuery("SELECT " + geometryField + " FROM " + receiverTableName);
        // Construct RTree with cells envelopes
        STRtree rtree = new STRtree();
        for(int i = 0; i < gridDim; i++) {
            for(int j = 0; j < gridDim; j++) {
                Envelope refEnv = getCellEnv(mainEnvelope, i,
                        j, getCellWidth(), getCellHeight());
                rtree.insert(refEnv, new CellIndex(j, i));
            }
        }
        // Iterate over receivers and look for intersecting cells
        try (SpatialResultSet srs = rs.unwrap(SpatialResultSet.class)) {
            while (srs.next()) {
                Geometry pt = srs.getGeometry();
                if(pt != null && !pt.isEmpty()) {
                    Coordinate ptCoord = pt.getCoordinate();
                    List queryResult = rtree.query(new Envelope(ptCoord));
                    for(Object o : queryResult) {
                        if(o instanceof CellIndex) {
                            cellIndices.merge((CellIndex) o, 1, Integer::sum);
                        }
                    }
                }
            }
        }
        return cellIndices;
    }

    /**
     * Launch sound propagation
     * @param connection
     * @param cellI
     * @param cellJ
     * @param progression
     * @return
     * @throws SQLException
     */
    public IComputePathsOut evaluateCell(Connection connection, int cellI, int cellJ,
                                         ProgressVisitor progression, Set<Long> skipReceivers) throws SQLException, IOException {
        SceneWithAttenuation threadData = prepareCell(connection, cellI, cellJ, progression, skipReceivers);

        if(verbose) {
            logger.info(String.format("This computation area contains %d receivers %d sound sources and %d buildings",
                    threadData.receivers.size(), threadData.sourceGeometries.size(),
                    threadData.profileBuilder.getBuildingCount()));
        }
        IComputePathsOut computeRaysOut;
        if(computeRaysOutFactory == null) {
            computeRaysOut = new AttenuationComputeOutput(false, attenuationCnossosParametersDay, threadData);
        } else {
            computeRaysOut = computeRaysOutFactory.create(threadData, attenuationCnossosParametersDay,
                    attenuationCnossosParametersEvening, attenuationCnossosParametersNight);
        }

        PathFinder computeRays = new PathFinder(threadData);

        if(profilerThread != null) {
            computeRays.setProfilerThread(profilerThread);
        }

        if(threadCount > 0) {
            computeRays.setThreadCount(threadCount);
        }

        if(!receiverHasAbsoluteZCoordinates) {
            computeRays.makeReceiverRelativeZToAbsolute();
        }

        if(!sourceHasAbsoluteZCoordinates) {
            computeRays.makeSourceRelativeZToAbsolute();
        }

        computeRays.run(computeRaysOut);

        return computeRaysOut;
    }

    /**
     * Initializes the noise map computation process.
     * @param connection Active connection
     * @param progression
     * @throws SQLException
     */
    @Override
    public void initialize(Connection connection, ProgressVisitor progression) throws SQLException {
        super.initialize(connection, progression);
        if(propagationProcessDataFactory != null) {
            propagationProcessDataFactory.initialize(connection, this);
        }
    }

    /**
     * A factory interface for initializing input propagation process data for noise map computation.
     */
    public interface PropagationProcessDataFactory {

        /**
         * Called only once when the settings are set.
         * @param connection             the database connection to be used for initialization.
         * @param noiseMapByReceiverMaker the noise map by receiver maker object associated with the computation process.
         * @throws SQLException if an SQL exception occurs while initializing the propagation process data factory.
         */
        void initialize(Connection connection, NoiseMapByReceiverMaker noiseMapByReceiverMaker) throws SQLException;

        /**
         * Called on each sub-domain in order to create cell input data.
         *
         * @param connection          Active connection
         * @param cellIndex Active cell covering the computation
         * @param expandedCellEnvelop Envelope expended envelope where to fetch the input data
         * @return Scene to feed the data
         */
        SceneWithEmission create(Connection connection, CellIndex cellIndex, Envelope expandedCellEnvelop);
    }

    /**
     * A factory interface for creating objects that compute rays out for noise map computation.
     */
    public interface IComputeRaysOutFactory {

        /**
         * Creates an object that computes paths out for noise map computation.
         * @param cellData the scene data for the current computation cell
         * @return an object that computes paths out for noise map computation.
         */
        IComputePathsOut create(SceneWithEmission cellData);
    }


}
