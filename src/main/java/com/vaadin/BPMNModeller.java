package com.vaadin;


import elemental.json.impl.JsonUtil;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.Process;
import org.camunda.bpm.model.bpmn.instance.*;

import java.io.*;
import java.nio.Buffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class BPMNModeller {
    private final String path = "src/main/resources/";
    private Workflow workflow;
    private BpmnModelInstance modelInstance;
    private Definitions definitions;
    private Process process;
    private HashMap<Integer, List<Object>> statusSequence = new HashMap<>();

    public BPMNModeller(Workflow workflow) {
        this.workflow = workflow;
    }

    public void createModel() {
        initializeModel();
        createProcess(); //create process
        initializeSequenceFlows(process);
        writeModel(workflow.getProcessDescription().replace(" ", "_"));
    }

    public File getModelFile() {
        File file = new File(path);
        File[] matchingFiles = file.listFiles((dir, name) ->
                name.startsWith(workflow.getProcessDescription().replace(" ", "_"))
                        && name.endsWith(".xml"));
        //TODO fix null pointer exception
        return matchingFiles[0];
    }

    public String getModelFileContent() {
        StringBuilder contents = new StringBuilder();
        File file = getModelFile();
        try (Stream<String> stream = Files.lines(Paths.get(file.getPath()), StandardCharsets.UTF_8)) {
            stream.forEach(s -> contents.append(s).append("\n"));
        } catch (IOException io) {
            io.printStackTrace();
        }
        return contents.toString();
    }

    private void initializeModel() {
        modelInstance = Bpmn.createEmptyModel();
        definitions = modelInstance.newInstance(Definitions.class);
        definitions.setTargetNamespace(workflow.getProcessDescription());
        modelInstance.setDefinitions(definitions);
        process = createElement(definitions, workflow.getProcessDescription().replace(" ", "-"), Process.class);
    }

    private void createProcess() {
        int counter = 0;
        List<Status> statusList = workflow.getStatuses().stream()
                .filter(distinctByKeys(Status::getStatusNumber, Status::getStatusDescription))
                .collect(Collectors.toList());

        for (Status status : statusList) {
            if (counter == 0) {
                StartEvent startEvent = createElement(process, "_" + status.getStatusNumber(), StartEvent.class);
                startEvent.setName(status.getStatusDescription());
                statusSequence.put(status.getStatusNumber(), new ArrayList<>(Collections.emptyList()));
            } else if (counter == statusList.size() - 1 || status.getNextStatusNumber() == 0) {
                EndEvent endEvent = createElement(process, "_" + status.getStatusNumber(), EndEvent.class);
                endEvent.setName(status.getStatusDescription());
                statusSequence.put(status.getStatusNumber(), new ArrayList<>(Collections.emptyList()));
            } else {
                UserTask task = createElement(process, "_" + status.getStatusNumber(), UserTask.class);
                task.setName(status.getStatusDescription());
                statusSequence.put(status.getStatusNumber(), new ArrayList<>(Collections.emptyList()));
            }
            counter++;
        }

        updateStatusSequence();


    }

    private void updateStatusSequence() {
        for (Status status : workflow.getStatuses()) {
            for (FlowElement flowElement : process.getFlowElements()) {
                int id = Integer.parseInt(flowElement.getId().replaceAll("\\D+", ""));
                if (status.getNextStatusNumber() == id) {
                    List<Object> updatedNextStatuses = statusSequence.get(status.getStatusNumber());
                    updatedNextStatuses.add(flowElement);
                    if (updatedNextStatuses.size() >= 2 && updatedNextStatuses.stream().noneMatch(o -> o instanceof ExclusiveGateway)) {
                        ExclusiveGateway exclusiveGateway = createElement(process, "_" + status.getStatusNumber() + "_gateway", ExclusiveGateway.class);
                        exclusiveGateway.setName(status.getStatusDescription() + " gateway");
                        System.out.println("gateway created named " + exclusiveGateway.getName() + "with id " + exclusiveGateway.getId());
                        updatedNextStatuses.add(exclusiveGateway);
                    }
                    statusSequence.replace(status.getStatusNumber(), updatedNextStatuses);
                }
            }
        }
    }

    private void initializeSequenceFlows(Process process) {
        for (FlowElement from : process.getFlowElements()) {
            int id = Integer.parseInt(from.getId().replaceAll("\\D+", ""));
            List<Object> nextStatuses = statusSequence.get(id);
            for (Object to : nextStatuses) {
                if (from instanceof StartEvent && to instanceof UserTask) {
                    createSequenceFlow(process, (StartEvent) from, (UserTask) to);
                } else if (from instanceof UserTask && to instanceof StartEvent) {
                    createSequenceFlow(process, (UserTask) from, (StartEvent) to);
                } else if (from instanceof StartEvent && to instanceof ExclusiveGateway) {
                    createSequenceFlow(process, (StartEvent) from, (ExclusiveGateway) to);
                } else if (from instanceof ExclusiveGateway && to instanceof StartEvent) {
                    createSequenceFlow(process, (ExclusiveGateway) from, (StartEvent) to);
                } else if (from instanceof EndEvent && to instanceof ExclusiveGateway) {
                    createSequenceFlow(process, (EndEvent) from, (ExclusiveGateway) to);
                } else if (from instanceof ExclusiveGateway && from instanceof EndEvent) {
                    createSequenceFlow(process, (ExclusiveGateway) from, (EndEvent) to);
                } else if (from instanceof UserTask && to instanceof EndEvent) {
                    createSequenceFlow(process, (UserTask) from, (EndEvent) to);
                } else if (from instanceof UserTask && to instanceof UserTask) {
                    createSequenceFlow(process, (UserTask) from, (UserTask) to);
                } else if (from instanceof UserTask && to instanceof ExclusiveGateway) {
                    createSequenceFlow(process, (UserTask) from, (ExclusiveGateway) to);
                } else if (from instanceof ExclusiveGateway && to instanceof UserTask) {
                    createSequenceFlow(process, (ExclusiveGateway) from, (UserTask) to);
                }
            }
        }
    }

    private void writeModel(String modelName) {
        Bpmn.validateModel(modelInstance);
        try {
            Stream<Path> walk = Files.walk(Paths.get(path));
            walk.map(x -> x.toFile())
                    .filter(f -> f.getName().startsWith(modelName))
                    .collect(Collectors.toList())
                    .forEach(File::delete);
            File file = File.createTempFile(modelName, ".xml", new File(path));
            Bpmn.writeModelToFile(file, modelInstance);
        } catch (IOException io) {
            io.printStackTrace();
        }
    }

    /**
     * A little helper method to simplify adding a element to current modelInstance
     *
     * @param parentElement
     * @param id
     * @param elementClass
     * @param <T>
     * @return <T>
     */
    private <T extends BpmnModelElementInstance> T createElement(BpmnModelElementInstance parentElement, String id, Class<T> elementClass) {
        T element = modelInstance.newInstance(elementClass);
        element.setAttributeValue("id", id, true);
        parentElement.addChildElement(element);
        return element;
    }

    /**
     * method to create sequence flow
     *
     * @param process
     * @param from
     * @param to
     * @return
     */
    private SequenceFlow createSequenceFlow(Process process, FlowNode from, FlowNode to) {
        String identifier = from.getId() + "-" + to.getId();
        SequenceFlow sequenceFlow = createElement(process, identifier, SequenceFlow.class);
        process.addChildElement(sequenceFlow);
        sequenceFlow.setSource(from);
        from.getOutgoing().add(sequenceFlow);
        sequenceFlow.setTarget(to);
        to.getIncoming().add(sequenceFlow);
        return sequenceFlow;
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

}
