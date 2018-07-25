/* Copyright (c) 2013 - 2017 Boundless - http://boundlessgeo.com All rights reserved.
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package com.boundlessgeo.gsr.core.geometry;

import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import net.sf.json.JSONArray;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;
import net.sf.json.util.JSONBuilder;
import net.sf.json.util.JSONStringer;

import java.util.ArrayList;
import java.util.List;

/**
 * Abstract encoder for encoding {@link Geometry JTS Geometries} as
 * {@link com.boundlessgeo.gsr.core.geometry.Geometry GSR Geometries}
 *
 * Also includes a number of static utility methods used encode and decode envelopes and other geometries
 *
 * TODO: While this technically implements converter, the converter part doesn't actually do anything yet. Fix this.
 *
 * @param <T> The coordinate type. Must be a {@link Number}.
 */
public abstract class AbstractGeometryEncoder<T extends Number> implements Converter {

    @Override
    public void marshal(Object o, HierarchicalStreamWriter hierarchicalStreamWriter, MarshallingContext marshallingContext) {

    }

    @Override
    public Object unmarshal(HierarchicalStreamReader hierarchicalStreamReader, UnmarshallingContext unmarshallingContext) {
        return null;
    }

    /**
     * Converts a JTS Envelope to a GSR JSON string
     *
     * @param envelope the envelope
     * @return JSON string representation
     */
    public static String toJson(Envelope envelope) {
        JSONStringer json = new JSONStringer();
        envelopeToJson(envelope, json);
        return json.toString();
    }

    /**
     * Converts a JTS Envelope with a Spatial Reference to a GSR JSON string
     *
     * @param envelope the envelope
     * @param sr the spatial reference
     * @return JSON string representation
     */
    public static void referencedEnvelopeToJson(Envelope envelope, SpatialReference sr, JSONBuilder json) {
        json.object();
        envelopeCoordsToJson(envelope, json);
        json.key("spatialReference");
        SpatialReferenceEncoder.toJson(sr, json);
        json.endObject();
    }

    /**
     * Converts a JTS Envelope to a JSON object
     *
     * @param envelope the envelope
     * @param json The JSONbuilder to add the envelope to
     */
    public static void envelopeToJson(Envelope envelope, JSONBuilder json) {
        json.object();
        envelopeCoordsToJson(envelope, json);
        json.endObject();
    }

    /**
     * Converts a JTS Envelope to a set of x and y keys for an existing JSON object
     *
     * @param envelope the envelope
     * @param json The JSONbuilder to add the keys to
     */
    private static void envelopeCoordsToJson(Envelope envelope, JSONBuilder json) {
        json
          .key("xmin").value(envelope.getMinX())
          .key("ymin").value(envelope.getMinY())
          .key("xmax").value(envelope.getMaxX())
          .key("ymax").value(envelope.getMaxY());
    }

