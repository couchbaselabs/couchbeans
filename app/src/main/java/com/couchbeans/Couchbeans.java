package com.couchbeans;

import com.couchbase.client.core.cnc.EventBus;
import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.Scope;

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

    static {
       CLUSTER = Cluster.connect(
               CBB_CLUSTER,
               CBB_USERNAME,
               CBB_PASSWORD
       );
       BUCKET = CLUSTER.bucket(CBB_BUCKET);
       SCOPE = BUCKET.scope(CBB_SCOPE);
       EVENT_BUS = CLUSTER.environment().eventBus();
    }
}
