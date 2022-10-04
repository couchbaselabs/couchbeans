package cbb;

import com.couchbase.client.core.error.DocumentNotFoundException;
import com.couchbase.client.core.error.InvalidArgumentException;
import com.couchbase.client.java.json.JsonArray;
import com.couchbase.client.java.kv.GetResult;
import com.couchbase.client.java.query.QueryOptions;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.stream.Collectors;

public class CouchbaseClassLoader extends ClassLoader {
    public static CouchbaseClassLoader INSTANCE = new CouchbaseClassLoader(CouchbaseClassLoader.class.getClassLoader());

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

    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        return findResources(name);
    }

    @Override
    protected Enumeration<URL> findResources(String name) throws IOException {
        return Collections.enumeration(
                Couchbeans.SCOPE.query(
                                String.format("SELECT RAW name FROM `%s` where name LIKE '%%' || $1 || '%%'", App.RESOURCE_COLLECTION),
                                QueryOptions.queryOptions().parameters(JsonArray.from(name))
                        ).rowsAs(String.class).stream()
                        .map(n -> {
                            try {
                                return new URL("cbbr://", Couchbeans.CBB_CLUSTER, String.format("%s/%s/%s", Couchbeans.CBB_BUCKET, Couchbeans.CBB_SCOPE, n));
                            } catch (MalformedURLException e) {
                                throw new RuntimeException(e);
                            }
                        })
                        .collect(Collectors.toList()));
    }

    @Override
    protected URL findResource(String name) {
        return Couchbeans.SCOPE.query(
                        String.format("SELECT RAW name FROM `%s` where name = '$1'", App.RESOURCE_COLLECTION),
                        QueryOptions.queryOptions().parameters(JsonArray.from(name))
                ).rowsAs(String.class).stream()
                .findFirst().map(n -> {
                    try {
                        return new URL("cbbr://", Couchbeans.CBB_CLUSTER, String.format("%s/%s/%s", Couchbeans.CBB_BUCKET, Couchbeans.CBB_SCOPE, n));
                    } catch (MalformedURLException e) {
                        throw new RuntimeException(e);
                    }
                })
                .orElseGet(() -> super.findResource(name));
    }

    @Override
    public InputStream getResourceAsStream(String name) {
        return Couchbeans.SCOPE.query(
                String.format("SELECT * FROM `%s` WHERE name = '$1'", App.RESOURCE_COLLECTION),
        QueryOptions.queryOptions().parameters(JsonArray.from(name))
        ).rowsAs(Resource.class).stream().findFirst()
                .map(b -> (InputStream) new ByteArrayInputStream(b.value()))
                .orElseGet(() -> super.getResourceAsStream(name));
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

    public void reloadClasses() {
        INSTANCE = new CouchbaseClassLoader(CouchbaseClassLoader.class.getClassLoader());
        System.out.println("Reloaded all bean definitions");
    }
}
