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

package uk.gov.dbt.ndtp.core;

import uk.gov.dbt.ndtp.jena.abac.lib.DatasetGraphABAC;
import uk.gov.dbt.ndtp.jena.abac.labels.LabelsStore;
import uk.gov.dbt.ndtp.jena.abac.labels.LabelsStoreRocksDB;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.io.FileUtils;
import org.apache.jena.atlas.lib.Timer;
import org.apache.jena.atlas.lib.Version;
import org.apache.jena.atlas.logging.FmtLog;
import org.apache.jena.fuseki.Fuseki;
import org.apache.jena.fuseki.kafka.FKS;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.fuseki.main.sys.FusekiAutoModule;
import org.apache.jena.fuseki.server.DataAccessPoint;
import org.apache.jena.fuseki.server.DataAccessPointRegistry;
import org.apache.jena.fuseki.server.DataService;
import org.apache.jena.fuseki.servlets.ServletOps;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.DatasetGraphWrapper;
import org.apache.jena.tdb2.DatabaseMgr;
import org.apache.jena.tdb2.store.DatasetGraphSwitchable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class FMod_InitialCompaction implements FusekiAutoModule {

    public static final Logger LOG = LoggerFactory.getLogger("uk.gov.dbt.ndtp.core.FMod_InitialCompaction");
    final Set<String> datasets = new HashSet<>();
    static final boolean DELETE_OLD = true;
    public static final String DISABLE_INITIAL_COMPACTION = "DISABLE_INITIAL_COMPACTION";
    private static final String VERSION = Version.versionForClass(FMod_InitialCompaction.class).orElse("<development>");
    static final Map<String, Long> sizes = new ConcurrentHashMap<>();

    @Override
    public String name() {
        return "Initial Compaction";
    }

    @Override
    public void prepare(FusekiServer.Builder builder, Set<String> names, Model configModel) {
        FmtLog.info(Fuseki.configLog, "%s Fuseki Module (%s)", name(), VERSION);
        this.datasets.addAll(names);
    }

    @Override
    public void configured(FusekiServer.Builder serverBuilder, DataAccessPointRegistry dapRegistry, Model configModel) {
        // Create a new dataset endpoint for compacting all datasets
        HttpServlet compactAllServlet = new HttpServlet() {
            @Override
            public void doPost(HttpServletRequest req, HttpServletResponse res) {
                try {
                    // Iterate over all registered datasets
                    for (DataAccessPoint dataAccessPoint : dapRegistry.accessPoints()) {
                        DataService dataService = dataAccessPoint.getDataService();
                        compactDatasetGraphDatabase(dataService.getDataset(), dataAccessPoint.getName());
                    }
                } catch (Exception e) {
                    FmtLog.error(Fuseki.configLog, "Error while compacting data points", e);
                    ServletOps.errorOccurred(e.getMessage());
                }
            }

        };
        serverBuilder.addServlet("/$/compactall", compactAllServlet);
    }

    @Override
    public void serverAfterStarting(FusekiServer server) {
        // Run after starting
        compactDatabases(server);
    }

    /**
     * Compacts the database
     *
     * @param server Server
     */
    private void compactDatabases(FusekiServer server) {
        for (String name : datasets) {
            Optional<DatasetGraph> optionalDatasetGraph = FKS.findDataset(server, name);
            if (optionalDatasetGraph.isPresent()) {
                compactDatasetGraphDatabase(optionalDatasetGraph.get(), name);
            } else {
                FmtLog.debug(LOG, "Compaction not required for %s as no graph", name);
            }
        }
    }

    /**
     * Carries out the actual compaction, provided the size of the TDB has changed since last time
     * @param datasetGraph Dataset Graph
     * @param name Name of dataset
     */
    private static void compactDatasetGraphDatabase(DatasetGraph datasetGraph, String name) {
        DatasetGraphSwitchable dsg = getTDB2(datasetGraph);
        if (dsg != null) {
            // See how big the database is, and whether it's size has changed
            // NB - Due to how the module is registered twice (see SecureAgentGraph) we'll get called twice, once
            //      before Kafka connectors are started, and again after they are started, this gives us two
            //      opportunities to compact stuff
            long sizeBefore = findDatabaseSize(dsg);
            if (sizes.containsKey(name)) {
                if (sizeBefore <= sizes.get(name)) {
                    FmtLog.info(LOG,
                            "[Compaction] Additional compaction not required for %s as it is already maximally compacted at %s (%d)",
                            name, humanReadableSize(sizeBefore), sizeBefore);
                    return;
                }
            }

            try {
                // Due to known issue - obtain a write lock prior to compaction.
                // If it fails, stop processing to avoid deadlock.

                if (!dsg.tryExclusiveMode(false)) {
                    FmtLog.info(LOG,
                            "[Compaction] Ignoring for %s due to potential deadlock operation", name);
                    return;
                }
                FmtLog.info(LOG, "[Compaction] >>>> Start compact %s, current size is %s (%d)", name,
                        humanReadableSize(sizeBefore), sizeBefore);
                Timer timer = new Timer();
                timer.startTimer();
                DatabaseMgr.compact(dsg, DELETE_OLD);
                long sizeAfter = findDatabaseSize(dsg);
                FmtLog.info(LOG, "[Compaction] <<<< Finish compact %s. Took %s seconds.  Compacted size is %s (%d)",
                        name, Timer.timeStr(timer.endTimer()), humanReadableSize(sizeAfter), sizeAfter);
                sizes.put(name, sizeAfter);
                compactLabels(datasetGraph);
            } finally {
                dsg.finishExclusiveMode();
            }
        } else {
            FmtLog.debug(LOG, "Compaction not required for %s as not TDB2", name);
        }
    }

    /**
     * Finds the database size on disk (assuming it's a TDB 2 on-disk database)
     *
     * @param dsg Graph
     * @return Size on disk, of {@code -1} if not calculable
     */
    public static long findDatabaseSize(DatasetGraph dsg) {
        if (dsg instanceof DatasetGraphSwitchable switchable) {
            File dbDir = switchable.getContainerPath().toFile();
            if (dbDir.exists()) {
                return FileUtils.sizeOfDirectory(dbDir);
            }
        }
        return -1;
    }

    /**
     * Formats a database size (if known) as a human-readable size
     *
     * @param size Size, may be less than zero if unknown
     * @return Human-readable size
     */
    public static String humanReadableSize(long size) {
        if (size < 0) {
            return "Unknown";
        } else {
            return FileUtils.byteCountToDisplaySize(size);
        }
    }

    /**
     * Check the given Graph and, if possible, return the underlying TDB2 instance
     *
     * @param dsg Graph
     * @return TDB2 compatible DSG or null
     */
    public static DatasetGraphSwitchable getTDB2(DatasetGraph dsg) {
        for (; ;) {
            if (dsg instanceof DatasetGraphSwitchable datasetGraphSwitchable)
            {
                return datasetGraphSwitchable;
            }
            if (!(dsg instanceof DatasetGraphWrapper dsgw)) {
                return null;
            }
            dsg = dsgw.getWrapped();
        }
    }

    public static void compactLabels(DatasetGraph dsg) {
        if (dsg instanceof DatasetGraphABAC abac) {
            LabelsStore labelsStore = abac.labelsStore();
            if (labelsStore instanceof LabelsStoreRocksDB rocksDB) {
                Timer timer = new Timer();
                timer.startTimer();
                FmtLog.info(LOG, "[Compaction] <<<< Start label store compaction.");
                rocksDB.compact();
                FmtLog.info(LOG, "[Compaction] <<<< Finish label store compaction. Took %s seconds.",
                        Timer.timeStr(timer.endTimer()));
                return;
            }
        }
        FmtLog.info(LOG, "[Compaction] <<<< Label store compaction not needed.");
    }
}
