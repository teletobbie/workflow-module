package com.vaadin;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

import java.util.List;

public class WorkflowService {
    private static WorkflowService instance;
    private Database database;

    public WorkflowService() {
        this.database = new Database();
    }

    public static WorkflowService getInstance() {
        if (instance == null) {
            instance = new WorkflowService();
        }
        return instance;
    }

    public Workflow getWorkflow(int processId) {
        //processId is kp_id
        Workflow workflow = null;

        database.start();
        ResultSet result = database.queryStatement(String.format(
                "select pr.KP_ID,pr.KP_OMSCHRIJVING, st.STATUSNUMBER, st.KS_OMSCHRIJVING,ns.NXT_STATUSNUMBER, nsd.KS_OMSCHRIJVING from status st\n" +
                        "left join next_status ns on ns.CUR_STATUSNUMBER=st.STATUSNUMBER\n" +
                        "left join proces pr on pr.KP_ID=st.KP_ID\n" +
                        "left join status nsd on nsd.STATUSNUMBER=ns.NXT_STATUSNUMBER\n" +
                        "where pr.kp_id=%s\n" +
                        "order by st.STATUSNUMBER;", processId));

        if (result != null) {
            try {
                List<Status> statusList = new ArrayList<>();
                while (result.next()) {
                    Status status = new Status(
                            result.getInt(3),
                            result.getString(4),
                            result.getInt(5),
                            result.getString(6));
                    statusList.add(status);
                    int workFlowId = result.getInt(1);
                    String workFlowDescription = result.getString(2);
                    workflow = new Workflow(workFlowId, workFlowDescription, statusList);
                }
            } catch (SQLException sqlEx) {
                sqlEx.printStackTrace();
            }
        }
        return workflow;
    }

    public BPMNModeller createBPMNDiagram(Workflow workflow) {
        BPMNModeller bpmnModeller = new BPMNModeller(workflow);
        bpmnModeller.createModel();
        return bpmnModeller;
    }
}
