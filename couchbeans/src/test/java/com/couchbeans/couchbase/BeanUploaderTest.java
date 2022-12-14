package com.couchbeans.couchbase;

import com.couchbase.client.java.Collection;
import com.couchbase.client.java.json.JsonObject;
import cbb.BeanUploader;
import javassist.CannotCompileException;
import javassist.ClassClassPath;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.NotFoundException;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;


public class BeanUploaderTest {
    private static final Logger LOG = LoggerFactory.getLogger(BeanUploaderTest.class);

    public void methodWithArguments(String argument1, Integer argument2) {

    }

    @Test
    public void testMain() throws IOException, CannotCompileException, NotFoundException {
        File source = File.createTempFile("test", ".class");
        ClassPool pool = ClassPool.getDefault();
        ClassClassPath ccpath = new ClassClassPath(BeanUploaderTest.class);
        pool.insertClassPath(ccpath);

        CtClass me = pool.get(BeanUploaderTest.class.getCanonicalName());
        DataOutputStream out = new DataOutputStream(new FileOutputStream(source));
        me.getClassFile().write(out);

        Collection meta = Mockito.mock(Collection.class);
        Collection code = Mockito.mock(Collection.class);

        Answer loggingAnswer = it -> {
            LOG.info("Class metadata document: id = {}; value = {}", it.getArgumentAt(0, String.class), it.getArgumentAt( 1, JsonObject.class).toString());
            return null;
        };

        Mockito.when(meta.upsert(Mockito.anyString(), Mockito.any())).thenAnswer(loggingAnswer);

        BeanUploader.main(new String[] {source.getAbsolutePath()}, meta, code);
    }
}