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
 */

package org.noise_planet.noisemodelling.wps.Import_and_Export;

import geoserver.GeoServer;
import geoserver.catalog.Store;

import groovy.sql.Sql
import groovy.transform.CompileStatic;
import org.geotools.jdbc.JDBCDataStore
import org.h2gis.utilities.wrapper.ConnectionWrapper;
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Coordinate;

import org.openstreetmap.osmosis.core.container.v0_6.EntityContainer;
import org.openstreetmap.osmosis.core.container.v0_6.NodeContainer;
import org.openstreetmap.osmosis.core.container.v0_6.RelationContainer;
import org.openstreetmap.osmosis.core.container.v0_6.WayContainer;
import org.openstreetmap.osmosis.core.domain.v0_6.Tag;
import org.openstreetmap.osmosis.core.domain.v0_6.Way;
import org.openstreetmap.osmosis.core.domain.v0_6.Relation;
import org.openstreetmap.osmosis.core.domain.v0_6.WayNode;
import org.openstreetmap.osmosis.core.domain.v0_6.Node;
import org.openstreetmap.osmosis.core.task.v0_6.Sink;
 
import crosby.binary.osmosis.OsmosisReader
import org.slf4j.Logger
import org.slf4j.LoggerFactory;

import java.sql.Connection

title = 'Import BUILDINGS, GROUND and ROADS tables from OSM'
description = 'Convert PBF file (https://wiki.openstreetmap.org/wiki/PBF_Format) to input tables. ' +
        '<br> The user can choose to create one to three output tables : <br>' +
        '-  <b> BUILDINGS  </b> : a table containing the building. </br>' +
        '-  <b> GROUND  </b> : surface/ground acoustic absorption table. </br>' +
        '-  <b> ROADS  </b> : a table containing the roads. As OSM does not include data on road traffic flows, default values are assigned according to the -Good Practice Guide for Strategic Noise Mapping and the Production of Associated Data on Noise Exposure - Version 2-. </br>'

inputs = [
        pathFile        : [
                name       : 'Path of the OSM PBF file',

                title      : 'Path of the OSM PBF file',
                description: 'Path of the OSM PBF file including extension. ' +
                        '</br> For example : c:/home/area.osm.pbf',
                type       : String.class
        ],
        ignoreBuilding: [
                name       : 'Do not import Buildings',
                title      : 'Do not import Buildings',
                description: 'If the box is checked, the table BUILDINGS will NOT be extracted. ' +
                        '<br>  The table will contain : </br>' +
                        '- <b> THE_GEOM </b> : the 2D geometry of the building (POLYGON or MULTIPOLYGON). </br>' +
                        '- <b> HEIGHT </b> : the height of the building (FLOAT). ' +
                        'If the height of the buildings is not available then it is deducted from the number of floors (if available) with the addition of a small random variation from one building to another. ' +
                        'Finally, if no information is available, a height of 5 m is set by default.',
                min        : 0, max: 1,
                type       : Boolean.class
        ],
        ignoreGround  : [
                name       : 'Do not import Surface acoustic absorption',
                title      : 'Do not import Surface acoustic absorption',
                description: 'If the box is checked, the table GROUND will NOT be extracted.' +
                        '</br>The table will contain : </br> ' +
                        '- <b> THE_GEOM </b> : the 2D geometry of the sources (POLYGON or MULTIPOLYGON).</br> ' +
                        '- <b> G </b> : the acoustic absorption of a ground (FLOAT between 0 : very hard and 1 : very soft).</br> ',
                min        : 0, max: 1,
                type       : Boolean.class
        ],
        ignoreRoads   : [
                name       : 'Do not import Roads',
                title      : 'Do not import Roads',
                description: 'If the box is checked, the table ROADS will NOT be extracted. ' +
                        "<br>  The table will contain : </br>" +
                        "- <b> PK </b> : an identifier. It shall be a primary key (INTEGER, PRIMARY KEY)<br/>" +
                        "- <b> LV_D </b> : Hourly average light and heavy vehicle count (6-18h) (DOUBLE)<br/>" +
                        "- <b> LV_E </b> :  Hourly average light and heavy vehicle count (18-22h) (DOUBLE)<br/>" +
                        "- <b> LV_N </b> :  Hourly average light and heavy vehicle count (22-6h) (DOUBLE)<br/>" +
                        "- <b> HV_D </b> :  Hourly average heavy vehicle count (6-18h) (DOUBLE)<br/>" +
                        "- <b> HV_E </b> :  Hourly average heavy vehicle count (18-22h) (DOUBLE)<br/>" +
                        "- <b> HV_N </b> :  Hourly average heavy vehicle count (22-6h) (DOUBLE)<br/>" +
                        "- <b> LV_SPD_D </b> :  Hourly average light vehicle speed (6-18h) (DOUBLE)<br/>" +
                        "- <b> LV_SPD_E </b> :  Hourly average light vehicle speed (18-22h) (DOUBLE)<br/>" +
                        "- <b> LV_SPD_N </b> :  Hourly average light vehicle speed (22-6h) (DOUBLE)<br/>" +
                        "- <b> HV_SPD_D </b> :  Hourly average heavy vehicle speed (6-18h) (DOUBLE)<br/>" +
                        "- <b> HV_SPD_E </b> :  Hourly average heavy vehicle speed (18-22h) (DOUBLE)<br/>" +
                        "- <b> HV_SPD_N </b> :  Hourly average heavy vehicle speed (22-6h) (DOUBLE)<br/>" +
                        "- <b> PVMT </b> :  CNOSSOS road pavement identifier (ex: NL05) (VARCHAR)" +
                        "</br> </br> <b> This information is created using the importance of the roads in OSM.</b>.",
                min        : 0, max: 1,
                type       : Boolean.class
        ],
        targetSRID      : [
                name       : 'Target projection identifier',
                title      : 'Target projection identifier',
                description: 'Target projection identifier (also called SRID) of your table. It should be an EPSG code, a integer with 4 or 5 digits (ex: 3857 is Web Mercator projection). ' +
                        '</br>  The target SRID must be in metric coordinates. </br>',
                type       : Integer.class
        ],
        removeTunnels  : [
                name       : 'Remove tunnels from OSM data',
                title      : 'Remove tunnels from OSM data',
                description: 'Remove roads from OSM data which contains OSM tag <b>tunnel=yes</b>.',
                min        : 0, max: 1,
                type       : Boolean.class
        ],
]

