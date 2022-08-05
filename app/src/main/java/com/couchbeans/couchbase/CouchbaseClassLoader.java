package com.couchbeans.couchbase;

import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.Collection;
import com.couchbase.client.java.Scope;
import com.couchbase.client.java.kv.GetResult;

public class CouchbaseClassLoader extends ClassLoader {
    private Collection source;

    public CouchbaseClassLoader(Collection source) {
        this.source = source;
    }

    public CouchbaseClassLoader(Scope scope, String collection) {
        this(scope.collection(collection));
    }

    public CouchbaseClassLoader(Bucket bucket, String scope, String collection) {
        this(bucket.scope(scope), collection);
    }

    public CouchbaseClassLoader(Cluster cluster, String bucket, String scope, String collection) {
        this(cluster.bucket(bucket), scope, collection);
    }

    public CouchbaseClassLoader(String address, String username, String password, String bucket, String scope, String collection) {
        this(Cluster.connect(address, username, password), bucket, scope, collection);
    }

    public CouchbaseClassLoader() {
        this(
                System.getProperty("com.couchbase.classloader.cluster"),
                System.getProperty("com.couchbase.classloader.username"),
                System.getProperty("com.couchbase.classloader.password"),
                System.getProperty("com.couchbase.classloader.bucket"),
                System.getProperty("com.couchbase.classloader.scope"),
                System.getProperty("com.couchbase.classloader.collection")
        );
    }

    @Override
    public Class<?> findClass(String name) throws ClassNotFoundException {
        byte[] code =  loadClassData(name);
        if (code == null) {
            if (getParent() != null) {
                return getParent().loadClass(name);
            }
            throw new ClassNotFoundException(name);
        }

        return defineClass(name, code, 0, code.length);
    }

    private byte[] loadClassData(String name) {
        GetResult data = source.get(name);
        if (data != null) {
            return data.contentAsBytes();
        }
        return null;
    }
}
