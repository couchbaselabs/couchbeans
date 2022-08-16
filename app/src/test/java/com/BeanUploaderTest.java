package com;

import com.couchbase.client.java.Collection;
import javassist.CannotCompileException;
import javassist.ClassClassPath;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.NotFoundException;
import org.checkerframework.checker.units.qual.C;
import org.junit.jupiter.api.Test;
import org.mockito.DoNotMock;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;


@DoNotMock
@Component
public class BeanUploaderTest {
    private static final Logger LOG = LoggerFactory.getLogger(BeanUploaderTest.class);

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
            LOG.info("Class metadata: id = {}; value = {}", it.getArgument(0).toString(), it.getArgument( 1).toString());
            return null;
        };

        Mockito.when(meta.upsert(Mockito.anyString(), Mockito.any())).thenAnswer(loggingAnswer);

        BeanUploader.main(new String[] {source.getAbsolutePath()}, meta, code);
    }
}