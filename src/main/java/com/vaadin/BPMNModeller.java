package com.vaadin;



import org.activiti.bpmn.BpmnAutoLayout;
import org.activiti.bpmn.converter.BpmnXMLConverter;
import org.activiti.bpmn.model.Process;
import org.activiti.bpmn.model.*;
import org.activiti.engine.ProcessEngine;
import org.activiti.engine.ProcessEngineConfiguration;
import org.apache.commons.io.FileUtils;
import org.apache.ibatis.javassist.tools.rmi.ObjectNotFoundException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;


public class BPMNModeller {
    private final String path = "src/main/resources/";
    private Workflow workflow;
    private Process process;
    private ProcessEngineConfiguration processEngineConfiguration;
    private ProcessEngine processEngine;
    private HashMap<Integer, List<Object>> statusSequence = new HashMap<>();

    public BPMNModeller(Workflow workflow) {
        this.workflow = workflow;
        this.processEngineConfiguration = ProcessEngineConfiguration.createStandaloneInMemProcessEngineConfiguration();
        this.processEngine = processEngineConfiguration
                .setJdbcDriver("org.h2.Driver")
                .setJdbcUrl("jdbc:h2:mem:activiti;DB_CLOSE_DELAY=1000")
                .setJdbcUsername("sa")
                .setJdbcPassword("")
                .buildProcessEngine();
    }

    public void createModel() {
        createProcess();
        createEvents();
        createSequences();
        writeModel();
    }

    private void createProcess() {
        process = new Process();
        process.setId(workflow.getProcessDescription().replace(" ", "_"));
        process.setName(workflow.getProcessDescription());
    }

    private void createEvents() {
        List<Status> statusList = workflow.getStatuses().stream()
                .filter(distinctByKeys(Status::getStatusNumber, Status::getStatusDescription))
                .collect(Collectors.toList());
        Map<Integer, Long> countSameStatuses = workflow.getStatuses().stream().collect(
                Collectors.groupingBy(Status::getStatusNumber, Collectors.counting()));

        for (Status status : statusList) {
            long count = countSameStatuses.get(status.getStatusNumber());
            if(isStartEvent(status.getStatusNumber())) {
                StartEvent startEvent = createStartEvent();
                UserTask startTask = createUserTask(status.getStatusNumber(), String.format("%s. %s", status.getStatusNumber(), status.getStatusDescription()));
                process.addFlowElement(startEvent);
                process.addFlowElement(startTask);
                statusSequence.put(status.getStatusNumber(), new ArrayList<>(Arrays.asList(startEvent, startTask)));
            } else if (status.getNextStatusNumber() == 0) {
                EndEvent endEvent = createEndEvent();
                UserTask endTask = createUserTask(status.getStatusNumber(), String.format("%s. %s", status.getStatusNumber(), status.getStatusDescription()));
                process.addFlowElement(endEvent);
                process.addFlowElement(endTask);
                statusSequence.put(status.getStatusNumber(), new ArrayList<>(Arrays.asList(endTask, endEvent)));
            } else {
                UserTask userTask = createUserTask(status.getStatusNumber(),
                        String.format("%s. %s", status.getStatusNumber(), status.getStatusDescription()));
                process.addFlowElement(userTask);
                statusSequence.put(status.getStatusNumber(), new ArrayList<>(Collections.emptyList()));
            }

            if (count >= 2) {
                ExclusiveGateway exclusiveGateway = createExclusiveGateway("xg"+status.getStatusNumber(), status.getStatusDescription());
                process.addFlowElement(exclusiveGateway);
                List<Object> updatedNextElements = statusSequence.get(status.getStatusNumber());
                updatedNextElements.add(exclusiveGateway);
                statusSequence.replace(status.getStatusNumber(), updatedNextElements);
            }
        }
        updateSequenceMap();



    }

