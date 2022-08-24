package com.couchbeans;

import org.apache.commons.lang3.exception.ExceptionUtils;

import java.io.PrintStream;
import java.io.StringWriter;

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
}
