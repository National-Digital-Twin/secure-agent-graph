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

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Map;

/** From jena-fuseki-kafka */
public class MockKafka {

    private final ConfluentKafkaContainer kafkaContainer;
    private final String bootstrap;
    private final AdminClient admin;

    public MockKafka() {
        kafkaContainer = new ConfluentKafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.1"));
        kafkaContainer.start();
        bootstrap = kafkaContainer.getBootstrapServers();
        admin = AdminClient.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, getKafkaBrokers()));
    }

    private String getKafkaBrokers() {
        Integer mappedPort = kafkaContainer.getFirstMappedPort();
        return String.format("%s:%d", "localhost", mappedPort);
    }

    public String getServer() { return bootstrap; }

    public void createTopic(String topic) {
        NewTopic newTopic = new NewTopic(topic, 1, (short) 1);
        admin.createTopics(List.of(newTopic));
    }

    public void stop() {
        kafkaContainer.stop();
    }
}
