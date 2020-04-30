package com.vaadin;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobAccessPolicy;
import com.azure.storage.blob.models.BlobSignedIdentifier;
import com.azure.storage.blob.models.PublicAccessType;

import java.io.File;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

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

    public String getBlobUrl(Workflow workflow) {
        String connectionString ="DefaultEndpointsProtocol=https;AccountName=workflowmoduledemo;AccountKey=LobXYmy6ya8FseToLyC7Zxjk8T2cUWyFa5x87iPKVzheBNQo5obo7koQBYMR4fSil31fP93eWa0TCbP3d6FKhg==;EndpointSuffix=core.windows.net";
        BlobServiceClient blobServiceClient = new BlobServiceClientBuilder().connectionString(connectionString).buildClient();
        String containerName = "testblobs"+ UUID.randomUUID();


        BlobSignedIdentifier identifier = new BlobSignedIdentifier()
                .setId("name")
                .setAccessPolicy(new BlobAccessPolicy()
                        .setStartsOn(OffsetDateTime.now())
                        .setExpiresOn(OffsetDateTime.now().plusDays(1))
                        .setPermissions("r"));
        BlobContainerClient containerClient = blobServiceClient.createBlobContainer(containerName);
        try {
            containerClient.setAccessPolicy(PublicAccessType.CONTAINER, Collections.singletonList(identifier));
            System.out.println("Set access policy to read only.");
        } catch (UnsupportedOperationException err) {
            err.printStackTrace();
        }

        File file = new File(String.format("src/main/resources/%s.bpmn", workflow.getProcessDescription().replace(" ", "_")));

        BlobClient blobClient = containerClient.getBlobClient(file.getName());

        System.out.println("\nUploading to Blob storage as blob:\n\t" + blobClient.getBlobUrl());

        blobClient.uploadFromFile(file.getAbsolutePath());
        return blobClient.getBlobUrl();
    }
}
