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

import static org.apache.jena.atlas.lib.Lib.concatPaths;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static uk.gov.dbt.ndtp.LibTestsSAG.tokenHeader;
import static uk.gov.dbt.ndtp.LibTestsSAG.tokenHeaderValue;

import java.util.Map;

import uk.gov.dbt.ndtp.core.FKProcessorSAG;
import uk.gov.dbt.ndtp.core.MainSecureAgentGraph;
import uk.gov.dbt.ndtp.jena.abac.AttributeValueSet;
import uk.gov.dbt.ndtp.jena.abac.SysABAC;
import uk.gov.dbt.ndtp.jena.abac.attributes.Attribute;
import uk.gov.dbt.ndtp.jena.abac.attributes.ValueTerm;
import uk.gov.dbt.ndtp.jena.abac.lib.Attributes;
import uk.gov.dbt.ndtp.jena.abac.lib.AttributesStore;
import uk.gov.dbt.ndtp.jena.abac.lib.DatasetGraphABAC;
import uk.gov.dbt.ndtp.platform.play.FKProcessorSender;
import uk.gov.dbt.ndtp.secure.agent.configuration.Configurator;
import org.apache.jena.atlas.lib.FileOps;
import org.apache.jena.fuseki.kafka.FKProcessor;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.fuseki.system.FusekiLogging;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFWriter;
import org.apache.jena.riot.web.HttpNames;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.exec.RowSet;
import org.apache.jena.sparql.exec.RowSetOps;
import org.apache.jena.sparql.exec.RowSetRewindable;
import org.apache.jena.sparql.exec.http.QueryExecHTTPBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Testing of the persistent setup
 */
public class TestPersistentSetup {

    private static String testArea = "target/databases/";

    @BeforeAll public static void beforeAll() throws Exception {
        LibTestsSAG.setupAuthentication();
        LibTestsSAG.disableInitialCompaction();
        FusekiLogging.setLogging();
        FileOps.ensureDir(testArea);
    }

    @BeforeEach public void beforeEach() {
        FileOps.clearAll(testArea);
    }

    @AfterAll public static void afterAll() {
        FileOps.clearAll(testArea);
        Configurator.reset();
    }

    @Test void persistentSetup() {

        //CxtABAC.systemTrace(Track.DEBUG);
        final String DIR = "src/test/files/";
        final String CONFIG = concatPaths(DIR, "config-persistent.ttl");

        final String FILES = "src/test/files/Data";
        final String TOPIC = "knowledge";

        // Load the attributes: this copy is only used for printing information
        AttributesStore attributeStore = Attributes.readAttributesStore(concatPaths(DIR, "attribute-store.ttl"), null);

        FusekiServer server = MainSecureAgentGraph.build("--port=0", "--config", CONFIG);

        // ---- Internal setup
        // -- DatasetGraphABAC and base dataset
        final DatasetGraph dsg = server.getDataAccessPointRegistry().get("/knowledge").getDataService().getDataset();
        final DatasetGraphABAC dsgz =(DatasetGraphABAC)dsg;
        final DatasetGraph dsgBase = dsgz.getBase();

        // Add connectors in such a way we can manually inject requests.
        FKProcessor fkProcessor = new FKProcessorSAG(dsgz,  "http://knowledge/kafka", server);

        try {
            server.start();
            FKProcessorSender procSender = FKProcessorSender.create(dsgz, TOPIC, server);
            int port = server.getPort();
            String queryURL = server.datasetURL("/knowledge")+"/sparql";

            // Batch ???

            // Send files to the FKProcessor
            procSender
                .send(concatPaths(FILES, "data-test-1.ttl"),
                      Map.of(SysABAC.H_SECURITY_LABEL, "clearance=ordinary", HttpNames.hContentType, Lang.TTL.getHeaderString()))
                .send(concatPaths(FILES, "data-test-2.ttl"),
                      Map.of(SysABAC.H_SECURITY_LABEL, "clearance=secret", HttpNames.hContentType, Lang.TTL.getHeaderString()));

            if ( false )
                dump(dsgz);

            // User 'user1' can see "ordinary", "secret"
            query(queryURL, 2, "user1",  "SELECT * { ?s ?p ?o}", attributeStore);
            // User 'public' - "no label" not present.
            query(queryURL, 0, "public", "SELECT * { ?s ?p ?o}", attributeStore);

            // Send more files to the FKProcessor
            procSender
                .send(concatPaths(FILES, "data-test-3.ttl"),
                      Map.of(SysABAC.H_SECURITY_LABEL, "clearance=top-secret", HttpNames.hContentType, Lang.TTL.getHeaderString()))
                .send(concatPaths(FILES, "data-test-4.ttl"),
                      Map.of(SysABAC.H_SECURITY_LABEL, "*", HttpNames.hContentType, Lang.TTL.getHeaderString()));

            // User 'user1' can see "no label", "ordinary", "secret"
            query(queryURL, 3, "user1",  "SELECT * { ?s ?p ?o}", attributeStore);
            // User 'public' can see "no label".
            query(queryURL, 1, "public", "SELECT * { ?s ?p ?o}", attributeStore);


        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
        finally {
            server.stop();
            server = null;
        }
    }

    private void dump(DatasetGraphABAC dsgz) {
        final DatasetGraph dsgBase = dsgz.getBase();
        dsgz.executeRead(()->{
            System.out.println("== Data");
            RDFWriter.source(dsgBase.getDefaultGraph()).lang(Lang.TTL).output(System.out);
//          System.out.println("== Labels");
//          // Assumes LabelsStore.forEach implemented.
//          RDFWriter.source(dsgz.labelsStore().asGraph()).lang(Lang.TTL).output(System.out);
            System.out.println("== User attributes");
            dsgz.attributesStore().users().forEach(u -> {
                AttributeValueSet a = dsgz.attributesStore().attributes(u);
                System.out.printf("%-10s %s\n", u, a);
            });
            System.out.println("==");
        });
    }

    private static void query(String URL, int expectedCount, String user, String queryString, AttributesStore attributeStore) {
        if ( false ) {
            System.out.printf("User = %s\n", user);
            printAttrs(user, attributeStore);
        }

        String bearerToken = LibTestsSAG.tokenForUser(user);
        tokenHeader();
        tokenHeaderValue(bearerToken);

        // == ABAC Query
        RowSet rs1 = QueryExecHTTPBuilder.service(URL)
                .query(queryString)
                .httpHeader(tokenHeader(), tokenHeaderValue(bearerToken))
                .select();

        RowSetRewindable rs2 = rs1.rewindable();
        //RowSetOps.out(rs2);
        rs2.reset();

        long actualCount = RowSetOps.count(rs2);
        assertEquals(expectedCount, actualCount, "ABAC Query count");
    }

    private static void printAttrs(String user, AttributesStore attributeStore) {
        AttributeValueSet avs = attributeStore.attributes(user);
        if ( avs == null ) {
            System.out.printf("    no such user\n");
            return;
        }
        if ( avs.isEmpty() ) {
            System.out.printf("    no attributes\n");
            return;
        }

        attributeStore.attributes(user).attributeValues((attributeValue) -> {
            Attribute a = attributeValue.attribute();
            ValueTerm vt = attributeValue.value();
            System.out.printf("    %s %s", a, vt);
            if ( attributeStore.hasHierarchy(a) ) {
                System.out.printf(" --");
                for ( ValueTerm hvt : attributeStore.getHierarchy(a).values() ) {
                    System.out.printf(" %s", hvt);
                    if ( hvt.equals(vt) )
                        break;
                }
            }
            System.out.println();
        });
    }
}
