/* Copyright (c) 2013 - 2017 Boundless - http://boundlessgeo.com All rights reserved.
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package com.boundlessgeo.gsr.resource;

import com.boundlessgeo.gsr.JsonSchemaTest;
import org.junit.Test;

import static org.junit.Assert.*;

public class FeatureRootResourceTest extends ResourceTest {
    private String query(String service, String params) {
        return baseURL + service + "/FeatureServer" + params;
    }

    @Test
    public void testBasicQuery() throws Exception {
        String result = getAsString(query("cite", "?f=json"));
        assertFalse(result.isEmpty());
        // TODO: Can't validate since ids are not integers.
        assertTrue(result + " ;Root resource validates", JsonSchemaTest.validateJSON(result, "/gsr-feature/1.0/root.json"));
    }
}
