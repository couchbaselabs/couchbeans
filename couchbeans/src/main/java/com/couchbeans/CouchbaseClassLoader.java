package com.couchbeans;

import com.couchbase.client.java.Collection;
import com.couchbase.client.java.kv.GetResult;

public class CouchbaseClassLoader extends ClassLoader {
    private static final String COLLECTION_NAME = Utils.collectionName(Class.class);
    private static final Collection source = Couchbeans.SCOPE.collection(COLLECTION_NAME);

    static {
        Utils.createCollectionIfNotExists(COLLECTION_NAME);
        Utils.createPrimaryIndexIfNotExists(COLLECTION_NAME);
    }

    public CouchbaseClassLoader(ClassLoader parent) {
        super(parent);
    }

    public CouchbaseClassLoader() {
        super();
    }

    @Override
    public Class<?> findClass(String name) throws ClassNotFoundException {
        byte[] code = loadClassData(name);
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