outputs = [
        result: [
                name: 'Result output string',
                title: 'Result output string',
                description: 'This type of result does not allow the blocks to be linked together.',
                type: String.class
        ]
]

// Open Connection to Geoserver
static Connection openGeoserverDataStoreConnection(String dbName) {
    if (dbName == null || dbName.isEmpty()) {
        dbName = new GeoServer().catalog.getStoreNames().get(0)
    }
    Store store = new GeoServer().catalog.getStore(dbName)
    JDBCDataStore jdbcDataStore = (JDBCDataStore) store.getDataStoreInfo().getDataStore(null)
    return jdbcDataStore.getDataSource().getConnection()
}

// run the script
def run(input) {

    // Get name of the database
    // by default an embedded h2gis database is created
    // Advanced user can replace this database for a postGis or h2Gis server database.
    String dbName = "h2gisdb"

    // Open connection
    openGeoserverDataStoreConnection(dbName).withCloseable {
        Connection connection ->
            return [result: exec(connection, input)]
    }
}

// main function of the script
def exec(Connection connection, input) {

    connection = new ConnectionWrapper(connection)

    Sql sql = new Sql(connection)

    String resultString

    Logger logger = LoggerFactory.getLogger("org.noise_planet.noisemodelling")
    logger.info('Start : Get Buildings from PBF OSM')
    logger.info("inputs {}", input)

    // -------------------
    // Get every inputs
    // -------------------

    String pathFile = input["pathFile"] as String

    Boolean ignoreBuilding = false
    if ('ignoreBuilding' in input) {
        ignoreBuilding = input['ignoreBuilding'] as Boolean
    }

    Boolean ignoreGround = false
    if ('ignoreGround' in input) {
        ignoreGround = input['ignoreGround'] as Boolean
    }

    Boolean ignoreRoads = false
    if ('ignoreRoads' in input) {
        ignoreRoads = input['ignoreRoads'] as Boolean
    }

    Integer srid = 3857
    if (input['targetSRID']) {
        srid = input['targetSRID'] as Integer
    }

    Boolean removeTunnels = false
    if ('removeTunnels' in input) {
        removeTunnels = input['removeTunnels'] as Boolean
    }

    InputStream inputStream = new FileInputStream(pathFile);
    OsmosisReader reader = new OsmosisReader(inputStream);
    OsmHandler handler = new OsmHandler(logger, ignoreBuilding, ignoreRoads, ignoreGround, removeTunnels)
    reader.setSink(handler);
    reader.run();

    logger.info('PBF Read done')

    if (!ignoreBuilding) {
        String tableName = "MAP_BUILDINGS_GEOM";

        sql.execute("DROP TABLE IF EXISTS " + tableName)
        sql.execute("CREATE TABLE " + tableName + '''( 
            ID_WAY integer PRIMARY KEY, 
            THE_GEOM geometry,
            HEIGHT real
        );''')

        for (Building building: handler.buildings) {
            sql.execute("INSERT INTO " + tableName + " VALUES (" + building.id + ", ST_MakeValid(ST_SIMPLIFYPRESERVETOPOLOGY(ST_Transform(ST_GeomFromText('" + building.geom + "', 4326), "+srid+"),0.1)), " + building.height + ")")
        }

        sql.execute('''
            CREATE SPATIAL INDEX IF NOT EXISTS BUILDINGS_INDEX ON ''' + tableName + '''(the_geom);
            -- list buildings that intersects with other buildings that have a greater area
            drop table if exists tmp_relation_buildings_buildings;
            create table tmp_relation_buildings_buildings as select s1.ID_WAY as PK_BUILDING, S2.ID_WAY as PK2_BUILDING FROM MAP_BUILDINGS_GEOM S1, MAP_BUILDINGS_GEOM S2 WHERE ST_AREA(S1.THE_GEOM) < ST_AREA(S2.THE_GEOM) AND S1.THE_GEOM && S2.THE_GEOM AND ST_DISTANCE(S1.THE_GEOM, S2.THE_GEOM) <= 0.1;
            
            -- Alter that small area buildings by removing shared area
            drop table if exists tmp_buildings_truncated;
            create table tmp_buildings_truncated as select PK_BUILDING, ST_DIFFERENCE(s1.the_geom,  ST_BUFFER(ST_Collect(s2.the_geom), 0.1, 'join=mitre')) the_geom, s1.HEIGHT HEIGHT from tmp_relation_buildings_buildings r, MAP_BUILDINGS_GEOM s1, MAP_BUILDINGS_GEOM s2 WHERE PK_BUILDING = S1.ID_WAY AND PK2_BUILDING = S2.ID_WAY  GROUP BY PK_BUILDING;
            
            -- merge original buildings with altered buildings 
            DROP TABLE IF EXISTS BUILDINGS;
            create table BUILDINGS(PK INTEGER PRIMARY KEY, THE_GEOM GEOMETRY, HEIGHT real)  as select s.id_way, ST_SETSRID(s.the_geom, '''+srid+'''), s.HEIGHT from  MAP_BUILDINGS_GEOM s where id_way not in (select PK_BUILDING from tmp_buildings_truncated) UNION ALL select PK_BUILDING, ST_SETSRID(the_geom, '''+srid+'''), HEIGHT from tmp_buildings_truncated WHERE NOT st_isempty(the_geom);
    
            drop table if exists tmp_buildings_truncated;
            drop table if exists tmp_relation_buildings_buildings;
            drop table if exists MAP_BUILDINGS_GEOM;
        ''');

        sql.execute("CREATE SPATIAL INDEX IF NOT EXISTS BUILDING_GEOM_INDEX ON " + "BUILDINGS" + "(THE_GEOM)")

    }

    if (!ignoreRoads) {
        sql.execute("DROP TABLE IF EXISTS ROADS")
        sql.execute("create table ROADS (PK serial, ID_WAY integer, THE_GEOM geometry, TV_D integer, TV_E integer,TV_N integer,HV_D integer,HV_E integer,HV_N integer,LV_SPD_D integer,LV_SPD_E integer,LV_SPD_N integer,HV_SPD_D integer, HV_SPD_E integer,HV_SPD_N integer, PVMT varchar(10));")

        for (Road road: handler.roads) {
            if (road.geom.isEmpty()) {
                continue;
            }
            String query = 'INSERT INTO ROADS(ID_WAY, ' +
                    'THE_GEOM, ' +
                    'LV_D, LV_E, LV_N, ' +
                    'HV_D, HV_E, HV_N, ' +
                    'LV_SPD_D, LV_SPD_E, LV_SPD_N, ' +
                    'HV_SPD_D, HV_SPD_E, HV_SPD_N, ' +
                    'PVMT) ' +
                    ' VALUES (?,' +
                    'st_setsrid(st_updatez(ST_precisionreducer(ST_SIMPLIFYPRESERVETOPOLOGY(ST_TRANSFORM(ST_GeomFromText(?, 4326), '+srid+'),0.1),1), 0.05), ' + srid + '),' +
                    '?,?,?,?,?,?,?,?,?,?,?,?,?);'
            sql.execute(query, [road.id, road.geom,
                    Road.aadf_d[road.category],
                    Road.aadf_e[road.category],
                    Road.aadf_n[road.category],
                    Road.aadf_d[road.category] * Road.hv_d[road.category],
                    Road.aadf_e[road.category] * Road.hv_e[road.category],
                    Road.aadf_n[road.category] * Road.hv_n[road.category],
                    Road.speed[road.category], Road.speed[road.category], Road.speed[road.category],
                    Road.speed[road.category], Road.speed[road.category], Road.speed[road.category],
                    'NL08'])
        }
        sql.execute("CREATE SPATIAL INDEX IF NOT EXISTS ROADS_GEOM_INDEX ON " + "ROADS" + "(THE_GEOM)")
    }

    if (!ignoreGround) {
        sql.execute("DROP TABLE IF EXISTS GROUND")
        sql.execute("create table GROUND (PK serial, ID_WAY int, THE_GEOM geometry, PRIORITY int, G double);")

        for (Ground ground : handler.grounds) {
            if (ground.priority == 0) {
                continue
            }
            if (ground.geom.isEmpty()) {
                continue
            }
            sql.execute("INSERT INTO GROUND (ID_WAY, THE_GEOM, PRIORITY, G) VALUES (" + ground.id + ", ST_Transform(ST_GeomFromText('" + ground.geom + "', 4326), " + srid + "), " + ground.priority + ", " + ground.coeff_G + ")")
        }
        sql.execute("CREATE SPATIAL INDEX IF NOT EXISTS GROUND_GEOM_INDEX ON " + "GROUND" + "(THE_GEOM)")
    }

    logger.info('SQL INSERT done')


    resultString = "nodes : " + handler.nb_nodes
    resultString += "<br>\n"
    resultString += "ways : " + handler.nb_ways
    resultString += "<br>\n"
    resultString += "relations : " + handler.nb_relations
    resultString += "<br>\n"
    resultString += "buildings : " + handler.nb_buildings
    resultString += "<br>\n"
    resultString += "roads : " + handler.nb_roads
    resultString += "<br>\n"
    resultString += "grounds : " + handler.nb_grounds
    resultString += "<br>\n"

    logger.info('End : Get Buildings from PBF OSM')
    logger.info('Result : ' + resultString)
    return resultString
}