    private void createSequences() {
        List<SequenceFlow> sequenceFlows = new ArrayList<>(); //list of incoming sequenceflows
        System.out.println(statusSequence);
        for (Map.Entry<Integer, List<Object>> flowEntry : statusSequence.entrySet()) {
            FlowElement from = process.getFlowElement(Integer.toString(flowEntry.getKey()));
            List<Object> next_statuses = flowEntry.getValue();
            if(containsExclusiveGateway(next_statuses)) {
                try {
                    ExclusiveGateway toExclusiveGateway = (ExclusiveGateway)next_statuses.stream()
                            .filter(o -> o.getClass() == ExclusiveGateway.class)
                            .findAny().orElseThrow(() -> new ObjectNotFoundException("No exclusive gateway could be found"));
                    SequenceFlow sequenceFlowFromToExGw = createSequenceFlow(from.getId(), toExclusiveGateway.getId());
                    sequenceFlows.add(sequenceFlowFromToExGw);
                    addOutGoingFlow(from, sequenceFlowFromToExGw); //from outgoing flow

                    //set outgoing flows for gateway
                    List<SequenceFlow> gatewayOutgoingFlows = new ArrayList<>();
                    next_statuses.stream()
                            .filter(x -> !x.equals(toExclusiveGateway))
                            .collect(Collectors.toList())
                            .forEach(o -> {
                                FlowElement flowElement = (FlowElement) o;
                                SequenceFlow sequenceFlowExGwToUserTask = createSequenceFlow(toExclusiveGateway.getId(), flowElement.getId());
                                sequenceFlows.add(sequenceFlowExGwToUserTask);
                                gatewayOutgoingFlows.add(sequenceFlowExGwToUserTask);
                            });
                    toExclusiveGateway.setOutgoingFlows(gatewayOutgoingFlows);
                } catch (ObjectNotFoundException e) {
                    e.printStackTrace();
                }
            } else if (containsStartEvent(next_statuses)) {
                try {
                    StartEvent startEvent = (StartEvent) next_statuses.stream()
                            .filter(o -> o.getClass() == StartEvent.class)
                            .findAny()
                            .orElseThrow(() -> new ObjectNotFoundException("No startevent could be found in this list"));
                    SequenceFlow sequenceFlowStartEventToFirstEvent = createSequenceFlow(startEvent.getId(), from.getId());//start event comes first
                    sequenceFlows.add(sequenceFlowStartEventToFirstEvent);
                    addOutGoingFlow(startEvent, sequenceFlowStartEventToFirstEvent);

                    next_statuses.stream()
                            .filter(x -> !x.equals(startEvent))
                            .collect(Collectors.toList())
                            .forEach(o -> {
                                FlowElement flowElement = (FlowElement)o;
                                SequenceFlow sequenceFlowFirstEventToNextEvent = createSequenceFlow(from.getId(), flowElement.getId());
                                sequenceFlows.add(sequenceFlowFirstEventToNextEvent);
                                addOutGoingFlow(from, sequenceFlowFirstEventToNextEvent);
                            });
                } catch (ObjectNotFoundException e) {
                    e.printStackTrace();
                }

            } else if (containsEndEvent(next_statuses)) {
                try {
                    EndEvent endEvent = (EndEvent) next_statuses.stream()
                            .filter(o -> o.getClass() == EndEvent.class)
                            .findAny()
                            .orElseThrow(() -> new ObjectNotFoundException("No endevent could be in this list"));
                    SequenceFlow sequenceFlowUserTaskToEndEvent = createSequenceFlow(from.getId(), endEvent.getId());
                    sequenceFlows.add(sequenceFlowUserTaskToEndEvent);
                    addOutGoingFlow(from, sequenceFlowUserTaskToEndEvent);
                } catch (ObjectNotFoundException e) {
                    e.printStackTrace();
                }
            } else {
                next_statuses.forEach(o -> {
                    FlowElement flowElement = (FlowElement)o;
                    SequenceFlow sequenceFlowFromToFlowElement = createSequenceFlow(from.getId(), flowElement.getId());
                    addOutGoingFlow(from, sequenceFlowFromToFlowElement);
                });
            }
        }
        addSequenceFlowsToProcess(sequenceFlows);
    }

    private void addSequenceFlowsToProcess(List<SequenceFlow> sequenceFlows) {
        for (SequenceFlow sequenceFlow : sequenceFlows) {
            process.addFlowElement(sequenceFlow);
        }
    }

    private void updateSequenceMap() {
        for (Status status : workflow.getStatuses()) {
            for (FlowElement flowElement : process.getFlowElements()) {
                if(flowElement instanceof UserTask) {
                    int id = Integer.parseInt(flowElement.getId());
                    if (status.getNextStatusNumber() == id) {
                        List<Object> updatedNextStatuses = statusSequence.get(status.getStatusNumber());
                        updatedNextStatuses.add(flowElement);
                        statusSequence.replace(status.getStatusNumber(), updatedNextStatuses);
                    }
                }
            }
        }
    }

