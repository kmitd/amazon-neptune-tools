/*
Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
Licensed under the Apache License, Version 2.0 (the "License").
You may not use this file except in compliance with the License.
A copy of the License is located at
    http://www.apache.org/licenses/LICENSE-2.0
or in the "license" file accompanying this file. This file is distributed
on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
express or implied. See the License for the specific language governing
permissions and limitations under the License.
*/

package com.amazonaws.services.neptune.propertygraph;

import org.apache.tinkerpop.gremlin.driver.Client;
import org.apache.tinkerpop.gremlin.driver.Cluster;
import org.apache.tinkerpop.gremlin.driver.ResultSet;
import org.apache.tinkerpop.gremlin.driver.SigV4WebSocketChannelizer;
import org.apache.tinkerpop.gremlin.driver.remote.DriverRemoteConnection;
import org.apache.tinkerpop.gremlin.driver.ser.Serializers;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.structure.util.empty.EmptyGraph;

import java.util.Collection;

public class NeptuneGremlinClient implements AutoCloseable {

    public static final int DEFAULT_BATCH_SIZE = 64;

    public static NeptuneGremlinClient create(Collection<String> endpoints, int port, ConcurrencyConfig concurrencyConfig, boolean useIamAuth) {
        return create(endpoints, port, concurrencyConfig, DEFAULT_BATCH_SIZE, useIamAuth);
    }

    public static NeptuneGremlinClient create(Collection<String> endpoints, int port, ConcurrencyConfig concurrencyConfig, int batchSize, boolean useIamAuth) {
        Cluster.Builder builder = Cluster.build()
                .port(port)
                .serializer(Serializers.GRYO_V3D0)
                .maxWaitForConnection(10000)
                .resultIterationBatchSize(batchSize);

        if (useIamAuth){
            builder = builder.channelizer(SigV4WebSocketChannelizer.class);
        }

        for (String endpoint : endpoints) {
            builder = builder.addContactPoint(endpoint);
        }

        return new NeptuneGremlinClient(concurrencyConfig.applyTo(builder).create());
    }

    private final Cluster cluster;

    private NeptuneGremlinClient(Cluster cluster) {
        this.cluster = cluster;
    }

    public GraphTraversalSource newTraversalSource() {
        return EmptyGraph.instance().traversal().withRemote(DriverRemoteConnection.using(cluster));
    }

    public QueryClient queryClient(){
        return new QueryClient(cluster.connect());
    }

    @Override
    public void close() throws Exception {
        if (cluster != null && !cluster.isClosed() && !cluster.isClosing()) {
            cluster.close();
        }
    }

    public static class QueryClient implements AutoCloseable{

        private final Client client;

        QueryClient(Client client) {
            this.client = client;
        }

        public ResultSet submit(String  gremlin){
            return client.submit(gremlin);
        }

        @Override
        public void close() throws Exception {
            client.close();
        }
    }

}