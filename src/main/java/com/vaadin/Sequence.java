package com.vaadin;

public final class Sequence {
    private final Object from;
    private final Object to;
    public Sequence(Object from, Object to) {this.from = from; this.to = to;}

    public Object getFrom() {
        return from;
    }

    public Object getTo() {
        return to;
    }
}
