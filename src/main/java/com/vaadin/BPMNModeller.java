package com.vaadin;


import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.Process;
import org.camunda.bpm.model.bpmn.instance.*;


import java.io.File;
import java.io.IOException;
import java.util.*;

public class BPMNModeller {
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
        createProcess();
        initializeSequenceFlows(process);
        writeModel("workflow");
    }

    private void initializeModel() {
        modelInstance = Bpmn.createEmptyModel();
        definitions = modelInstance.newInstance(Definitions.class);
        definitions.setTargetNamespace(workflow.getProcessDescription());
        modelInstance.setDefinitions(definitions);
        process = createElement(definitions, workflow.getProcessDescription(), Process.class);
    }

    private void createProcess() {
        int counter = 0;
        StartEvent startEvent = null;
        EndEvent endEvent = null;
        for (Status status : workflow.getStatuses()) {
            //set events and task
            if (counter == 0) {
                startEvent = createElement(process, Integer.toString(status.getStatusNumber()), StartEvent.class);
                startEvent.setName(status.getStatusDescription());
                addToSequenceMap(status.getStatusNumber(), startEvent);
            } else if (counter == workflow.getStatusesSize() - 1) {
                endEvent = createElement(process, Integer.toString(status.getStatusNumber()), EndEvent.class);
                endEvent.setName(status.getStatusDescription());
                addToSequenceMap(status.getStatusNumber(), endEvent);
            } else {
                UserTask task = createElement(process, Integer.toString(status.getStatusNumber()), UserTask.class);
                task.setName(status.getStatusDescription());
                addToSequenceMap(status.getStatusNumber(), task);
            }
            counter++;
        }

        statusSequence.forEach((k, v) -> System.out.println(k + " " + v));

    }

    private void addToSequenceMap(int key, Object object) {
        if (statusSequence.containsKey(key)) {
            List<Object> updated_next_statuses = statusSequence.get(key);
            updated_next_statuses.add(object);
            statusSequence.replace(key, updated_next_statuses);
        } else {
            ArrayList<Object> next_statuses = new ArrayList<>(Arrays.asList(object));
            statusSequence.put(key, next_statuses);
        }
    }

    private void initializeSequenceFlows(Process process) {

        for (FlowElement flowElement : process.getFlowElements()) {
            String id = flowElement.getId();
            if(flowElement instanceof StartEvent) {
                StartEvent from = modelInstance.getModelElementById(id);
                for (Object sequence : statusSequence.get(Integer.parseInt(id))) {
                    createSequenceFlow(process, from, (FlowNode) sequence);
                }
            } else {
                UserTask from = modelInstance.getModelElementById(id);
                for(Object sequence : statusSequence.get(Integer.parseInt(id))) {
                    if(sequence instanceof EndEvent) {
                        EndEvent to = (EndEvent)sequence;
                        createSequenceFlow(process, from, to);
                    } else {
                        UserTask to = (UserTask)sequence;
                        createSequenceFlow(process, from, to);
                    }
                }
            }
        }
    }

    private void writeModel(String modelName) {
        Bpmn.validateModel(modelInstance);
        try {
            File file = File.createTempFile(modelName, ".bpmn");
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

}
