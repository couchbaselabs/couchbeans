package cbb;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.commons.lang3.exception.ExceptionUtils;

public class BeanException {
    private String message;
    private String[] trace;
    private String location;

    public BeanException() {

    }

    public BeanException(Throwable e) {
        message = e.getMessage();
        trace = ExceptionUtils.getStackFrames(e);
        location = ExceptionUtils.getStackFrames(ExceptionUtils.getRootCause(e))[0];
    }

    public static void reportAndThrow(Object related, Throwable e) {
        report(related, e);
        if (e instanceof RuntimeException) {
            throw (RuntimeException) e;
        } else {
            throw new RuntimeException(e);
        }
    }

    public static void report(String targetType, String key, Throwable e) {
        BeanException be = new BeanException(e);
        Couchbeans.store(be);
        Couchbeans.store(new BeanLink(targetType, key, BeanException.class.getCanonicalName(), Couchbeans.key(be)));
    }

    public String[] trace() {
        return trace;
    }

    public String message() {
        return message;
    }

    public String location() {
        return location;
    }

    public static void report(Object related, Throwable e) {
        report(related.getClass().getCanonicalName(), Couchbeans.key(related), e);
    }
}
