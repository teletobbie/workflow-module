package com.vaadin;


import org.activiti.bpmn.BpmnAutoLayout;
import org.activiti.bpmn.converter.BpmnXMLConverter;
import org.activiti.bpmn.model.Process;
import org.activiti.bpmn.model.*;
import org.activiti.engine.ProcessEngine;
import org.activiti.engine.ProcessEngineConfiguration;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
                StartEvent startEvent = createStartEvent(status.getStatusNumber(), status.getStatusDescription());
                process.addFlowElement(startEvent);
                statusSequence.put(status.getStatusNumber(), new ArrayList<>(Collections.emptyList()));
            } else if (status.getNextStatusNumber() == 0) {
                EndEvent endEvent = createEndEvent(status.getStatusNumber(), status.getStatusDescription());
                process.addFlowElement(endEvent);
                statusSequence.put(status.getStatusNumber(), new ArrayList<>(Collections.emptyList()));
            } else {
                UserTask userTask = createUserTask(status.getStatusNumber(), status.getStatusDescription());
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
        System.out.println(statusSequence);


    }

    private void createSequences() {
        //1. loop de sequencemap values
        //2. loop over de process.flowelements
        //3.
        List<SequenceFlow> sequenceFlows = new ArrayList<>(); //list of incoming sequenceflows

        for (FlowElement from : process.getFlowElements()) {
            int id = Integer.parseInt(from.getId().replaceAll("\\D+", ""));
            List<Object> nextStatuses = statusSequence.get(id);

            for (Object to : nextStatuses) {
                if (from instanceof StartEvent && to instanceof UserTask) {
                    SequenceFlow flow = createSequenceFlow(from.getId(), ((UserTask) to).getId());
                    sequenceFlows.add(flow);
                    List<SequenceFlow> startEventOutgoingFlows = new ArrayList<>();
                    startEventOutgoingFlows.add(flow);
                    ((StartEvent) from).setOutgoingFlows(startEventOutgoingFlows);
                } else if (from instanceof UserTask && to instanceof StartEvent) {
                    SequenceFlow flow = createSequenceFlow(from.getId(), ((StartEvent) to).getId());
                    sequenceFlows.add(flow);
                    List<SequenceFlow> userTaskOutgoingFlows = new ArrayList<>();
                    userTaskOutgoingFlows.add(flow);
                    ((UserTask) from).setOutgoingFlows(userTaskOutgoingFlows);
                } else if (from instanceof StartEvent && to instanceof ExclusiveGateway) {
                    SequenceFlow flow = createSequenceFlow(from.getId(), ((ExclusiveGateway) to).getId());
                    sequenceFlows.add(flow);
                    List<SequenceFlow> startEventOutgoingFlows = new ArrayList<>();
                    startEventOutgoingFlows.add(flow);
                    ((StartEvent) from).setOutgoingFlows(startEventOutgoingFlows);
                } else if (from instanceof ExclusiveGateway && to instanceof StartEvent) {
                    SequenceFlow flow = createSequenceFlow(from.getId(), ((StartEvent) to).getId());
                    sequenceFlows.add(flow);
                    List<SequenceFlow> gatewayOutgoingFlows = new ArrayList<>();
                    gatewayOutgoingFlows.add(flow);
                    ((ExclusiveGateway) from).setOutgoingFlows(gatewayOutgoingFlows);
                } else if (from instanceof EndEvent && to instanceof ExclusiveGateway) {
                    SequenceFlow flow = createSequenceFlow(from.getId(), ((ExclusiveGateway) to).getId());
                    sequenceFlows.add(flow);
                    List<SequenceFlow> endEventOutgoingFlows = new ArrayList<>();
                    endEventOutgoingFlows.add(flow);
                    ((EndEvent) from).setOutgoingFlows(endEventOutgoingFlows);
                } else if (from instanceof ExclusiveGateway && to instanceof EndEvent) {
                    SequenceFlow flow = createSequenceFlow(from.getId(), ((EndEvent) to).getId());
                    sequenceFlows.add(flow);
                    List<SequenceFlow> gatewayOutgoingFlows = new ArrayList<>();
                    gatewayOutgoingFlows.add(flow);
                    ((ExclusiveGateway) from).setOutgoingFlows(gatewayOutgoingFlows);
                } else if (from instanceof UserTask && to instanceof EndEvent) {
                    SequenceFlow flow = createSequenceFlow(from.getId(), ((EndEvent) to).getId());
                    sequenceFlows.add(flow);
                    List<SequenceFlow> userTaskOutgoingFlows = new ArrayList<>();
                    userTaskOutgoingFlows.add(flow);
                    ((UserTask) from).setOutgoingFlows(userTaskOutgoingFlows);
                } else if (from instanceof UserTask && to instanceof UserTask) {
                    SequenceFlow flow = createSequenceFlow(from.getId(), ((UserTask) to).getId());
                    sequenceFlows.add(flow);
                    List<SequenceFlow> userTaskOutgoingFlows = new ArrayList<>();
                    userTaskOutgoingFlows.add(flow);
                    ((UserTask) from).setOutgoingFlows(userTaskOutgoingFlows);
                } else if (from instanceof UserTask && to instanceof ExclusiveGateway) {
                    SequenceFlow flow = createSequenceFlow(from.getId(), ((ExclusiveGateway) to).getId());
                    sequenceFlows.add(flow);
                    List<SequenceFlow> userTaskOutgoingFlows = new ArrayList<>();
                    userTaskOutgoingFlows.add(flow);
                    ((UserTask) from).setOutgoingFlows(userTaskOutgoingFlows);
                } else if (from instanceof ExclusiveGateway && to instanceof UserTask) {
                    SequenceFlow flow = createSequenceFlow(from.getId(), ((UserTask) to).getId());
                    sequenceFlows.add(flow);
                    List<SequenceFlow> gatewayOutgoingFlows = new ArrayList<>();
                    gatewayOutgoingFlows.add(flow);
                    ((ExclusiveGateway) from).setOutgoingFlows(gatewayOutgoingFlows);
                }
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
                if(!flowElement.getId().contains("xg")) {
                    int id = Integer.parseInt(flowElement.getId().replaceAll("\\D+", ""));
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

    private StartEvent createStartEvent(int id, String name) {
        StartEvent startEvent = new StartEvent();
        startEvent.setId(Integer.toString(id));
        startEvent.setName(name);
        return startEvent;
    }

    private EndEvent createEndEvent(int id, String name) {
        EndEvent endEvent = new EndEvent();
        endEvent.setId(Integer.toString(id));
        endEvent.setName(name);
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


//    public BPMNModeller(Workflow workflow) {
//        this.workflow = workflow;
//    }
//
//    public void createModel() {
//
//        initializeModel();
//        createProcess(); //create process
//        initializeSequenceFlows(process);
//        writeModel(workflow.getProcessDescription().replace(" ", "_"));
//    }
//
//    public File getModelFile() {
//        File file = new File(path);
//        File[] matchingFiles = file.listFiles((dir, name) ->
//                name.startsWith(workflow.getProcessDescription().replace(" ", "_"))
//                        && name.endsWith(".xml"));
//        //TODO fix null pointer exception
//        return matchingFiles[0];
//    }
//
//    public String getModelFileContent() {
//        StringBuilder contents = new StringBuilder();
//        File file = getModelFile();
//        try (Stream<String> stream = Files.lines(Paths.get(file.getPath()), StandardCharsets.UTF_8)) {
//            stream.forEach(s -> contents.append(s).append("\n"));
//        } catch (IOException io) {
//            io.printStackTrace();
//        }
//        return contents.toString();
//    }
//
//
//    private void initializeModel() {
//        modelInstance = Bpmn.createEmptyModel();
//        definitions = modelInstance.newInstance(Definitions.class);
//        definitions.setTargetNamespace(workflow.getProcessDescription());
//        modelInstance.setDefinitions(definitions);
//        process = createElement(definitions, workflow.getProcessDescription().replace(" ", "-"), Process.class);
//    }
//
//    private void createProcess() {
//        int counter = 0;
//        List<Status> statusList = workflow.getStatuses().stream()
//                .filter(distinctByKeys(Status::getStatusNumber, Status::getStatusDescription))
//                .collect(Collectors.toList());
//
//        for (Status status : statusList) {
//            if (counter == 0) {
//                StartEvent startEvent = createElement(process, "_" + status.getStatusNumber(), StartEvent.class);
//                startEvent.setName(status.getStatusDescription());
//                statusSequence.put(status.getStatusNumber(), new ArrayList<>(Collections.emptyList()));
//            } else if (counter == statusList.size() - 1 || status.getNextStatusNumber() == 0) {
//                EndEvent endEvent = createElement(process, "_" + status.getStatusNumber(), EndEvent.class);
//                endEvent.setName(status.getStatusDescription());
//                statusSequence.put(status.getStatusNumber(), new ArrayList<>(Collections.emptyList()));
//            } else {
//                UserTask task = createElement(process, "_" + status.getStatusNumber(), UserTask.class);
//                task.setName(status.getStatusDescription());
//                statusSequence.put(status.getStatusNumber(), new ArrayList<>(Collections.emptyList()));
//            }
//            counter++;
//        }
//
//        updateStatusSequence();
//    }
//
//    private void updateStatusSequence() {
//        for (Status status : workflow.getStatuses()) {
//            for (FlowElement flowElement : process.getFlowElements()) {
//                int id = Integer.parseInt(flowElement.getId().replaceAll("\\D+", ""));
//                if (status.getNextStatusNumber() == id) {
//                    List<Object> updatedNextStatuses = statusSequence.get(status.getStatusNumber());
//                    updatedNextStatuses.add(flowElement);
//                    if (updatedNextStatuses.size() >= 2 && updatedNextStatuses.stream().noneMatch(o -> o instanceof ExclusiveGateway)) {
//                        ExclusiveGateway exclusiveGateway = createElement(process, "_" + status.getStatusNumber() + "_gateway", ExclusiveGateway.class);
//                        exclusiveGateway.setName(status.getStatusDescription() + " gateway");
//                        System.out.println("gateway created named " + exclusiveGateway.getName() + "with id " + exclusiveGateway.getId());
//                        updatedNextStatuses.add(exclusiveGateway);
//                    }
//                    statusSequence.replace(status.getStatusNumber(), updatedNextStatuses);
//                }
//            }
//        }
//    }
//
//    private void initializeSequenceFlows(Process process) {
//        for (FlowElement from : process.getFlowElements()) {
//            int id = Integer.parseInt(from.getId().replaceAll("\\D+", ""));
//            List<Object> nextStatuses = statusSequence.get(id);
//            for (Object to : nextStatuses) {
//                if (from instanceof StartEvent && to instanceof UserTask) {
//                    createSequenceFlow(process, (StartEvent) from, (UserTask) to);
//                } else if (from instanceof UserTask && to instanceof StartEvent) {
//                    createSequenceFlow(process, (UserTask) from, (StartEvent) to);
//                } else if (from instanceof StartEvent && to instanceof ExclusiveGateway) {
//                    createSequenceFlow(process, (StartEvent) from, (ExclusiveGateway) to);
//                } else if (from instanceof ExclusiveGateway && to instanceof StartEvent) {
//                    createSequenceFlow(process, (ExclusiveGateway) from, (StartEvent) to);
//                } else if (from instanceof EndEvent && to instanceof ExclusiveGateway) {
//                    createSequenceFlow(process, (EndEvent) from, (ExclusiveGateway) to);
//                } else if (from instanceof ExclusiveGateway && from instanceof EndEvent) {
//                    createSequenceFlow(process, (ExclusiveGateway) from, (EndEvent) to);
//                } else if (from instanceof UserTask && to instanceof EndEvent) {
//                    createSequenceFlow(process, (UserTask) from, (EndEvent) to);
//                } else if (from instanceof UserTask && to instanceof UserTask) {
//                    createSequenceFlow(process, (UserTask) from, (UserTask) to);
//                } else if (from instanceof UserTask && to instanceof ExclusiveGateway) {
//                    createSequenceFlow(process, (UserTask) from, (ExclusiveGateway) to);
//                } else if (from instanceof ExclusiveGateway && to instanceof UserTask) {
//                    createSequenceFlow(process, (ExclusiveGateway) from, (UserTask) to);
//                }
//            }
//        }
//    }
//
//    private void writeModel(String modelName) {
//        Bpmn.validateModel(modelInstance);
//        try {
//            Stream<Path> walk = Files.walk(Paths.get(path));
//            walk.map(x -> x.toFile())
//                    .filter(f -> f.getName().startsWith(modelName))
//                    .collect(Collectors.toList())
//                    .forEach(File::delete);
//            File file = File.createTempFile(modelName, ".bpmn", new File(path));
//            Bpmn.writeModelToStream();
//            Bpmn.writeModelToFile(file, modelInstance);
//        } catch (IOException io) {
//            io.printStackTrace();
//        }
//    }
//
//
//
//    /**
//     * A little helper method to simplify adding a element to current modelInstance
//     *
//     * @param parentElement
//     * @param id
//     * @param elementClass
//     * @param <T>
//     * @return <T>
//     */
//    private <T extends BpmnModelElementInstance> T createElement(BpmnModelElementInstance parentElement, String id, Class<T> elementClass) {
//        T element = modelInstance.newInstance(elementClass);
//        element.setAttributeValue("id", id, true);
//        parentElement.addChildElement(element);
//        return element;
//    }
//
//    /**
//     * method to create sequence flow
//     *
//     * @param process
//     * @param from
//     * @param to
//     * @return
//     */
//    private SequenceFlow createSequenceFlow(Process process, FlowNode from, FlowNode to) {
//        String identifier = from.getId() + "-" + to.getId();
//        SequenceFlow sequenceFlow = createElement(process, identifier, SequenceFlow.class);
//        process.addChildElement(sequenceFlow);
//        sequenceFlow.setSource(from);
//        from.getOutgoing().add(sequenceFlow);
//        sequenceFlow.setTarget(to);
//        to.getIncoming().add(sequenceFlow);
//        return sequenceFlow;
//    }

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


}