    /**
     * Converts a GeoTools {@link Geometry} to a GSR {@link com.boundlessgeo.gsr.core.geometry.Geometry}
     *
     * @param geom The Geometry to convert
     * @param spatialReference The spatialReference of geom.
     * @return a {@link com.boundlessgeo.gsr.core.geometry.Geometry} or {@link GeometryArray}
     */
    public com.boundlessgeo.gsr.core.geometry.Geometry toRepresentation(
        Geometry geom, SpatialReference spatialReference) {
        // Implementation notes.

        // We have only directly provided support for the
        // JTS geometry types that most closely map to those defined in the
        // GeoServices REST API spec. In the future we will need to deal with
        // the remaining JTS geometry types - there's some design work needed to
        // figure out a good tradeoff of information loss (for example, the spec
        // doesn't distinguish between a linestring and a multilinestring) and
        // generality.

        if (geom instanceof com.vividsolutions.jts.geom.Point) {
            com.vividsolutions.jts.geom.Point p = (com.vividsolutions.jts.geom.Point) geom;
            T[] coords = embeddedPoint(p);

            return new Point(coords[0], coords[1], spatialReference);
        } else if (geom instanceof com.vividsolutions.jts.geom.MultiPoint) {
            com.vividsolutions.jts.geom.MultiPoint mpoint = (com.vividsolutions.jts.geom.MultiPoint) geom;
            List<T[]> points = new ArrayList<>();
            for (int i = 0; i < mpoint.getNumPoints(); i++) {
                points.add(embeddedPoint((com.vividsolutions.jts.geom.Point) mpoint.getGeometryN(i)));
            }
            return new Multipoint(points.toArray(new Number[points.size()][]), spatialReference);
        } else if (geom instanceof com.vividsolutions.jts.geom.LineString) {
            com.vividsolutions.jts.geom.LineString line = (com.vividsolutions.jts.geom.LineString) geom;
            return new Polyline(new Number[][][]{embeddedLineString(line)}, spatialReference);
        } else if (geom instanceof com.vividsolutions.jts.geom.MultiLineString) {
            com.vividsolutions.jts.geom.MultiLineString mline = (com.vividsolutions.jts.geom.MultiLineString) geom;
            List<T[][]> paths = new ArrayList<>();

            for (int i = 0; i < mline.getNumGeometries(); i++) {
                com.vividsolutions.jts.geom.LineString line = (com.vividsolutions.jts.geom.LineString) mline.getGeometryN(i);
                paths.add(embeddedLineString(line));
            }
            return new Polyline(paths.toArray(new Number[paths.size()][][]), spatialReference);

        } else if (geom instanceof com.vividsolutions.jts.geom.Polygon) {
            com.vividsolutions.jts.geom.Polygon polygon = (com.vividsolutions.jts.geom.Polygon) geom;
            List<T[][]> rings = new ArrayList<>();
            rings.add(embeddedLineString(polygon.getExteriorRing()));

            for (int i = 0; i < polygon.getNumInteriorRing(); i++) {
                rings.add(embeddedLineString(polygon.getInteriorRingN(i)));
            }
            return new Polygon(rings.toArray(new Number[rings.size()][][]), spatialReference);
        } else if (geom instanceof com.vividsolutions.jts.geom.MultiPolygon) {
            com.vividsolutions.jts.geom.MultiPolygon mpoly = (com.vividsolutions.jts.geom.MultiPolygon) geom;
            List<T[][]> rings = new ArrayList<>();

            for (int i = 0; i < mpoly.getNumGeometries(); i++) {
                //for now, assume these are all polygons. that SHOULD be the case anyway
                com.vividsolutions.jts.geom.Polygon geometryN = (com.vividsolutions.jts.geom.Polygon) mpoly
                    .getGeometryN(i);

                //encode the outer ring
                rings.add(embeddedLineString(geometryN.getExteriorRing()));

                for (int j = 0; j < geometryN.getNumInteriorRing(); j++) {
                    rings.add(embeddedLineString(geometryN.getInteriorRingN(j)));
                }
            }

            return new Polygon(rings.toArray(new Number[rings.size()][][]), spatialReference);

        } else if (geom instanceof com.vividsolutions.jts.geom.GeometryCollection) {
            com.vividsolutions.jts.geom.GeometryCollection collection = (com.vividsolutions.jts.geom.GeometryCollection) geom;
            GeometryTypeEnum geometryType = determineGeometryType(collection);
            List<com.boundlessgeo.gsr.core.geometry.Geometry> geometries = new ArrayList<>();
            for (int i = 0; i < collection.getNumGeometries(); i++) {
                geometries.add(toRepresentation(collection.getGeometryN(i), spatialReference));
            }
            return new GeometryArray(geometryType, geometries.toArray(new com.boundlessgeo.gsr.core.geometry.Geometry[geometries.size()]), spatialReference);
        } else {
          throw new IllegalStateException("Geometry encoding not yet supported for " + geom.getGeometryType());
        }
    }

    /**
     * Encodes a coordinate.
     *
     * All methods which encode a feature delegate to this method; implementations of {@link AbstractGeometryEncoder}
     * should override it with the applicable implementation.
     *
     * @param coord The Coordinate to encode
     * @return The coordinate as an array.
     */
    protected abstract T[] embeddedCoordinate(com.vividsolutions.jts.geom.Coordinate coord);

    /**
     * Called immediately before a new feature is encoded.
     * Used by subclasses for to handle certain special cases.
     */
    protected abstract void startFeature();

    /**
     * Called immediately after a feature is encoded.
     * Used by subclasses for to handle certain special cases.
     */
    protected abstract void endFeature();

    /**
     * Encodes a point feature
     *
     * @param point the point to encode.
     * @return the encoded point
     */
    protected T[] embeddedPoint(com.vividsolutions.jts.geom.Point point) {
        startFeature();
        T [] p = embeddedCoordinate(point.getCoordinate());
        endFeature();
        return p;
    }

    /**
     * Encodes a linestring feature (this may be a line feature, or one ring of a polygon feature).
     *
     * @param line the linestring to encode
     * @return the encoded linestring
     */
    protected T[][] embeddedLineString(com.vividsolutions.jts.geom.LineString line) {
        List<T[]> points = new ArrayList<>();
        startFeature();
        for (com.vividsolutions.jts.geom.Coordinate c : line.getCoordinates()) {
            points.add(embeddedCoordinate(c));
        }
        endFeature();
        return (T[][])points.toArray(new Number[points.size()][]);
    }