public class OsmHandler implements Sink {
 
    public int nb_ways = 0;
    public int nb_nodes = 0;
    public int nb_relations = 0;
    public int nb_buildings = 0;
    public int nb_roads = 0;
    public int nb_grounds = 0;

    Random rand = new Random();
    
    public Map<Long, Node> nodes = new HashMap<Long, Node>();
    public Map<Long, Way> ways = new HashMap<Long, Way>();
    public Map<Long, Relation> relations = new HashMap<Long, Relation>();
    public List<Building> buildings = new ArrayList<Building>();
    public List<Road> roads = new ArrayList<Road>();
    public List<Ground> grounds = new ArrayList<Ground>();

    Logger logger
    boolean ignoreBuildings
    boolean ignoreRoads
    boolean ignoreGround
    boolean removeTunnels

    OsmHandler(Logger logger, boolean ignoreBuildings, boolean ignoreRoads, boolean ignoreGround, boolean removeTunnels) {
        this.logger = logger
        this.ignoreBuildings = ignoreBuildings
        this.ignoreRoads = ignoreRoads
        this.ignoreGround = ignoreGround
        this.removeTunnels = removeTunnels
    }

    @Override
    public void initialize(Map<String, Object> arg0) {
    }
 
    @Override
    public void process(EntityContainer entityContainer) {
        if (entityContainer instanceof NodeContainer) {
            nb_nodes++;
            Node node = ((NodeContainer) entityContainer).getEntity();
            nodes.put(node.getId(), node);
        } else if (entityContainer instanceof WayContainer) {
            nb_ways++;
            Way way = ((WayContainer) entityContainer).getEntity();
            ways.put(way.getId(), way);
            boolean isBuilding = false;
            boolean isRoad = false;
            boolean isTunnel = false;
            double height = 4.0 + rand.nextDouble() * 2.1;
            boolean trueHeightFound = false;
            for (Tag tag : way.getTags()) {
                if ("building".equalsIgnoreCase(tag.getKey())) {
                    isBuilding = true;
                }
                if ("tunnel".equalsIgnoreCase(tag.getKey()) && "yes".equalsIgnoreCase(tag.getValue())) {
                    isTunnel = true;
                }
                if ("highway".equalsIgnoreCase((tag.getKey()))) {
                    isRoad = true
                }
                if (isBuilding) {
                    if (!trueHeightFound && "building:levels".equalsIgnoreCase(tag.getKey())) {
                        height = height - 4 + Double.parseDouble(tag.getValue().replaceAll("[^0-9]+", "")) * 3.0;
                    }
                    if ("height".equalsIgnoreCase(tag.getKey())) {
                        height = Double.parseDouble(tag.getValue().replaceAll("[^0-9]+", ""));
                        trueHeightFound = true;
                    }
                }
            }
            if (!ignoreBuildings && isBuilding && way.isClosed()) {
                buildings.add(new Building(way, height));
                nb_buildings++;
            }
            if (!ignoreRoads && isRoad) {
                if (removeTunnels && isTunnel) {
                    return
                }
                roads.add(new Road(way));
                nb_roads++;
            }
            if (!ignoreGround && !isBuilding && !isRoad && way.isClosed()) {
                grounds.add(new Ground(way));
                nb_grounds++;
            }
        } else if (entityContainer instanceof RelationContainer) {
            nb_relations++;
            Relation rel = ((RelationContainer) entityContainer).getEntity();
            relations.put(rel.getId(), rel);
        } else {
            System.out.println("Unknown Entity!");
        }
    }
 
