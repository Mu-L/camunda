/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.data.generation.generators.impl.process;

import io.camunda.optimize.data.generation.UserAndGroupProvider;
import io.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import io.camunda.optimize.test.util.client.SimpleEngineClient;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;

public class BookRequestWithSuspendedInstancesDataGenerator extends ProcessDataGenerator {

  private static final String DIAGRAM = "/diagrams/process/book-request-suspended-instances.bpmn";

  public BookRequestWithSuspendedInstancesDataGenerator(
      final SimpleEngineClient engineClient,
      final Integer nVersions,
      final UserAndGroupProvider userAndGroupProvider) {
    super(engineClient, nVersions, userAndGroupProvider);
  }

  @Override
  protected void startInstance(final String definitionId, final Map<String, Object> variables) {
    addCorrelatingVariable(variables);
    final ProcessInstanceEngineDto processInstance =
        engineClient.startProcessInstance(definitionId, variables, getBusinessKey());
    // randomly suspend some process instances
    final Random rnd = ThreadLocalRandom.current();
    if (rnd.nextBoolean()) {
      engineClient.suspendProcessInstance(processInstance.getId());
    }
  }

  @Override
  protected BpmnModelInstance retrieveDiagram() {
    return readProcessDiagramAsInstance(DIAGRAM);
  }

  @Override
  protected Map<String, Object> createVariables() {
    return new HashMap<>();
  }
}
