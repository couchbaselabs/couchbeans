package com.couchbeans;

import com.couchbase.client.core.cnc.EventBus;
import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.Scope;
import com.couchbase.client.java.codec.JsonSerializer;
import com.couchbase.client.java.kv.GetResult;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

public class Couchbeans {
    public static final Cluster CLUSTER;
    public static final Bucket BUCKET;
    public static final Scope SCOPE;
    public static final String CBB_BUCKET = Utils.envOrDefault("CBB_BUCKET", "default");
    public static final String CBB_SCOPE = Utils.envOrDefault("CBB_SCOPE", "_default");
    public static final String CBB_CLUSTER = Utils.envOrDefault("CBB_CLUSTER", "couchbase://localhost");
    public static final String CBB_USERNAME = Utils.envOrDefault("CBB_USERNAME", "Administrator");
    public static final String CBB_PASSWORD = Utils.envOrDefault("CBB_PASSWORD", "password");

    public static final EventBus EVENT_BUS;

    public static final JsonSerializer SERIALIZER;
    public static final Map<Object, String> KEY = Collections.synchronizedMap(new WeakHashMap<>());

    static {
       CLUSTER = Cluster.connect(
               CBB_CLUSTER,
               CBB_USERNAME,
               CBB_PASSWORD
       );
       BUCKET = CLUSTER.bucket(CBB_BUCKET);
       SCOPE = BUCKET.scope(CBB_SCOPE);
       EVENT_BUS = CLUSTER.environment().eventBus();
       SERIALIZER = SCOPE.environment().jsonSerializer();
    }

    public static <T> T get(Class<T> type, String id) {
        GetResult gr = SCOPE.collection(Utils.collectionName(type)).get(id);
        if (gr != null) {
            T bean = gr.contentAs(type);
            KEY.put(bean, id);
            return bean;
        }

        return null;
    }
}
