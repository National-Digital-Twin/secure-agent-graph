// SPDX-License-Identifier: Apache-2.0
// Originally developed by Telicent Ltd.; subsequently adapted, enhanced, and maintained by the National Digital Twin Programme.
/*
 *  Copyright (c) Telicent Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
/*
 *  Modifications made by the National Digital Twin Programme (NDTP)
 *  © Crown Copyright 2025. This work has been developed by the National Digital Twin Programme
 *  and is legally attributed to the Department for Business and Trade (UK) as the governing entity.
 */

package uk.gov.dbt.ndtp;

import uk.gov.dbt.ndtp.core.SecureAgentGraph;
import uk.gov.dbt.ndtp.jena.abac.lib.Attributes;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.fuseki.system.FusekiLogging;
import org.apache.jena.http.HttpEnv;
import org.apache.jena.http.HttpLib;
import org.apache.jena.riot.web.HttpNames;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.apache.jena.graph.Graph.emptyGraph;
import static org.apache.jena.http.HttpLib.execute;
import static org.apache.jena.http.HttpLib.toRequestURI;
import static org.apache.jena.riot.web.HttpNames.METHOD_OPTIONS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TestCORSHeadersSAG {

    private static final String EXPECTED_ALLOWED_HEADERS = "X-Requested-With,Content-Type,Accept,Origin,Last-Modified,Authorization,Custom-Header";

    private static FusekiServer server;

    @BeforeAll
    static void createAndSetupFusekiServer() throws Exception {
        LibTestsSAG.setupAuthentication();
        FusekiLogging.setLogging();
        Attributes.buildStore(emptyGraph);
        server = SecureAgentGraph.construct("--port=0", "--empty", "--CORS=src/test/files/cors.properties").start();
    }

    @AfterAll
    static void stopServer() throws Exception {
        if (server != null) {
            server.stop();
        }
        LibTestsSAG.teardownAuthentication();
    }

    @Test
    void jena_access_control_allow_headers() {
        // given
        String originalHeaders = "X-Requested-With, Content-Type, Accept, Origin, Last-Modified, Authorization";
        // when
        HttpResponse<InputStream> response = makeOptionsCallWithGivenAllowHeaders(originalHeaders);
        // then
        assertEquals(200, response.statusCode());
        String actualAllowedHeaders = HttpLib.responseHeader(response, HttpNames.hAccessControlAllowHeaders);
        assertEquals(EXPECTED_ALLOWED_HEADERS, actualAllowedHeaders, "Headers in request not as expected");
    }

    @Test
    void sag_access_control_allow_header() {
        // given
        String sagHeaders = "Content-Type, Custom-Header";
        // when
        HttpResponse<InputStream> response = makeOptionsCallWithGivenAllowHeaders(sagHeaders);
        // then
        assertEquals(200, response.statusCode());
        String actualAllowedHeaders = HttpLib.responseHeader(response, HttpNames.hAccessControlAllowHeaders);
        assertEquals(EXPECTED_ALLOWED_HEADERS, actualAllowedHeaders, "Headers in request not as expected");
    }


    @Test
    void invalid_access_control_allow_header() {
        // given
        String sagHeaders = "Content-Type, Rubbish-Header";
        // when
        HttpResponse<InputStream> response = makeOptionsCallWithGivenAllowHeaders(sagHeaders);
        // then
        assertEquals(200, response.statusCode());
        String actualAllowedHeaders = HttpLib.responseHeader(response, HttpNames.hAccessControlAllowHeaders);
        assertNull(actualAllowedHeaders, "No headers expected given invalid request");
    }

    private HttpResponse<InputStream> makeOptionsCallWithGivenAllowHeaders(String accessControlRequestHeaders) {
        HttpRequest.Builder builder =
                HttpLib.requestBuilderFor(server.serverURL())
                       .uri(toRequestURI(server.serverURL()))
                       .method(METHOD_OPTIONS, HttpRequest.BodyPublishers.noBody())
                        .headers("Access-Control-Request-Method", "POST",
                        "Access-Control-Request-Headers",accessControlRequestHeaders,
                        "Origin", "http://localhost:5173")
                ;
        return execute(HttpEnv.getDftHttpClient(), builder.build());
    }
}
