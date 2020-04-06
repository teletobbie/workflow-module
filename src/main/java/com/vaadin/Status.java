package com.vaadin;

public class Status {
    private int statusNumber;
    private String statusDescription;
    private int nextStatusNumber;
    private String nextStatusDescription;

    public Status(int statusNumber, String statusDescription, int nextStatusNumber, String nextStatusDescription) {
        this.statusNumber = statusNumber;
        this.statusDescription = statusDescription;
        this.nextStatusNumber = nextStatusNumber;
        this.nextStatusDescription = nextStatusDescription;
    }

    public int getStatusNumber() {
        return statusNumber;
    }

    public String getStatusDescription() {
        return statusDescription;
    }

    public int getNextStatusNumber() {
        return nextStatusNumber;
    }
}
