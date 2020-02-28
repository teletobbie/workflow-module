package com.vaadin;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class WorkflowService {
    private static WorkflowService instance;
    private Database database;
    private DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
    private final String path = "src/main/resources/workflow.xml";

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

    public void createXML(Workflow workflow) {
        int counter = 0;
        List<Status> statusList = workflow.getStatuses().stream()
                .filter(distinctByKeys(Status::getStatusNumber, Status::getStatusDescription))
                .collect(Collectors.toList());
        try {
            DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
            Document document = documentBuilder.parse(path);
            Element root = document.getDocumentElement();

            removeAllChildNodes(root);

            Element process = document.createElement("process");
            process.setAttribute("processType", "Private");
            process.setAttribute("isExecutable", "true");
            process.setAttribute("id", "_"+workflow.getId());
            process.setAttribute("name", workflow.getProcessDescription());

            for (Status status : statusList) {
                if (counter == 0) {
                    Element startEvent = createNodeElement(document, "startEvent",
                            Integer.toString(status.getStatusNumber()), status.getStatusDescription());
                    process.appendChild(startEvent);
                } else if (counter == statusList.size() - 1) {
                    Element endEvent = createNodeElement(document, "endEvent",
                            Integer.toString(status.getStatusNumber()), status.getStatusDescription());
                    Element terminateEvent = document.createElement("terminateEventDefinition");
                    endEvent.appendChild(terminateEvent);
                    process.appendChild(endEvent);
                } else {
                    Element scriptTask = createNodeElement(document, "scriptTask",
                            Integer.toString(status.getStatusNumber()), status.getStatusDescription());
                    process.appendChild(scriptTask);
                }
                counter++;
            }

//            List<Status> listOfStatusDuplicates = getDuplicatedStatuses(workflow.getStatuses());
//            if(listOfStatusDuplicates.size() >= 2) {
//                appendGateway(document, process, listOfStatusDuplicates);
//            } else {
//                for (Status status : workflow.getStatuses()) {
//                    if (status.getNextStatusNumber() != 0) {
//                        Element sequenceFlow = createSequence(document, status.getStatusNumber(),
//                                status.getNextStatusNumber());
//                        process.appendChild(sequenceFlow);
//                    }
//                }
//            }

            for (Status status : workflow.getStatuses()) {
                if (status.getNextStatusNumber() != 0) {
                    Element sequenceFlow = createSequence(document, status.getStatusNumber(),
                            status.getNextStatusNumber());
                    process.appendChild(sequenceFlow);
                }
            }

            root.appendChild(process);
            transformXML(document);
        } catch (IOException | ParserConfigurationException | SAXException | TransformerException ex) {
            ex.printStackTrace();
        }
    }

    private Element createNodeElement(Document document, String tagName, String id, String name) {
        //create element for the workflow
        Element node = document.createElement(tagName);
        node.setAttribute("id", "_"+id);
        node.setAttribute("name", name);
        return node;
    }

    private Element createSequence(Document document, int statusNumber, int nextStatusNumber) {
        //sequenceFlow are the connection points to the different nodes.
        Element sequenceFlow = document.createElement("sequenceFlow");
        sequenceFlow.setAttribute("id", String.format("_%s-%s", statusNumber, nextStatusNumber));
        sequenceFlow.setAttribute("sourceRef", "_"+statusNumber);
        sequenceFlow.setAttribute("targetRef", "_"+nextStatusNumber);
        return sequenceFlow;
    }

    private Element createGatewaySequence(Document document, String gateWayId, int nextStatusNumber, String nextStatusDescription) {
        Element sequenceConditionFlow = document.createElement("sequenceFlow");
        sequenceConditionFlow.setAttribute("sourceRef", gateWayId);
        sequenceConditionFlow.setAttribute("targetRef", "_"+nextStatusNumber);
        sequenceConditionFlow.setAttribute("name", nextStatusDescription);
        Element conditionExpression = document.createElement("conditionExpression");
        conditionExpression.setAttribute("xsi:type", "tFormalExpression");
        conditionExpression.setTextContent(nextStatusDescription);
        sequenceConditionFlow.appendChild(conditionExpression);
        return sequenceConditionFlow;
    }

    private Element createGateway(Document document, String statusDescription) {
        Element gateway = document.createElement("exclusiveGateway");
        gateway.setAttribute("id", statusDescription);
        gateway.setAttribute("name", String.format("exclusive gateway %s", statusDescription));
        return gateway;
    }

    private void appendGateway(Document document, Element parent, List<Status> statuses) {
        Element gatewayFlow = createGateway(document, statuses.get(0).getStatusDescription());
        parent.appendChild(gatewayFlow);
        for (Status status : statuses) {
            Element gatewaySequenceFlow = createGatewaySequence(document, status.getStatusDescription(), status.getNextStatusNumber(), status.getNextStatusDescription());
            parent.appendChild(gatewaySequenceFlow);
        }
    }

    private void transformXML(Document document) throws TransformerException {
        DOMSource source = new DOMSource(document);
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        StreamResult result = new StreamResult(path);
        transformer.transform(source, result);
    }

    private void removeAllChildNodes(Node parentNode) {
        while (parentNode.hasChildNodes()) {
            parentNode.removeChild(parentNode.getFirstChild());
        }
    }

    @SafeVarargs
    private static <T> Predicate<T> distinctByKeys(Function<? super T, ?>... keyExtractors) {
        //functional interface that returns a Predicate (consequence) if in this case Obj 1 key equals Obj 2 key
        //functional interface is a interface with one abstract method in this case the Equals method
        final Map<List<?>, Boolean> seen = new ConcurrentHashMap<>();
        return t ->
        {
            final List<?> keys = Arrays.stream(keyExtractors)
                    .map(ke -> ke.apply(t))
                    .collect(Collectors.toList());
            return seen.putIfAbsent(keys, Boolean.TRUE) == null;
        };
    }

    private List<Status> getDuplicatedStatuses(List<Status> statusList) {
        List<Status> result = new ArrayList<>();
        for (int i = 0; i < statusList.size() - 1; i++) {
            if(statusList.get(i).getStatusNumber() == statusList.get(i + 1).getStatusNumber()) {
                result.add(statusList.get(i));
                result.add(statusList.get(i + 1));
            }
        }
        return result;
    }



}
