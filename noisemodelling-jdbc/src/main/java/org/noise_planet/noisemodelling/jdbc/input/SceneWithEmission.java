package org.noise_planet.noisemodelling.jdbc.input;

import org.h2gis.utilities.SpatialResultSet;
import org.locationtech.jts.geom.Geometry;
import org.noise_planet.noisemodelling.jdbc.EmissionTableGenerator;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.ProfileBuilder;
import org.noise_planet.noisemodelling.pathfinder.utils.AcousticIndicatorsFunctions;
import org.noise_planet.noisemodelling.propagation.SceneWithAttenuation;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * Add emission information for each source in the computation scene
 * This is input data, not thread safe, never update anything here during propagation
 */
public class SceneWithEmission extends SceneWithAttenuation {
    /** Old style DEN columns traffic period  */
    Map<String, Integer> sourceFieldsCache = new HashMap<>();
    Map<String, Integer> sourceEmissionFieldsCache = new HashMap<>();

    /**
     * Set of unique power spectrum. Key is the hash of the spectrum. Power spectrum in energetic e = pow(10, dbVal / 10.0)
     */
    public Map<Integer, double[]> powerSpectrum = new HashMap<>();

    //  For each source primary key give the map between period and source power spectrum hash value
    public Map<Long, ArrayList<PeriodEmission>> wjSources = new HashMap<>();

    public SceneDatabaseInputSettings sceneDatabaseInputSettings = new SceneDatabaseInputSettings();

    public SceneWithEmission(ProfileBuilder profileBuilder, SceneDatabaseInputSettings sceneDatabaseInputSettings) {
        super(profileBuilder);
        this.sceneDatabaseInputSettings = sceneDatabaseInputSettings;
    }

    public SceneWithEmission(ProfileBuilder profileBuilder) {
        super(profileBuilder);
    }

    public SceneWithEmission() {
    }

    int updatePowerSpectrumSet(double[] wj) {
        int hashCode = Arrays.hashCode(wj);
        powerSpectrum.putIfAbsent(hashCode, wj);
        return hashCode;
    }

    public void processTrafficFlowDEN(Long pk, SpatialResultSet rs) throws SQLException {
        // Source table PK, GEOM, LV_D, LV_E, LV_N ...
        double[][] lw = EmissionTableGenerator.computeLw(rs, sceneDatabaseInputSettings.coefficientVersion, sourceFieldsCache);
        for (EmissionTableGenerator.STANDARD_PERIOD period : EmissionTableGenerator.STANDARD_PERIOD.values()) {
            addSourceEmission(pk, EmissionTableGenerator.STANDARD_PERIOD_VALUE[period.ordinal()], lw[period.ordinal()]);
        }
    }

    /**
     * @param pk Source primary key
     * @param rs Emission source table IDSOURCE, PERIOD, LV, HV ..
     * @throws SQLException
     */
    public void processTrafficFlow(Long pk, ResultSet rs) throws SQLException {
        String period = rs.getString("PERIOD");
        // Use geometry as default slope (if field slope is not provided
        double defaultSlope = 0;
        if(!sourceEmissionFieldsCache.containsKey("SLOPE")) {
            int sourceIndex = sourcesPk.indexOf(pk);
            if(sourceIndex >= 0) {
                defaultSlope = EmissionTableGenerator.getSlope(sourceGeometries.get(sourceIndex));
            }
        }
        double[] lw = AcousticIndicatorsFunctions.dBToW(
                EmissionTableGenerator.getEmissionFromTrafficTable(rs, "",
                        defaultSlope,
                        sceneDatabaseInputSettings.coefficientVersion, sourceEmissionFieldsCache));
        addSourceEmission(pk, period, lw);
    }

    /**
     * @param pk Source primary key
     * @param rs Emission source table IDSOURCE, PERIOD, LV, HV ..
     * @throws SQLException
     */
    public void processEmission(Long pk, ResultSet rs) throws SQLException {
        double[] lw = new double[profileBuilder.frequencyArray.size()];
        List<Integer> frequencyArray = profileBuilder.frequencyArray;
        for (int i = 0, frequencyArraySize = frequencyArray.size(); i < frequencyArraySize; i++) {
            Integer frequency = frequencyArray.get(i);
            lw[i] = AcousticIndicatorsFunctions.dBToW(rs.getDouble(sceneDatabaseInputSettings.lwFrequencyPrepend+frequency));
        }
        String period = rs.getString("PERIOD");
        addSourceEmission(pk, period, lw);
    }

