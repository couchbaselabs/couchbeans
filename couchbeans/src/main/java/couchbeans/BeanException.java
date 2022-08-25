package couchbeans;

import org.apache.commons.lang3.exception.ExceptionUtils;

public class BeanException {
    private String message;
    private String[] trace;
    private String location;

    public BeanException(Throwable e) {
        message = e.getMessage();
        trace = ExceptionUtils.getStackFrames(e);
        location = ExceptionUtils.getStackFrames(ExceptionUtils.getRootCause(e))[0];
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
        BeanException be = new BeanException(e);
        Couchbeans.store(be);
        Couchbeans.link(related, be);
    }
}