    @Override
    public void complete() {
        for(Building building: buildings) {
            building.setGeom(calculateBuildingGeometry(building.way));
        }
        for(Road road: roads) {
            road.setGeom(calculateRoadGeometry(road.way));
        }
        GeometryFactory geomFactory = new GeometryFactory();
        for(Ground ground: grounds) {
            if (ground.priority == 0) {
                ground.setGeom(geomFactory.createPolygon())
                continue
            }
            Geometry geom = calculateGroundGeometry(ground.way)
            ground.setGeom(geom)
        }
        int doPrint = 2
        for (int j = 0; j < grounds.size(); j++) {
            if (j >= doPrint) {
                logger.info("cleaning GROUND geom : " + j + "/" + grounds.size())
                doPrint *= 2
            }
            if (grounds[j].geom.isEmpty() || !grounds[j].geom.isValid()) {
                continue
            }
            if (!["Polygon", "MultiPolygon"].contains(grounds[j].geom.geometryType)) {
                continue
            }
            for (int k = 0; k < grounds.size(); k++) {
                if (j == k) {
                    continue
                }
                if (grounds[k].geom.isEmpty() || !grounds[k].geom.isValid()) {
                    continue
                }
                if (!["Polygon", "MultiPolygon"].contains(grounds[k].geom.geometryType)) {
                    continue
                }
                if (!grounds[j].geom.intersects(grounds[k].geom)) {
                    continue
                }
                if (grounds[j].priority >= grounds[k].priority) {
                    grounds[k].geom = grounds[k].geom.difference(grounds[j].geom)
                }
                if (grounds[j].priority < grounds[k].priority) {
                    grounds[j].geom = grounds[j].geom.difference(grounds[k].geom)
                }
            }
        }
    }
 
