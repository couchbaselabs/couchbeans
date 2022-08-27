package cbb;

import com.couchbase.client.core.error.DocumentNotFoundException;
import com.couchbase.client.java.kv.GetResult;

public class CouchbaseClassLoader extends ClassLoader {
    public static ClassLoader INSTANCE = new CouchbaseClassLoader(Thread.currentThread().getContextClassLoader());

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
            return super.findClass(name);
        }

        return defineClass(name, code, 0, code.length);
    }

    private byte[] loadClassData(String name) {
        try {
            GetResult data = Couchbeans.SCOPE.collection(Utils.collectionName(Class.class)).get(name);
            if (data != null) {
                return data.contentAsBytes();
            }
        } catch (DocumentNotFoundException dnfe) {
            // noop
        }
        return null;
    }
}
