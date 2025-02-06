/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */
package org.noise_planet.noisemodelling.jdbc.input;

import org.h2gis.api.ProgressVisitor;

import java.sql.Connection;

/**
 * SceneWithEmission will read table according to this settings
 */
public class SceneDatabaseInputSettings {
    public enum INPUT_MODE {
        /** Guess input mode at {@link org.noise_planet.noisemodelling.jdbc.NoiseMapByReceiverMaker.TableLoader#initialize(Connection, ProgressVisitor)} step */
        INPUT_MODE_GUESS,
        /** Read traffic from geometry source table */
        INPUT_MODE_TRAFFIC_FLOW_DEN,
        /** Read source emission noise level limited to DEN periods from source geometry table */
        INPUT_MODE_LW_DEN,
        /** Read traffic from emission source table for each period */
        INPUT_MODE_TRAFFIC_FLOW,
        /** Read source emission noise level from source emission table for each period */
        INPUT_MODE_LW,
        /** Compute only attenuation */
        INPUT_MODE_ATTENUATION }

    INPUT_MODE inputMode = INPUT_MODE.INPUT_MODE_GUESS;
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