package org.camunda.optimize.data.generation.generators.impl;

import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.data.generation.generators.DataGenerator;
import org.camunda.optimize.data.generation.generators.client.SimpleEngineClient;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class ContactInterviewDataGenerator extends DataGenerator {

  private static final String DIAGRAM = "diagrams/contact-interview.bpmn";

  public ContactInterviewDataGenerator(SimpleEngineClient engineClient) {
    super(engineClient);
  }

  protected BpmnModelInstance retrieveDiagram() {
    try {
      return readDiagramAsInstance(DIAGRAM);
    } catch (IOException e) {
      e.printStackTrace();
    }
    return null;
  }

  public Set<String> getPathVariableNames() {
    return new HashSet<>();
  }

}
