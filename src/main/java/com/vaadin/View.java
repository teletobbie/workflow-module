package com.vaadin;

import com.vaadin.annotations.JavaScript;
import com.vaadin.annotations.Theme;
import com.vaadin.annotations.VaadinServletConfiguration;
import com.vaadin.data.Binder;
import com.vaadin.data.ValidationException;
import com.vaadin.data.converter.StringToIntegerConverter;
import com.vaadin.server.FileResource;
import com.vaadin.server.VaadinRequest;
import com.vaadin.server.VaadinServlet;
import com.vaadin.ui.*;

import javax.servlet.annotation.WebServlet;
import java.io.File;
import java.util.ArrayList;

/**
 * This UI is the application entry point. A UI may either represent a browser window
 * (or tab) or some part of a html page where a Vaadin application is embedded.
 * <p>
 * The UI is initialized using {@link #init(VaadinRequest)}. This method is intended to be
 * overridden to add component to the user interface and initialize non-component functionality.
 */
@Theme("mytheme")
@JavaScript({"https://unpkg.com/bpmn-js@6.3.1/dist/bpmn-navigated-viewer.development.js", "https://unpkg.com/jquery@3.3.1/dist/jquery.js"})
public class View extends UI {
    private WorkflowService workflowService = WorkflowService.getInstance();
    private Workflow workFlow = new Workflow(0, "", new ArrayList<>());
    private Grid<Status> grid = new Grid<>(Status.class);
    private TextField processIdField = new TextField();
    private Binder<Workflow> binder = new Binder<>();

    @Override
    protected void init(VaadinRequest vaadinRequest) {
        final VerticalLayout layout = new VerticalLayout();
        final HorizontalLayout toolbar = new HorizontalLayout();

        processIdField.setPlaceholder("Fill in a process number...");

        binder.forField(processIdField)
                .withConverter(new StringToIntegerConverter("Must be a number"))
                .withNullRepresentation(0)
                .bind(Workflow::getId, Workflow::setId);
        binder.readBean(workFlow);

        VerticalLayout main = new VerticalLayout();

        Button button = new Button("Go");
        button.addClickListener(e -> {
            updateWorkflow(main);
        });

        toolbar.addComponents(processIdField, button);

        layout.addComponents(toolbar, main);

        main.setSizeFull();
        grid.setSizeFull();
        setContent(layout);

    }

    private void updateWorkflow(VerticalLayout layout) {
        try {
            workFlow = workflowService.getWorkflow(Integer.parseInt(processIdField.getValue()));
            if (workFlow != null) {
                workflowService.createBPMNDiagram(workFlow);

                binder.writeBean(workFlow);
                grid.setItems(workFlow.getStatuses());

                HorizontalLayout horizontalLayout = new HorizontalLayout();

                CustomLayout content = new CustomLayout("canvas");
                content.setId("canvas");
                content.setSizeFull();
                horizontalLayout.addComponents(grid, content);

                renderBPMNDiagram(String.format("src/main/resources/%s.bpmn",
                        workFlow.getProcessDescription().replace(" ", "_")));

                FileResource imageResource = new FileResource(new File(
                        String.format("src/main/resources/%s.png",
                                workFlow.getProcessDescription().replace(" ", "_"))));
                Image workflowImage = new Image(workFlow.getProcessDescription(), imageResource);

                layout.addComponents(horizontalLayout, workflowImage);

            } else {
                Notification.show("Workflow with " + processIdField.getValue() + " could not be found");
            }
        } catch (ValidationException ve) {
            Notification.show("Workflow with " + processIdField.getValue() + " is not valid");
        }
    }

    private void renderBPMNDiagram(String path) {
        com.vaadin.ui.JavaScript.getCurrent().execute("" +
                "var diagramUrl = 'https://cdn.staticaly.com/gh/bpmn-io/bpmn-js-examples/dfceecba/starter/diagram.bpmn';\n" +
                "      var bpmnViewer = new BpmnJS({\n" +
                "        container: '#canvas'\n" +
                "      });\n" +
                "      function openDiagram(bpmnXML) {\n" +
                "        bpmnViewer.importXML(bpmnXML, function(err) {\n" +
                "          if (err) {\n" +
                "            return console.error('could not import BPMN 2.0 diagram', err);\n" +
                "          }\n" +
                "          var canvas = bpmnViewer.get('canvas');\n" +
                "          var overlays = bpmnViewer.get('overlays');\n" +
                "          canvas.zoom('fit-viewport');\n" +
                "        });\n" +
                "      }\n" +
                "      $.get(diagramUrl, openDiagram, 'text');");
    }


    @WebServlet(urlPatterns = "/*", name = "MyUIServlet", asyncSupported = true)
    @VaadinServletConfiguration(ui = View.class, productionMode = false)
    public static class MyUIServlet extends VaadinServlet {
    }


}