    /**
     * Determines the geometry type of geometries in a geometry collection.
     *
     * @param collection The geometry collection
     * @return The type of all geometries in the collection, or {@link GeometryTypeEnum#POINT} if the collection is
     *         empty.
     * @throws IllegalArgumentException if the gemetry collection contains multiple geometry types
     */
    protected static GeometryTypeEnum determineGeometryType(com.vividsolutions.jts.geom.GeometryCollection collection) {
        if (collection.getNumGeometries() == 0) {
            return GeometryTypeEnum.POINT;
        } else {
            String type = collection.getGeometryN(0).getGeometryType();
            for (int i = 0; i < collection.getNumGeometries(); i++) {
                Geometry g = collection.getGeometryN(i);
                if (! type.equals(g.getGeometryType())) {
                    throw new IllegalArgumentException("GeoServices REST API Specification does not support mixed geometry types in geometry collections. (Core 9.8)");
                }
            }
            return GeometryTypeEnum.forJTSClass(collection.getGeometryN(0).getClass());
        }
    }

    protected static com.vividsolutions.jts.geom.Coordinate jsonArrayToCoordinate(JSONArray array) {
        if (array.size() != 2) {
            throw new JSONException("Coordinate JSON must be an array with exactly two elements");
        }
        return new com.vividsolutions.jts.geom.Coordinate(array.getDouble(0), array.getDouble(1));
    }

    protected static com.vividsolutions.jts.geom.Coordinate[] jsonArrayToCoordinates(JSONArray array) {
        com.vividsolutions.jts.geom.Coordinate[] coordinates = new com.vividsolutions.jts.geom.Coordinate[array.size()];
        for (int i = 0; i < array.size(); i++) {
            coordinates[i] = jsonArrayToCoordinate(array.getJSONArray(i));
        }
        return coordinates;
    }

    /**
     * Converts a JSON object to an envelope
     *
     * @param json the json object representing an envelope
     * @return the envelope
     */
    public static Envelope jsonToEnvelope(net.sf.json.JSON json) {
        if (!(json instanceof JSONObject)) {
            throw new JSONException("An envelope must be encoded as a JSON Object");
        }
        JSONObject obj = (JSONObject) json;
        double minx = obj.getDouble("xmin");
        double miny = obj.getDouble("ymin");
        double maxx = obj.getDouble("xmax");
        double maxy = obj.getDouble("ymax");
        return new Envelope(minx, maxx, miny, maxy);
    }

    /**
     * Converts a JSON object to a geometry
     *
     * @param json the json object representing a geometry
     * @return the geometry
     */
    public static Geometry jsonToGeometry(net.sf.json.JSON json) {
        if (!(json instanceof JSONObject)) {
            throw new JSONException("A geometry must be encoded as a JSON Object");
        }
        JSONObject obj = (JSONObject) json;
        GeometryFactory geometries = new GeometryFactory();

        if (obj.containsKey("x") && obj.containsKey("y")) {
            double x = obj.getDouble("x");
            double y = obj.getDouble("y");
            return geometries.createPoint(new com.vividsolutions.jts.geom.Coordinate(x, y));
        } else if (obj.containsKey("points")) {
            JSONArray points = obj.getJSONArray("points");
            return geometries.createMultiPoint(jsonArrayToCoordinates(points));
        } else if (obj.containsKey("paths")) {
            JSONArray paths = obj.getJSONArray("paths");
            com.vividsolutions.jts.geom.LineString[] lines = new com.vividsolutions.jts.geom.LineString[paths.size()];
            for (int i = 0; i < paths.size(); i++) {
                com.vividsolutions.jts.geom.Coordinate[] coords = jsonArrayToCoordinates(paths.getJSONArray(i));
                lines[i] = geometries.createLineString(coords);
            }
            return geometries.createMultiLineString(lines);
        } else if (obj.containsKey("rings")) {
            JSONArray rings = obj.getJSONArray("rings");
            if (rings.size() < 1) {
                throw new JSONException("Polygon must have at least one ring");
            }
            com.vividsolutions.jts.geom.LinearRing shell =
                    geometries.createLinearRing(jsonArrayToCoordinates(rings.getJSONArray(0)));
            com.vividsolutions.jts.geom.LinearRing[] holes = new com.vividsolutions.jts.geom.LinearRing[rings.size() - 1];
            for (int i = 1; i < rings.size(); i++) {
                holes[i - 1] = geometries.createLinearRing(jsonArrayToCoordinates(rings.getJSONArray(i)));
            }
            return geometries.createPolygon(shell, holes);
        } else if (obj.containsKey("geometries")) {
            JSONArray nestedGeometries = obj.getJSONArray("geometries");
            Geometry[] parsedGeometries = new Geometry[nestedGeometries.size()];
            for (int i = 0; i < nestedGeometries.size(); i++) {
                parsedGeometries[i] = jsonToGeometry(nestedGeometries.getJSONObject(i));
            }
            return geometries.createGeometryCollection(parsedGeometries);
        } else {
            throw new JSONException("Could not parse Geometry from " + json);
        }
    }

    @Override
    public boolean canConvert(Class clazz) {
        return Geometry.class.isAssignableFrom(clazz) ||
                Envelope.class.isAssignableFrom(clazz);
    }
}
