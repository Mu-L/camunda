/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.generator.execution;

public class CompleteUserTaskStep implements ProcessExecutionStep {

  private final String elementId;

  public CompleteUserTaskStep(final String elementId) {
    this.elementId = elementId;
  }

  @Override
  public String getElementId() {
    return elementId;
  }

  @Override
  public String description() {
    return "Complete a user task with element id '%s'.".formatted(elementId);
  }
}
