/**
 * NoiseModelling is an open-source tool designed to produce environmental noise maps on very large urban areas. It can be used as a Java library or be controlled through a user friendly web interface.
 *
 * This version is developed by Université Gustave Eiffel and CNRS
 * <http://noise-planet.org/noisemodelling.html>
 *
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 *
 * Contact: contact@noise-planet.org
 *
 */
/**
 * @Author Valentin Le Bescond, Université Gustave Eiffel
 * @Author Pierre Aumond, Université Gustave Eiffel
 */

package org.noise_planet.noisemodelling.wps.Dynamic

import geoserver.GeoServer
import geoserver.catalog.Store
import org.geotools.jdbc.JDBCDataStore
import org.h2gis.utilities.JDBCUtilities
import org.h2gis.utilities.TableLocation
import org.h2gis.utilities.dbtypes.DBTypes
import org.h2gis.utilities.dbtypes.DBUtils
import org.h2gis.utilities.wrapper.ConnectionWrapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.sql.*
import groovy.sql.Sql

title = 'Noise Map From Attenuation Matrix'
description = 'Noise Map From Attenuation Matrix.' +
        '<br/>'

inputs = [
        lwTable : [
                name: 'LW(t)',
                title: 'LW(t)',
                description: 'LW(t)' +
                        '<br/>The table must contain the following fields :' +
                        '<br/>PK, THE_GEOM, HZ63, HZ125, HZ250, HZ500, HZ1000, HZ2000, HZ4000, HZ8000, T' +
                        '<br/> with PK, the IDSOURCE and T a timestring',
                type: String.class
        ],
        lwTable_sourceId: [
                name: 'LW(t) sourceId',
                title: 'LW(t) sourceId',
                description: 'LW(t)tring',
                min        : 0,
                max        : 1,
                type: String.class
        ],
        attenuationTable : [
        name: 'Attenuation Matrix Table name',
        title: 'Attenuation Matrix Table name',
        description: 'Attenuation Matrix Table name, Obtained from the Noise_level_from_source script with "confExportSourceId" enabled' +
                '<br/>The table must contain the following fields :' +
                '<br/>IDRECEIVER, IDSOURCE, THE_GEOM, HZ63, HZ125, HZ250, HZ500, HZ1000, HZ2000, HZ4000, HZ8000',
        type: String.class
    ],
        outputTable : [
                name: 'outputTable Matrix Table name',
                title: 'outputTable Matrix Table name',
                description: 'outputTable',
                type: String.class
        ]
]

outputs = [
    result: [
        name: 'Result output string',
        title: 'Result output string',
        description: 'This type of result does not allow the blocks to be linked together.',
        type: String.class
    ]
]

static Connection openGeoserverDataStoreConnection(String dbName) {
    if (dbName == null || dbName.isEmpty()) {
        dbName = new GeoServer().catalog.getStoreNames().get(0)
    }
    Store store = new GeoServer().catalog.getStore(dbName)
    JDBCDataStore jdbcDataStore = (JDBCDataStore) store.getDataStoreInfo().getDataStore(null)
    return jdbcDataStore.getDataSource().getConnection()
}


def run(input) {

    // Get name of the database
    String dbName = "h2gisdb"

    // Open connection
    openGeoserverDataStoreConnection(dbName).withCloseable {
        Connection connection ->
            return [result: exec(connection, input)]
    }
}

// main function of the script
def exec(Connection connection, input) {

    DBTypes dbType = DBUtils.getDBType(connection.unwrap(Connection.class))

    connection = new ConnectionWrapper(connection)

    Sql sql = new Sql(connection)

    String resultString

    Logger logger = LoggerFactory.getLogger("org.noise_planet.noisemodelling")
    logger.info('Start : Noise_From_Attenuation_Matrix')
    logger.info("inputs {}", input)



    String lwTable_sourceId = "IDSOURCE"
    if (input['lwTable_sourceId']) {
        lwTable_sourceId = input['lwTable_sourceId']
    }


    String outputTable = input['outputTable'].toString().toUpperCase()
    String attenuationTable = input['attenuationTable'].toString().toUpperCase()
    String lwTable = input['lwTable'].toString().toUpperCase()
    String timeString = "PERIOD"
    String prefix = "LW"

    // Groovy Dollar slashy string that contain the queries

    def query2 = $/CREATE TABLE $outputTable AS SELECT lg.IDRECEIVER,
            mr.$timeString AS $timeString,
            lg.the_geom,
            10 * LOG10( SUM(POWER(10,(mr.LW63 + lg.LW63) / 10))) AS LW63,
            10 * LOG10( SUM(POWER(10,(mr.LW125 + lg.LW125) / 10))) AS LW125,
            10 * LOG10( SUM(POWER(10,(mr.LW250 + lg.LW250) / 10))) AS LW250,
            10 * LOG10( SUM(POWER(10,(mr.LW500 + lg.LW500) / 10))) AS LW500,
            10 * LOG10( SUM(POWER(10,(mr.LW1000 + lg.LW1000) / 10))) AS LW1000,
            10 * LOG10( SUM(POWER(10,(mr.LW2000 + lg.LW2000) / 10))) AS LW2000,
            10 * LOG10( SUM(POWER(10,(mr.LW4000 + lg.LW4000) / 10))) AS LW4000,
            10 * LOG10( SUM(POWER(10,(mr.LW8000 + lg.LW8000) / 10))) AS LW8000
        FROM $attenuationTable  lg , $lwTable mr WHERE lg.IDSOURCE = mr.$lwTable_sourceId 
        GROUP BY lg.IDRECEIVER, mr.$timeString;
        
        ALTER TABLE  $outputTable ADD COLUMN LAEQ float as 10*log10((power(10,(${prefix}63-26.2)/10)+power(10,(${prefix}125-16.1)/10)+power(10,(${prefix}250-8.6)/10)+power(10,(${prefix}500-3.2)/10)+power(10,(${prefix}1000)/10)+power(10,(${prefix}2000+1.2)/10)+power(10,(${prefix}4000+1)/10)+power(10,(${prefix}8000-1.1)/10)));
        ALTER TABLE $outputTable ADD COLUMN LEQ float as 10*log10((power(10,(${prefix}63)/10)+power(10,(${prefix}125)/10)+power(10,(${prefix}250)/10)+power(10,(${prefix}500)/10)+power(10,(${prefix}1000)/10)+power(10,(${prefix}2000)/10)+power(10,(${prefix}4000)/10)+power(10,(${prefix}8000)/10)));
        CREATE UNIQUE INDEX ON $outputTable (IDRECEIVER, $timeString);
    /$

    sql.execute(query2.toString())

    logger.info('End : Noise_From_Attenuation_Matrix_MatSim')
    resultString = "Process done. Table of receivers LT_GEOM created !"
    logger.info('Result : ' + resultString)
    return resultString
}
