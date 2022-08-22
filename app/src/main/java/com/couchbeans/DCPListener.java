package com.couchbeans;

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
import com.couchbase.client.java.json.JsonObject;
import com.couchbase.client.java.json.JsonValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.stream.Stream;

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

        if (DcpMutationMessage.is(event)) {
            processMutation(event);
        }
        event.release();
    }

    private static CollectionsManifest getCurrentManifest(int vbucket) {
        return CLIENT.sessionState()
                .get(vbucket)
                .getCollectionsManifest();
    }

    private static void processMutation(ByteBuf event) {
        CollectionIdAndKey ckey = MessageUtil.getCollectionIdAndKey(event, true);
        CollectionsManifest manifest = getCurrentManifest(MessageUtil.getVbucket(event));
        CollectionsManifest.CollectionInfo cinfo = manifest.getCollection(ckey.collectionId());
        String className = cinfo.name();

        Class targetClass;
        Object bean;

        LOGGER.info("DCP Mutation message.\n\tCollection: {};\n\tKey: {};\n\tContent:{}\n\tMessage: {}",
                cinfo.name(),
                ckey.key(),
                MessageUtil.getContentAsString(event),
                DcpMutationMessage.toString(event));

        if (className.equals("_default")) {
            if (isJson(event)) {
                targetClass = JsonValue.class;
                bean = Couchbeans.SERIALIZER.deserialize(targetClass, MessageUtil.getContentAsByteArray(event));
            } else {
                targetClass = byte[].class;
                bean = MessageUtil.getContentAsByteArray(event);
            }
        } else {
            try {
                targetClass = Class.forName(className);
                bean = Couchbeans.SERIALIZER.deserialize(targetClass, MessageUtil.getContentAsByteArray(event));
            } catch (ClassNotFoundException e) {
                LOGGER.error("Failed to load class " + className, e);
                return;
            }
        }

        Couchbeans.KEY.put(bean, ckey.key());

    }

    public static Stream<Object> processBean(Object bean) {
        Class beanType = bean.getClass();

        return null;
    }

    private static boolean isJson(ByteBuf event) {
        final int flags = DcpMutationMessage.flags(event);
        final int commonFlags = flags >> 24;
        if (commonFlags == 0) {
            return flags == 0;
        }

        return (commonFlags & 0xf) == 2;
    }

}
