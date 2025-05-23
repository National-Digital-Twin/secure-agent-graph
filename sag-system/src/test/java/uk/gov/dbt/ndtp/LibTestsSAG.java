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

import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import uk.gov.dbt.ndtp.core.FMod_InitialCompaction;
import uk.gov.dbt.ndtp.servlet.auth.jwt.JwtHttpConstants;
import uk.gov.dbt.ndtp.servlet.auth.jwt.verifier.aws.AwsConstants;
import uk.gov.dbt.ndtp.servlet.auth.jwt.verifier.aws.AwsElbKeyUrlRegistry;
import uk.gov.dbt.ndtp.secure.agent.configuration.Configurator;
import uk.gov.dbt.ndtp.secure.agent.configuration.sources.PropertiesSource;
import org.apache.jena.sparql.exec.RowSet;
import org.apache.jena.sparql.exec.http.DSP;
import org.apache.jena.sparql.exec.http.QueryExecHTTPBuilder;
import org.slf4j.Logger;
import uk.gov.dbt.ndtp.secure.agent.configuration.auth.AuthConstants;

import static uk.gov.dbt.ndtp.backup.FMod_BackupData.ENABLE_BACKUPS;

public class LibTestsSAG {

    private static final Properties AUTH_DISABLED_PROPERTIES = new Properties();
    private static final PropertiesSource AUTH_DISABLED_SOURCE = new PropertiesSource(AUTH_DISABLED_PROPERTIES);
    private static final AtomicInteger KEY_SERVER_PORT = new AtomicInteger(12345);
    private static final String TEST_AWS_REGION = "test";
    private static MockKeyServer MOCK_KEY_SERVER;
    private static boolean AWS_MODE = false;
    public static String DEFAULT_AUTH_HEADER = JwtHttpConstants.HEADER_AUTHORIZATION;
    public static String DEFAULT_AUTH_PREFIX = JwtHttpConstants.AUTH_SCHEME_BEARER;

    static {
        AUTH_DISABLED_PROPERTIES.put(AuthConstants.ENV_JWKS_URL, AuthConstants.AUTH_DISABLED);
    }

    public static RowSet queryWithToken(String url, String queryString, String user) {
        if (user == null) {
            return queryNoToken(url, queryString);
        }
        String jwt = tokenForUser(user);
        RowSet rs1 = QueryExecHTTPBuilder.service(url)
                                         .query(queryString)
                                         .httpHeader(tokenHeader(), tokenHeaderValue(jwt))
                                         .select();
        return rs1;
    }

    public static RowSet queryNoToken(String url, String queryString) {
        RowSet rs1 = QueryExecHTTPBuilder.service(url)
                                         .query(queryString)
                                         .select();
        return rs1;
    }

    public static void uploadFile(String URL, String filename) {
        DSP.service(URL)
           .httpHeader(tokenHeader(), tokenHeaderValue(tokenForUser("admin")))
           .POST(filename);
    }

    public static String tokenHeader() {
        if (AWS_MODE) {
            return AwsConstants.HEADER_DATA;
        } else {
            return DEFAULT_AUTH_HEADER;
        }
    }

    public static String tokenHeaderValue(String token) {
        if (AWS_MODE) {
            return token;
        } else {
            return DEFAULT_AUTH_PREFIX + " " + token;
        }
    }

    /**
     * Creates a cryptographically signed JWT for the given user
     *
     * @param user User
     * @return Signed JWT compacted into the Base64 representation per the JWT specifications
     */
    public static String tokenForUser(String user) {
        String keyId = MOCK_KEY_SERVER.selectKeyId();
        return Jwts.builder()
                   .header().keyId(keyId).and()
                   .claim("email", user)
                   .signWith(MOCK_KEY_SERVER.getPrivateKey(keyId))
                   .compact();
    }

    public static JwtBuilder tokenBuilder(String user) {
        String keyId = MOCK_KEY_SERVER.selectKeyId();
        return Jwts.builder()
                   .header()
                   .keyId(keyId)
                   .and()
                   .claim("email", user)
                   .signWith(MOCK_KEY_SERVER.getPrivateKey(keyId));
    }

    /**
     * Tears down the mock key server
     *
     * @throws Exception Thrown if the server cannot be stopped
     */
    public static void teardownAuthentication() throws Exception {
        if (MOCK_KEY_SERVER != null) {
            MOCK_KEY_SERVER.stop();
            MOCK_KEY_SERVER = null;
        }
        AwsElbKeyUrlRegistry.reset();
        Configurator.setSingleSource(AUTH_DISABLED_SOURCE);
        AWS_MODE = false;
        DEFAULT_AUTH_HEADER = JwtHttpConstants.HEADER_AUTHORIZATION;
        DEFAULT_AUTH_PREFIX = JwtHttpConstants.AUTH_SCHEME_BEARER;
    }

    /**
     * Sets up a mock key server
     *
     * @throws Exception Thrown if the server cannot be started
     */
    public static void setupAuthentication() throws Exception {
        setupAuthentication(false);
    }

    /**
     * Sets up a mock key server
     *
     * @param awsMode Whether keys should be retrieved AWS ELB style
     * @throws Exception Thrown if the server cannot be started
     */
    public static void setupAuthentication(boolean awsMode) throws Exception {
        if (MOCK_KEY_SERVER != null) {
            MOCK_KEY_SERVER.stop();
        }
        MOCK_KEY_SERVER = new MockKeyServer(KEY_SERVER_PORT.getAndIncrement());
        MOCK_KEY_SERVER.start();

        AWS_MODE = awsMode;
        Properties properties = getAuthConfigurationProperties(awsMode);
        Configurator.setSingleSource(new PropertiesSource(properties));
    }

    public static Properties getAuthConfigurationProperties(boolean awsMode) {
        Properties properties = new Properties();
        if (awsMode) {
            AwsElbKeyUrlRegistry.register(TEST_AWS_REGION, MOCK_KEY_SERVER.getAwsElbUrlFormat());
            properties.put(AuthConstants.ENV_JWKS_URL, AuthConstants.AUTH_PREFIX_AWS + TEST_AWS_REGION);
        } else {
            properties.put(AuthConstants.ENV_JWKS_URL, MOCK_KEY_SERVER.getJwksUrl());
        }
        return properties;
    }

    // Fine-grained logging control.
    // See also LogCtl in jena-base for log4j implementation.

    /** Execute with a given logging level. */
    public static void withLevel(Logger logger, String execLevel, Runnable action) {
        CtlLogback.withLevel(logger, execLevel, action);
    }

    public static void disableInitialCompaction() {
        Properties properties = new Properties();
        properties.put(FMod_InitialCompaction.DISABLE_INITIAL_COMPACTION, "true");
        Configurator.addSource(new PropertiesSource(properties));
    }

    public static void enableBackups() {
        Properties properties = new Properties();
        properties.put(ENABLE_BACKUPS, "true");
        Configurator.addSource(new PropertiesSource(properties));
    }

    public static void disableBackups() {
        Properties properties = new Properties();
        properties.put(ENABLE_BACKUPS, "false");
        Configurator.addSource(new PropertiesSource(properties));
    }
}