    @Override
    public void addSource(Long pk, Geometry geom, SpatialResultSet rs) throws SQLException {
        super.addSource(pk, geom, rs);
        if (Objects.requireNonNull(sceneDatabaseInputSettings.inputMode) == SceneDatabaseInputSettings.INPUT_MODE.INPUT_MODE_TRAFFIC_FLOW_DEN) {
            processTrafficFlowDEN(pk, rs);
        }
    }

    public void addSourceEmission(Long pk, ResultSet rs) throws SQLException {
        switch (sceneDatabaseInputSettings.inputMode) {
            case INPUT_MODE_TRAFFIC_FLOW:
                processTrafficFlow(pk, rs);
                break;
            case INPUT_MODE_LW:
                processEmission(pk, rs);
                break;
        }
    }

    /**
     * Link a source with a period and a spectrum
     * @param sourcePrimaryKey
     * @param period
     * @param wj
     */
    public void addSourceEmission(Long sourcePrimaryKey, String period, double[] wj) {
        int powerSpectrumHash = updatePowerSpectrumSet(wj);
        ArrayList<PeriodEmission> sourceEmissions;
        if(wjSources.containsKey(sourcePrimaryKey)) {
            sourceEmissions = wjSources.get(sourcePrimaryKey);
        } else {
            sourceEmissions = new ArrayList<>();
            wjSources.put(sourcePrimaryKey, sourceEmissions);
        }
        sourceEmissions.add(new PeriodEmission(period, powerSpectrumHash));
    }

    public static class PeriodEmission {
        public final String period;
        public final int emissionHash;

        public PeriodEmission(String period, int emissionHash) {
            this.period = period;
            this.emissionHash = emissionHash;
        }
    }

    /**
     * SceneWithEmission will read table according to this settings
     */
    public static class SceneDatabaseInputSettings {
        public enum INPUT_MODE {
            /** Read traffic from geometry source table */
            INPUT_MODE_TRAFFIC_FLOW_DEN,
            /** Read traffic from emission source table for each period */
            INPUT_MODE_TRAFFIC_FLOW,
            /** Read source emission noise level from source emission table for each period */
            INPUT_MODE_LW,
            /** Compute only attenuation */
            INPUT_MODE_ATTENUATION }

        INPUT_MODE inputMode = INPUT_MODE.INPUT_MODE_ATTENUATION;
        String sourcesEmissionTableName = "";
        String sourceEmissionPrimaryKeyField = "IDSOURCE";
        /** Cnossos coefficient version  (1 = 2015, 2 = 2020) */
        int coefficientVersion = 2;
        public String lwFrequencyPrepend = "LW";

        public SceneDatabaseInputSettings() {

        }

        public SceneDatabaseInputSettings(INPUT_MODE inputMode, String sourcesEmissionTableName) {
            this.inputMode = inputMode;
            this.sourcesEmissionTableName = sourcesEmissionTableName;
        }

        public int getCoefficientVersion() {
            return coefficientVersion;
        }

        public SceneDatabaseInputSettings setCoefficientVersion(int coefficientVersion) {
            this.coefficientVersion = coefficientVersion;
            return this;
        }

        public INPUT_MODE getInputMode() {
            return inputMode;
        }

        public void setInputMode(INPUT_MODE inputMode) {
            this.inputMode = inputMode;
        }

        public String getSourcesEmissionTableName() {
            return sourcesEmissionTableName;
        }

        public void setSourcesEmissionTableName(String sourcesEmissionTableName) {
            this.sourcesEmissionTableName = sourcesEmissionTableName;
        }

        public String getSourceEmissionPrimaryKeyField() {
            return sourceEmissionPrimaryKeyField;
        }

        public void setSourceEmissionPrimaryKeyField(String sourceEmissionPrimaryKeyField) {
            this.sourceEmissionPrimaryKeyField = sourceEmissionPrimaryKeyField;
        }

        public String getLwFrequencyPrepend() {
            return lwFrequencyPrepend;
        }

        public void setLwFrequencyPrepend(String lwFrequencyPrepend) {
            this.lwFrequencyPrepend = lwFrequencyPrepend;
        }
    }
}
