package com.vaadin;

import java.util.List;

public class Workflow {
    private int id;
    private String processDescription;
    private List<Status> statuses;

    public Workflow(int id, String processDescription, List<Status> statuses) {
        this.id = id;
        this.processDescription = processDescription;
        this.statuses = statuses;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getProcessDescription() {
        return processDescription;
    }

    public List<Status> getStatuses() {
        return statuses;
    }
}
