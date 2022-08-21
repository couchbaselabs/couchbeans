package com.couchbeans;

import com.couchbase.client.core.cnc.events.config.CollectionMapRefreshSucceededEvent;
import com.couchbase.client.core.io.CollectionIdentifier;
import com.couchbase.client.core.io.CollectionMap;
import com.couchbase.client.dcp.Client;
import com.couchbase.client.dcp.ControlEventHandler;
import com.couchbase.client.dcp.DataEventHandler;
import com.couchbase.client.dcp.StreamFrom;
import com.couchbase.client.dcp.StreamTo;
import com.couchbase.client.dcp.deps.io.netty.buffer.ByteBuf;
import com.couchbase.client.dcp.highlevel.internal.CollectionIdAndKey;
import com.couchbase.client.dcp.highlevel.internal.CollectionsManifest;
import com.couchbase.client.dcp.message.DcpMutationMessage;
import com.couchbase.client.dcp.message.MessageUtil;
import com.couchbase.client.dcp.transport.netty.ChannelFlowController;
import org.apache.logging.log4j.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.couchbase.repository.query.CouchbaseEntityInformation;

import java.util.Map;
import java.util.function.Consumer;

public class DCPListener implements DataEventHandler, ControlEventHandler {

    private static final DCPListener INSTANCE = new DCPListener();
    private static final Logger LOGGER = LoggerFactory.getLogger(DCPListener.class);

    private static final Client CLIENT = Client.builder()
            .collectionsAware(true)
            .connectionString(Couchbeans.CBB_CLUSTER)
            .credentials(Couchbeans.CBB_USERNAME, Couchbeans.CBB_PASSWORD)
            .bucket(Couchbeans.CBB_BUCKET)
            .scopeName(Couchbeans.CBB_SCOPE)
            .build();

    public static void main(String... args) {
        CLIENT.controlEventHandler(INSTANCE);
        CLIENT.dataEventHandler(INSTANCE);

        CLIENT.connect().block();
        CLIENT.initializeState(StreamFrom.NOW, StreamTo.INFINITY).block();
        CLIENT.startStreaming().block();
        try {
            while (true) {
                Thread.sleep(10000);
            }
        } catch (InterruptedException e) {

        } finally {
            CLIENT.disconnect().block();
        }
    }

    @Override
    public void onEvent(ChannelFlowController flowController, ByteBuf event) {
        CollectionsManifest manifest = getCurrentManifest(MessageUtil.getVbucket(event));

        if (DcpMutationMessage.is(event)) {
            CollectionIdAndKey ckey = MessageUtil.getCollectionIdAndKey(event, true);
            CollectionsManifest.CollectionInfo cinfo = manifest.getCollection(ckey.collectionId());

            LOGGER.info("DCP Mutation message.\n\tCollection: {};\n\tKey: {};\n\tContent:{}\n\tMessage: {}",
                    cinfo.name(),
                    ckey.key(),
                    MessageUtil.getContentAsString(event),
                    DcpMutationMessage.toString(event));
        }
        event.release();
    }

    private static CollectionsManifest getCurrentManifest(int vbucket) {
        return CLIENT.sessionState()
                .get(vbucket)
                .getCollectionsManifest();
    }
}
