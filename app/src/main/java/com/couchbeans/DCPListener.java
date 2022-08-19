package com.couchbeans;

import com.couchbase.client.dcp.Client;
import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.Scope;

import java.util.Map;

public class DCPListener {

    public static void main(String... args) {
        Map<String, String> env = System.getenv();
        Client client = Client.builder()
                .connectionString(env.getOrDefault("CBB_CLUSTER", "localhost"))
                .credentials(
                        env.getOrDefault("CBB_USERNAME", "Administrator"),
                        env.getOrDefault("CBB_PASSWORD", "password")
                )
                .bucket(Couchbeans.BUCKET_NAME)
                .scopeName(Couchbeans.SCOPE_NAME)
                .build();

    }
}