    @Override
    public void close() {
    }

    public Geometry calculateBuildingGeometry(Way way) {
        GeometryFactory geomFactory = new GeometryFactory();
        if (way == null) {
            return geomFactory.createPolygon();
        }
        List<WayNode> wayNodes = way.getWayNodes();
        if (wayNodes.size() < 4) {
            return geomFactory.createPolygon();
        }
        Coordinate[] shell = new Coordinate[wayNodes.size()];
        for(int i = 0; i < wayNodes.size(); i++) {
            Node node = nodes.get(wayNodes.get(i).getNodeId());
            double x = node.getLongitude();
            double y = node.getLatitude();
            shell[i] = new Coordinate(x, y, 0.0);
        }
        return geomFactory.createPolygon(shell);
    }

    public Geometry calculateRoadGeometry(Way way) {
        GeometryFactory geomFactory = new GeometryFactory();
        if (way == null) {
            return geomFactory.createLineString();
        }
        List<WayNode> wayNodes = way.getWayNodes();
        if (wayNodes.size() < 2) {
            return geomFactory.createLineString();
        }
        Coordinate[] coordinates = new Coordinate[wayNodes.size()];
        for(int i = 0; i < wayNodes.size(); i++) {
            Node node = nodes.get(wayNodes.get(i).getNodeId());
            double x = node.getLongitude();
            double y = node.getLatitude();
            coordinates[i] = new Coordinate(x, y, 0.0);
        }
        return geomFactory.createLineString(coordinates);
    }

