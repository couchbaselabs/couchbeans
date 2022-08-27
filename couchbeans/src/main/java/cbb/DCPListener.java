package cbb;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

public class DCPListener implements DataEventHandler, ControlEventHandler {

    private static DCPListener INSTANCE;
    private static final Logger LOGGER = LoggerFactory.getLogger(DCPListener.class);

    public static final AtomicBoolean RUNNING = new AtomicBoolean(false);

    private static Client CLIENT;

    public static void main(String... args) {
        INSTANCE = new DCPListener();
        CLIENT = Client.builder()
                .collectionsAware(true)
                .connectionString(Couchbeans.CBB_CLUSTER)
                .credentials(Couchbeans.CBB_USERNAME, Utils.envOrDefault("CBB_PASSWORD", "password"))
                .bucket(Couchbeans.CBB_BUCKET)
                .scopeName(Couchbeans.CBB_SCOPE)
                .build();
        RUNNING.set(true);
        Couchbeans.NODE.setRunning(true);
        Singleton.initializeNode();
        CLIENT.controlEventHandler(INSTANCE);
        CLIENT.dataEventHandler(INSTANCE);


        CLIENT.connect().block();
        System.out.println("DCP client connected.");
        CLIENT.initializeState(StreamFrom.BEGINNING, StreamTo.INFINITY).block();
        System.out.println("Starting the stream...");
        CLIENT.startStreaming().block();
        System.out.println("Started the stream.");
        try {
            while (true) {
                Thread.sleep(10000);
            }
        } catch (InterruptedException e) {

        } finally {
            RUNNING.set(false);
            Couchbeans.NODE.setRunning(false);
            CLIENT.disconnect().block();
        }
    }

    @Override
    public void onEvent(ChannelFlowController flowController, ByteBuf event) {
        // TODO: add some magic that detects if this mutation belongs to this node
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

        LOGGER.warn("DCP Mutation message.\n\tCollection: {};\n\tKey: {};\n\tContent:{}\n\tMessage: {}",
                cinfo.name(),
                ckey.key(),
                MessageUtil.getContentAsString(event),
                DcpMutationMessage.toString(event));

        if (className.equals("_default")) {
            if (isJson(event)) {
                targetClass = Object.class;
            } else {
                return;
            }
        } else {
            targetClass = Utils.collectionClass(className).orElse(null);
            if (targetClass == null) {
                LOGGER.error("Failed to load class " + className);
                return;
            }
        }
        String targetType = targetClass.getCanonicalName();

        try {
            if (targetClass.getPackageName().startsWith("cbb")) {
                if (Singleton.class == targetClass) {
                    Singleton target = Singleton.get(targetType);
                    target.update(MessageUtil.getContentAsString(event));
                }
            } else if (targetClass.getPackageName().startsWith("java")) {
                return;
            } else {
                Utils.getBeanInfo(targetClass.getCanonicalName(), ckey.key()).ifPresentOrElse(
                        info -> MutationTreeWalker.processBeanUpdate(info, MessageUtil.getContentAsString(event)),
                        () -> {
                            BeanInfo info = MutationTreeWalker.registerBean(targetClass, ckey.key(), MessageUtil.getContentAsString(event));
                            if (BeanLink.class.isAssignableFrom(targetClass)) {
                                MutationTreeWalker.processNewBeanLink((BeanLink) info.bean());
                            }
                        }
                );
            }
        } catch (Exception e) {
            BeanException.report(targetType, ckey.key(), e);
            LOGGER.error("Failed to process message", e);
        }
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
