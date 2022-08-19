package com.couchbeans;

import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.Scope;

public class Couchbeans {
    public static final Cluster CLUSTER;
    public static final Bucket BUCKET;
    public static final Scope SCOPE;
    public static final String BUCKET_NAME = Utils.envOrDefault("CBB_BUCKET", "default");
    public static final String SCOPE_NAME = Utils.envOrDefault("CBB_SCOPE", "_default");

    static {
       CLUSTER = Cluster.connect(
               Utils.envOrDefault("CBB_CLUSTER", "localhost"),
               Utils.envOrDefault("CBB_USERNAME", "Administrator"),
               Utils.envOrDefault("CBB_PASSWORD", "password")
       );
       BUCKET = CLUSTER.bucket(BUCKET_NAME);
       SCOPE = BUCKET.scope(SCOPE_NAME);
    }
}