    public Geometry calculateGroundGeometry(Way way) {
        GeometryFactory geomFactory = new GeometryFactory();
        if (way == null) {
            return geomFactory.createPolygon();
        }
        List<WayNode> wayNodes = way.getWayNodes();
        if (wayNodes.size() < 4) {
            return geomFactory.createPolygon();
        }
        Coordinate[] shell = new Coordinate[wayNodes.size()];
        for (int i = 0; i < wayNodes.size(); i++) {
            Node node = nodes.get(wayNodes.get(i).getNodeId());
            double x = node.getLongitude();
            double y = node.getLatitude();
            shell[i] = new Coordinate(x, y, 0.0);
        }
        return geomFactory.createPolygon(shell);
    }
}


public class Building {

    long id;
    Way way;
    Geometry geom;
    double height = 0.0;

    Building(Way way) {
        this.way = way;
        this.id = way.getId();
        double h = 4.0 + rand.nextDouble() * 2.1;
        boolean trueHeightFound = false;
        for (Tag tag : way.getTags()) {
            if (!trueHeightFound && "building:levels".equalsIgnoreCase(tag.getKey())) {
                h = h - 4 + Double.parseDouble(tag.getValue().replaceAll("[^0-9]+", "")) * 3.0;
            }
            if ("height".equalsIgnoreCase(tag.getKey())) {
                h = Double.parseDouble(tag.getValue().replaceAll("[^0-9]+", ""));
                trueHeightFound = true;
            }
        }
        this.height = h;
    }
    Building(Way way, double height) {
        this.way = way;
        this.id = way.getId();
        this.height = height;
    }

    void setGeom(Geometry geom) {
        this.geom = geom;
    }

    void setHeight(double height) {
        this.height = height;
    }
}

public class Road {


    def static aadf_d = [17936, 7124, 1400, 700, 350, 175]
    def static aadf_e = [3826, 1069, 400, 200, 100, 50]
    def static aadf_n = [2152, 712, 200, 100, 50, 25]
    def static hv_d = [0.2, 0.2, 0.15, 0.10, 0.05, 0.02]
    def static hv_e = [0.2, 0.15, 0.10, 0.06, 0.02, 0.01]
    def static hv_n = [0.2, 0.05, 0.05, 0.03, 0.01, 0.0]
    def static speed = [110, 80, 50, 50, 30, 30]

    long id;
    Way way;
    Geometry geom;
    double maxspeed = 0.0;
    String type = null;
    int category = 5;

    Road(Way way) {
        this.way = way;
        this.id = way.getId();
        for (Tag tag : way.getTags()) {
            if ("maxspeed".equalsIgnoreCase(tag.getKey())) {
                this.maxspeed = Double.parseDouble(tag.getValue().replaceAll("[^0-9]+", ""));
                if (tag.getValue().contains("mph")) {
                    maxspeed = maxspeed * 1.60934
                }
            }
            if ("highway".equalsIgnoreCase(tag.getKey())) {
                this.type = tag.getValue();
            }
        }
        updateCategory();
    }

