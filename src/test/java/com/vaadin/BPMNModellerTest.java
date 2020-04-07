package com.vaadin;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;

class BPMNModellerTest {
    Workflow workflow;
    BPMNModeller bpmnModeller;

    @BeforeEach
    void initialize() {
        int id = 110;
        String processDescription = "Promotie behandeling";
        ArrayList<Status> statuses = new ArrayList<>(
                Arrays.asList(
                        new Status(151, "Voorgesteld", 152, "In behandeling"),
                        new Status(152, "In behandeling", 153, "Afgekeurd"),
                        new Status(152, "In behandeling", 154, "Goedgekeurd"),
                        new Status(153, "Afgekeurd", 0, null),
                        new Status(154, "Goedgekeurd", 155, "Verwerkt"),
                        new Status(155, "Verwerkt", 0, null))
        );
        workflow = new Workflow(id, processDescription, statuses);
        bpmnModeller = new BPMNModeller(workflow);
    }



}