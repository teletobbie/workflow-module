package com.vaadin;

import javax.servlet.annotation.WebServlet;

import com.vaadin.annotations.Theme;
import com.vaadin.annotations.VaadinServletConfiguration;
import com.vaadin.data.Binder;
import com.vaadin.data.ValidationException;
import com.vaadin.data.converter.StringToIntegerConverter;
import com.vaadin.server.VaadinRequest;
import com.vaadin.server.VaadinServlet;
import com.vaadin.ui.*;

import java.util.ArrayList;

/**
 * This UI is the application entry point. A UI may either represent a browser window 
 * (or tab) or some part of a html page where a Vaadin application is embedded.
 * <p>
 * The UI is initialized using {@link #init(VaadinRequest)}. This method is intended to be 
 * overridden to add component to the user interface and initialize non-component functionality.
 */
@Theme("mytheme")
public class MyUI extends UI {
    private WorkflowService workflowService = WorkflowService.getInstance();
    private BPMNModeller modeller;
    private Workflow workFlow = new Workflow(0, "", new ArrayList<>());

    @Override
    protected void init(VaadinRequest vaadinRequest) {
        final VerticalLayout layout = new VerticalLayout();
        Binder<Workflow> binder = new Binder<>();
        final Grid<Status> grid = new Grid<>(Status.class);
        
        final TextField processIdField = new TextField();
        processIdField.setCaption("Enter a process number here:");

        binder.forField(processIdField)
                .withConverter(new StringToIntegerConverter("Must be a number"))
                .withNullRepresentation(0)
                .bind(Workflow::getId, Workflow::setId);

        binder.readBean(workFlow);

        Button button = new Button("Go");

        button.addClickListener( e -> {
            try {
                workFlow = workflowService.getWorkflow(Integer.parseInt(processIdField.getValue()));
                if (workFlow != null) {
                    //workflowService.createXML(workFlow);
                    modeller = new BPMNModeller(workFlow);
                    modeller.createModel();
                    binder.writeBean(workFlow);
                    layout.addComponent(new Label("process id " + workFlow.getId() + "\n Description " + workFlow.getProcessDescription()));
                    grid.setItems(workFlow.getStatuses());
                    layout.addComponent(grid);
                } else {
                    Notification.show("Workflow with " + processIdField.getValue() + " could not be found");
                }
            } catch (ValidationException ve) {
                Notification.show("Workflow with " + processIdField.getValue() + " could not be found");
            }
        });

        layout.addComponents(processIdField, button);
        
        setContent(layout);

    }


    @WebServlet(urlPatterns = "/*", name = "MyUIServlet", asyncSupported = true)
    @VaadinServletConfiguration(ui = MyUI.class, productionMode = false)
    public static class MyUIServlet extends VaadinServlet {
    }
    
}
