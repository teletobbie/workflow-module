async function fetchLocal(url) {
  return new Promise(function(resolve, reject){
    const xhr = new XMLHttpRequest();
    xhr.onload = function() {
      resolve(new Response(xhr.responseText, {status: xhr.status}))
    };
    xhr.onerror = function() {
      reject(new TypeError('Local request failed'))
    };
    xhr.open('GET', url);
    xhr.send(null)
  })
}

var diagramUrl = 'https://cdn.staticaly.com/gh/bpmn-io/bpmn-js-examples/dfceecba/starter/diagram.bpmn';
var bpmnViewer = new BpmnJS({
  container: '#canvas'
});

function openDiagram(bpmnXML) {
  bpmnViewer.importXML(bpmnXML, function (err) {

    if (err) {
      return console.error('could not import BPMN 2.0 diagram', err);
    }
    var canvas = bpmnViewer.get('canvas');
    canvas.zoom('fit-viewport');
  });
}

$.get(diagramUrl, openDiagram, 'text');

window.com_vaadin_BPMNComponent = function() {
  alert("I am in the function!");
  var diagramUrl = 'https://cdn.staticaly.com/gh/bpmn-io/bpmn-js-examples/dfceecba/starter/diagram.bpmn';
  var bpmnViewer = new BpmnJS({
    container: '#canvas'
  });

  this.getDiagram = function() {
    function openDiagram(bpmnXML) {
      bpmnViewer.importXML(bpmnXML, function (err) {

        if (err) {
          return console.error('could not import BPMN 2.0 diagram', err);
        }
        var canvas = bpmnViewer.get('canvas');
        canvas.zoom('fit-viewport');
      });
    }

    $.get(diagramUrl, openDiagram, 'text');
  }
};



