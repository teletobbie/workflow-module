package com.vaadin;

import com.vaadin.annotations.JavaScript;
import com.vaadin.ui.AbstractJavaScriptComponent;

@JavaScript({"vaadin://js/bpmn.js"})
public class BPMNComponent extends AbstractJavaScriptComponent {
    public void getDiagram() {
        callFunction("getDiagram");
    }
}