    private void writeModel() {
        String fileName = workflow.getProcessDescription().replaceAll(" ", "_");
        BpmnModel model = new BpmnModel();
        model.addProcess(process);
        new BpmnAutoLayout(model).execute();
        try {
            resetModel();
            byte[] convertToXML = new BpmnXMLConverter().convertToXML(model);
            FileOutputStream fileOuputStream = new FileOutputStream(
                    path + fileName + ".xml");
            fileOuputStream.write(convertToXML);
            fileOuputStream.close();
            FileUtils.copyInputStreamToFile(
                    processEngineConfiguration.getProcessDiagramGenerator().generatePngDiagram(model),
                    new File(path + fileName + ".png"));
        } catch (IOException io) {
            io.printStackTrace();
        }
    }

    private void resetModel() throws IOException {
        String fileName = workflow.getProcessDescription().replaceAll(" ", "_");
        File diagram = new File(path + fileName + ".xml");
        File picture = new File(path + fileName + ".png");
        FileUtils.cleanDirectory(new File(path));
        diagram.createNewFile();
        picture.createNewFile();
    }

    private SequenceFlow createSequenceFlow(String fromId, String toId) {
        SequenceFlow flow = new SequenceFlow();
        flow.setId(String.format("%s_%s", fromId, toId));
        flow.setSourceRef(fromId);
        flow.setTargetRef(toId);
        return flow;
    }

    private StartEvent createStartEvent() {
        StartEvent startEvent = new StartEvent();
        startEvent.setId("start");
        startEvent.setName("start");
        return startEvent;
    }

    private EndEvent createEndEvent() {
        EndEvent endEvent = new EndEvent();
        endEvent.setId("end");
        endEvent.setName("end");
        return endEvent;
    }

    private UserTask createUserTask(int id, String name) {
        UserTask userTask = new UserTask();
        userTask.setId(Integer.toString(id));
        userTask.setName(name);
        return userTask;
    }

    private ExclusiveGateway createExclusiveGateway(String id, String name) {
        ExclusiveGateway exclusiveGateway = new ExclusiveGateway();
        exclusiveGateway.setId(id);
        exclusiveGateway.setName(name);
        return exclusiveGateway;
    }

    private void addOutGoingFlow(FlowElement flowElement, SequenceFlow outGoingFlow) {
        List<SequenceFlow> flowElementSequence = new ArrayList<>();
        flowElementSequence.add(outGoingFlow);
        if(flowElement instanceof StartEvent) {
            ((StartEvent) flowElement).setOutgoingFlows(flowElementSequence);
        } else if (flowElement instanceof ExclusiveGateway) {
            ((ExclusiveGateway) flowElement).setOutgoingFlows(flowElementSequence);
        } else if (flowElement instanceof UserTask) {
            ((UserTask) flowElement).setOutgoingFlows(flowElementSequence);
        }
    }

    @SafeVarargs
    private static <T> Predicate<T> distinctByKeys(Function<? super T, ?>... keyExtractors) {
        //functional interface that returns a Predicate (consequence) if in this case Obj 1 key equals Obj 2 key
        //functional interface is a interface with one abstract method in this case the Equals method
        //used to get the unique values out of a list of classes
        final Map<List<?>, Boolean> seen = new ConcurrentHashMap<>();
        return t ->
        {
            final List<?> keys = Arrays.stream(keyExtractors)
                    .map(ke -> ke.apply(t))
                    .collect(Collectors.toList());
            return seen.putIfAbsent(keys, Boolean.TRUE) == null;
        };
    }

    private boolean isStartEvent(int statusNumber) {
        try {
            Database database = new Database();
            database.start();
            ResultSet result = database.queryStatement(String.format(
                    "SELECT * FROM rainbow.status_tag WHERE STATUSNUMBER = %s;", statusNumber));

            if (result.next()) {
                return true;
            }

        } catch (SQLException sqlEx) {
            sqlEx.printStackTrace();
        }
        return false;
    }

    private boolean containsExclusiveGateway(List<Object> objectList) {
        for (Object object : objectList) {
            if(object instanceof ExclusiveGateway) {
                return true;
            }
        }
        return false;
    }

    private boolean containsStartEvent(List<Object> objectList) {
        for (Object object : objectList) {
            if(object instanceof StartEvent) {
                return true;
            }
        }
        return false;
    }

    private boolean containsEndEvent(List<Object> objectList) {
        for (Object object : objectList) {
            if(object instanceof EndEvent) {
                return true;
            }
        }
        return false;
    }







}
