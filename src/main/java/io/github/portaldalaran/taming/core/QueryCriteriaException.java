package io.github.portaldalaran.taming.core;

/**
 * @author david
 */
public class QueryCriteriaException extends RuntimeException {
    public QueryCriteriaException() {
        super();
    }

    public QueryCriteriaException(String s) {
        super(s);
    }

    public QueryCriteriaException(String s, Throwable throwable) {
        super(s, throwable);
    }

    public QueryCriteriaException(Throwable throwable) {
        super(throwable);
    }

    protected QueryCriteriaException(String s, Throwable throwable, boolean b, boolean b1) {
        super(s, throwable, b, b1);
    }
}
