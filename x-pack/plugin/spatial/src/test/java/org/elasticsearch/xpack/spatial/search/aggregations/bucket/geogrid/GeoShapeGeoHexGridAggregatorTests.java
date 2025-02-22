/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.spatial.search.aggregations.bucket.geogrid;

import org.apache.lucene.document.SortedSetDocValuesField;
import org.apache.lucene.geo.LatLonGeometry;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.util.BytesRef;
import org.elasticsearch.common.geo.GeoBoundingBox;
import org.elasticsearch.geo.GeometryTestUtils;
import org.elasticsearch.geometry.Point;
import org.elasticsearch.h3.H3;
import org.elasticsearch.search.aggregations.bucket.geogrid.GeoGridAggregationBuilder;
import org.elasticsearch.xpack.spatial.common.H3CartesianUtil;
import org.elasticsearch.xpack.spatial.index.fielddata.GeoRelation;
import org.elasticsearch.xpack.spatial.index.fielddata.GeoShapeValues;
import org.elasticsearch.xpack.spatial.util.GeoTestUtils;

import java.io.IOException;
import java.util.Collections;

public class GeoShapeGeoHexGridAggregatorTests extends GeoShapeGeoGridTestCase<InternalGeoHexGridBucket> {
    @Override
    protected int randomPrecision() {
        return randomIntBetween(0, H3.MAX_H3_RES);
    }

    @Override
    protected String hashAsString(double lng, double lat, int precision) {
        // TODO: In theory we can have more than one hash per point?
        final long h3 = H3.geoToH3(lat, lng, precision);
        if (LatLonGeometry.create(H3CartesianUtil.getLatLonGeometry(h3)).contains(lng, lat)) {
            return H3.h3ToString(h3);
        }
        for (long n : H3.hexRing(h3)) {
            if (LatLonGeometry.create(H3CartesianUtil.getLatLonGeometry(n)).contains(lng, lat)) {
                return H3.h3ToString(n);
            }
        }
        fail("Could not find valid h3 bin");
        return null;
    }

    @Override
    protected Point randomPoint() {
        return GeometryTestUtils.randomPoint();
    }

    @Override
    protected GeoBoundingBox randomBBox() {
        return GeoTestUtils.randomBBox();
    }

    @Override
    protected boolean intersects(double lng, double lat, int precision, GeoShapeValues.GeoShapeValue value) throws IOException {
        return value.relate(new org.apache.lucene.geo.Point(lat, lng)) != GeoRelation.QUERY_DISJOINT;
    }

    @Override
    protected boolean intersectsBounds(double lng, double lat, int precision, GeoBoundingBox box) {
        final BoundedGeoHexGridTiler tiler = new BoundedGeoHexGridTiler(precision, box);
        return tiler.h3IntersectsBounds(H3.stringToH3(hashAsString(lng, lat, precision)));
    }

    @Override
    protected GeoGridAggregationBuilder createBuilder(String name) {
        return new GeoHexGridAggregationBuilder(name);
    }

    @Override
    public void testMappedMissingGeoShape() throws IOException {
        final String lineString = "LINESTRING (30 10, 10 30, 40 40)";
        final GeoGridAggregationBuilder builder = createBuilder("_name").field(FIELD_NAME).missing(lineString);
        testCase(
            new MatchAllDocsQuery(),
            1,
            null,
            iw -> { iw.addDocument(Collections.singleton(new SortedSetDocValuesField("string", new BytesRef("a")))); },
            geoGrid -> { assertEquals(8, geoGrid.getBuckets().size()); },
            builder
        );
    }
}