    void updateCategory() {
        if (type.equalsIgnoreCase("trunk")) {
            category = 0;
        }
        else if (type.equalsIgnoreCase("trunk_link")) {
            category = 0;
        }
        else if (type.equalsIgnoreCase("primary")) {
            category = 1;
        }
        else if (type.equalsIgnoreCase("primary_link")) {
            category = 1;
        }
        else if (type.equalsIgnoreCase("secondary")) {
            category = 2;
        }
        else if (type.equalsIgnoreCase("secondary_link")) {
            category = 2;
        }
        else if (type.equalsIgnoreCase("tertiary")) {
            category = 2;
        }
        else if (type.equalsIgnoreCase("tertiary_link") && maxspeed > 40) {
            category = 3;
        }
        else if (type.equalsIgnoreCase("residential") && maxspeed > 40) {
            category = 3;
        }
        else if (type.equalsIgnoreCase("unclassified") && maxspeed > 40) {
            category = 3;
        }
        else if (type.equalsIgnoreCase("tertiary_link") && maxspeed <= 40) {
            category = 4;
        }
        else if (type.equalsIgnoreCase("residential") && maxspeed <= 40) {
            category = 4;
        }
        else if (type.equalsIgnoreCase("unclassified") && maxspeed <= 40) {
            category = 4;
        }
        else if (type.equalsIgnoreCase("service")) {
            category = 5;
        }
        else if (type.equalsIgnoreCase("living_street")) {
            category = 5;
        }
    }

    void setGeom(Geometry geom) {
        this.geom = geom;
    }

    void setHeight(double height) {
        this.height = height;
    }
}

public class Ground {

    long id;
    Way way;
    Geometry geom;

    int priority = 0;
    float coeff_G = 0.0;

