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

import uk.gov.dbt.ndtp.core.FMod_RequestIDFilter;
import uk.gov.dbt.ndtp.core.SecureAgentGraph;
import uk.gov.dbt.ndtp.secure.agent.configuration.Configurator;
import uk.gov.dbt.ndtp.secure.agent.configuration.sources.PropertiesSource;
import uk.gov.dbt.ndtp.secure.agent.configuration.auth.AuthConstants;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.http.HttpEnv;
import org.apache.jena.sparql.core.DatasetGraphFactory;
import org.junit.Assert;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static uk.gov.dbt.ndtp.LibTestsSAG.disableInitialCompaction;

public class TestRequestIDFilter {

    private static final Properties AUTH_DISABLED_PROPERTIES = new Properties();
    private static final PropertiesSource AUTH_DISABLED_SOURCE = new PropertiesSource(AUTH_DISABLED_PROPERTIES);

    private static final String DATASET_NAME = "/ds";
    private static final String REQUEST_ID = "Request-ID";
    private static final String EXPECTED_REQUEST_ID = UUID.randomUUID().toString();

    private static FusekiServer server;
    private static URI uri;

    @BeforeAll
    static void createAndSetupFusekiServer(){
        AUTH_DISABLED_PROPERTIES.put(AuthConstants.ENV_JWKS_URL, AuthConstants.AUTH_DISABLED);
        Configurator.setSingleSource(AUTH_DISABLED_SOURCE);
        disableInitialCompaction();
        server = SecureAgentGraph.secureAgentGraphBuilder()
                                             .port(0)
                                             .add(DATASET_NAME, DatasetGraphFactory.empty())
                                             .build()
                                             .start();

        uri = URI.create(server.datasetURL(DATASET_NAME));
    }

    @AfterAll
    static void stopFusekiServer(){
        if (null != server)
            server.stop();
    }

    @Test
    void make_request_with_existing_id() throws IOException, InterruptedException {
        // given
        HttpRequest request = HttpRequest.newBuilder()
                                         .uri(uri)
                                         .headers(REQUEST_ID, EXPECTED_REQUEST_ID)
                                         .GET()
                                         .build();
        // when
        HttpResponse<Void> response = HttpEnv.getDftHttpClient().send(request, HttpResponse.BodyHandlers.discarding());
        // then
        assertEquals(200, response.statusCode());
        Optional<String> optionalHeader =  response.headers().firstValue(REQUEST_ID);
        assertTrue(optionalHeader.isPresent());
        assertTrue(optionalHeader.get().startsWith(EXPECTED_REQUEST_ID));
    }

    @Test
    void make_identical_requests_with_existing_id() throws IOException, InterruptedException {
        // given
        HttpRequest request = HttpRequest.newBuilder()
                                         .uri(uri)
                                         .headers(REQUEST_ID, EXPECTED_REQUEST_ID)
                                         .GET()
                                         .build();
        // when
        HttpResponse<Void> response = HttpEnv.getDftHttpClient().send(request, HttpResponse.BodyHandlers.discarding());
        HttpResponse<Void> nextResponse = HttpEnv.getDftHttpClient().send(request, HttpResponse.BodyHandlers.discarding());
        // then
        assertEquals(200, response.statusCode());
        Optional<String> optionalHeader =  response.headers().firstValue(REQUEST_ID);
        assertTrue(optionalHeader.isPresent());
        String firstResponseRequestId = optionalHeader.get();
        assertTrue(firstResponseRequestId.startsWith(EXPECTED_REQUEST_ID));


        assertEquals(200, response.statusCode());
        Optional<String> nextOptionalHeader =  nextResponse.headers().firstValue(REQUEST_ID);
        assertTrue(nextOptionalHeader.isPresent());
        String secondResponseRequestId = nextOptionalHeader.get();
        assertTrue(secondResponseRequestId.startsWith(EXPECTED_REQUEST_ID));

        assertNotEquals(firstResponseRequestId, secondResponseRequestId);

    }

    @Test
    void make_request_without_id() throws IOException, InterruptedException {
        // given
        HttpRequest request = HttpRequest.newBuilder()
                                         .uri(uri)
                                         .GET()
                                         .build();
        // when
        HttpResponse<Void> response = HttpEnv.getDftHttpClient().send(request, HttpResponse.BodyHandlers.discarding());
        // then
        assertEquals(200, response.statusCode());
        Optional<String> optionalHeader =  response.headers().firstValue(REQUEST_ID);
        assertTrue(optionalHeader.isPresent());
        String responseRequestId = optionalHeader.get();
        assertFalse(responseRequestId.startsWith(EXPECTED_REQUEST_ID)); // It'll be randomly created
    }

    @Test
    void make_request_with_existing_id_too_long() throws IOException, InterruptedException {
        // given
        String randomID = UUID.randomUUID().toString();
        String longRequestId = randomID + EXPECTED_REQUEST_ID;

        HttpRequest request = HttpRequest.newBuilder()
                                         .uri(uri)
                                         .headers(REQUEST_ID, longRequestId)
                                         .GET()
                                         .build();
        // when
        HttpResponse<Void> response = HttpEnv.getDftHttpClient().send(request, HttpResponse.BodyHandlers.discarding());
        // then
        assertEquals(200, response.statusCode());
        Optional<String> optionalHeader =  response.headers().firstValue(REQUEST_ID);
        assertTrue(optionalHeader.isPresent());
        String responseRequestId = optionalHeader.get();
        assertFalse(responseRequestId.contains(EXPECTED_REQUEST_ID));
        assertTrue(responseRequestId.startsWith(randomID));
    }

    @Test
    void name_happyPath() {
        // given
        String expected = "Request ID Capture ";
        FMod_RequestIDFilter fModRequestIDFilter = new FMod_RequestIDFilter();
        // when
        String actual = fModRequestIDFilter.name();
        // then
        Assert.assertEquals(expected, actual);
    }

    @Test
    void test_isBlank() {
        // given
        // when
        // then
        assertFalse(FMod_RequestIDFilter.isNotBlank("  "));
    }
}