    Ground(Way way) {
        this.way = way;
        this.id = way.getId();

        String primaryTagKey = "";
        String primaryTagValue = "";
        String secondaryTagKey = "";
        String secondaryTagValue = "";

        for (Tag tag : way.getTags()) {
            String key = tag.getKey()
            String value = tag.getValue()
            if (["aeroway","amenity","landcover","landuse","leisure","natural","water","waterway"].contains(key)) {
                primaryTagKey = key
                primaryTagValue = value
            }
            if (["parking","covered","surface","wetland"].contains(key)) {
                secondaryTagKey = key
                secondaryTagValue = value
            }
        }
        if (primaryTagKey == "aeroway" &&
                ["taxiway","runway","aerodrome","helipad","apron","taxilane"].contains(primaryTagValue)) {
            priority = 29
            coeff_G = 0.1
        }
        if (primaryTagKey == "amenity") {
            if (primaryTagValue == "taxi") {
                priority = 22
                coeff_G = 0.1
            }
            if (primaryTagValue == "parking" && secondaryTagKey == "parking" && !secondaryTagValue.contains("underground")) {
                priority = 22
                coeff_G = 0.1
            }
        }
        if (primaryTagKey == "landcover") {
            if (primaryTagValue == "water") {
                priority = 7
                coeff_G = 0.3
            }
            if (["bedrock","bare_ground","concrete","asphalt"].contains(primaryTagValue)) {
                priority = 8
                coeff_G = 0.1
            }
            if (primaryTagValue == "sand") {
                priority = 9
                coeff_G = 0.2
            }
            if (["scrub","gravel"].contains(primaryTagValue)) {
                priority = 10
                coeff_G = 0.7
            }
            if (["bushes","vegetation"].contains(primaryTagValue)) {
                priority = 11
                coeff_G = 0.8
            }
            if (["flowerbed","trees, grass","trees","grass","tree","grassland","wood"].contains(primaryTagValue)) {
                priority = 12
                coeff_G = 1.0
            }
        }
        if (primaryTagKey == "landuse") {
            if (primaryTagValue == "reservoir" && secondaryTagKey == "covered" && secondaryTagValue == "no") {
                priority = 7
                coeff_G = 0.3
            }
            if (["residential","industrial","retail","harbour","quarry","landfill",
                 "construction","commercial","garages","railway","basin","farmyard"].contains(primaryTagValue)) {
                priority = 23
                coeff_G = 0.1
            }
            if (primaryTagValue == "brownfield") {
                priority = 24
                coeff_G = 0.3
            }
            if (primaryTagValue == "salt_pond") {
                priority = 25
                coeff_G = 0.5
            }
            if (["farmland","allotements","logging","plant_nursery","farm"].contains(primaryTagValue)) {
                priority = 26
                coeff_G = 0.7
            }
            if (["vineyard","orchard","greenfield","village_green"].contains(primaryTagValue)) {
                priority = 27
                coeff_G = 0.8
            }
            if (["meadow","forest","grass"].contains(primaryTagValue)) {
                priority = 28
                coeff_G = 1.0
            }
        }
        if (primaryTagKey == "leisure") {
            if (primaryTagValue == "pitch" && secondaryTagKey == "surface") {
                if (["asphalt","concrete","concrete:plate"].contains(secondaryTagValue)) {
                    priority = 1
                    coeff_G = 0.1
                }
                if (["dirt","compacted","sand","wood","clay"].contains(secondaryTagValue)) {
                    priority = 2
                    coeff_G = 0.2
                }
                if (["ground","fine_gravel","earth","mud"].contains(secondaryTagValue)) {
                    priority = 4
                    coeff_G = 0.5
                }
                if (["gravel"].contains(secondaryTagValue)) {
                    priority = 5
                    coeff_G = 0.7
                }
                if (["grass"].contains(secondaryTagValue)) {
                    priority = 6
                    coeff_G = 1.0
                }
            }
            if (primaryTagValue == "marina") {
                priority = 30
                coeff_G = 0.2
            }
            if (primaryTagValue == "park") {
                priority = 31
                coeff_G = 0.7
            }
            if (["garden","nature_reserve","golf_course"].contains(primaryTagValue)) {
                priority = 32
                coeff_G = 1.0
            }
        }
        if (primaryTagKey == "natural") {
            if (primaryTagValue == "beach" && secondaryTagKey == "surface") {
                if (secondaryTagValue == "sand") {
                    priority = 2
                    coeff_G = 0.2
                }
                if (secondaryTagValue == "shingle") {
                    priority = 3
                    coeff_G = 0.3
                }
                if (secondaryTagValue == "gravel" || secondaryTagValue == "pebbles") {
                    priority = 5
                    coeff_G = 0.7
                }
            }
            if (primaryTagValue == "wetland" && secondaryTagKey == "wetland") {
                if (secondaryTagValue == "tidalflat") {
                    priority = 14
                    coeff_G = 0.2
                }
                if (secondaryTagValue == "saltern") {
                    priority = 15
                    coeff_G = 0.3
                }
                if (secondaryTagValue == "marsh") {
                    priority = 16
                    coeff_G = 0.4
                }
                if (secondaryTagValue == "reebed") {
                    priority = 18
                    coeff_G = 0.6
                }
                if (secondaryTagValue == "bog") {
                    priority = 19
                    coeff_G = 0.7
                }
                if (secondaryTagValue == "mangrove") {
                    priority = 21
                    coeff_G = 1.0
                }
                if (["swamp","saltmarsh","wet_meadow"].contains(secondaryTagValue)) {
                    priority = 20
                    coeff_G = 0.9
                }
            }
            if (primaryTagValue == "bare_rock") {
                priority = 13
                coeff_G = 0.1
            }
            if (primaryTagValue == "bay") {
                priority = 7
                coeff_G = 0.3
            }
            if (primaryTagValue == "glacier") {
                priority = 13
                coeff_G = 0.1
            }
            if (primaryTagValue == "heath" || primaryTagValue == "grassland") {
                priority = 21
                coeff_G = 1.0
            }
            if (primaryTagValue == "rock") {
                priority = 13
                coeff_G = 0.1
            }
            if (primaryTagValue == "sand") {
                priority = 14
                coeff_G = 0.2
            }
            if (primaryTagValue == "scree") {
                priority = 17
                coeff_G = 0.5
            }
            if (primaryTagValue == "scrub") {
                priority = 19
                coeff_G = 0.7
            }
            if (primaryTagValue == "shingle") {
                priority = 15
                coeff_G = 0.3
            }
            if (primaryTagValue == "water") {
                priority = 7
                coeff_G = 0.3
            }
            if (primaryTagValue == "wood") {
                priority = 21
                coeff_G = 1.0
            }
        }
        if (primaryTagKey == "water") {
            if (["pond","lake","reservoir","river","wastewater","canal","oxbow",
                 "salt","lagoon","yes","ditch","salt_pool","tidal","stream",
                 "fishpond","riverbank","pool","lock","natural","shallow",
                 "salt_pond","lake;pond","marsh","well","reflecting_pool",
                 "fountain","stream;river","not_deep"].contains(primaryTagValue)) {
                priority = 7
                coeff_G = 0.3
            }
        }
        if (primaryTagKey == "waterway") {
            if (["stream","riverbank","canal","artificial"].contains(primaryTagValue)) {
                priority = 7
                coeff_G = 0.3
            }
        }
    }

    void setGeom(Geometry geom) {
        this.geom = geom;
    }

}