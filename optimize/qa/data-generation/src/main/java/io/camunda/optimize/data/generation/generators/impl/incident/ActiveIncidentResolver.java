/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.data.generation.generators.impl.incident;

import io.camunda.optimize.dto.optimize.query.variable.VariableType;
import io.camunda.optimize.test.util.client.SimpleEngineClient;
import io.camunda.optimize.test.util.client.dto.EngineIncidentDto;
import io.camunda.optimize.test.util.client.dto.VariableValueDto;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ActiveIncidentResolver implements IncidentResolver {

  private final SimpleEngineClient engineClient;

  @Override
  public void resolveIncidents() {
    final List<EngineIncidentDto> first100Incidents = engineClient.getFirst100Incidents();
    if (first100Incidents.isEmpty()) {
      return;
    }

    // we take only half of the incidents to complete to have some open ones left
    final List<EngineIncidentDto> incidentsToComplete =
        first100Incidents.subList(0, first100Incidents.size() / 2);
    final List<String> processInstanceIdsToRetry = new ArrayList<>();
    incidentsToComplete.stream()
        .peek(incident -> processInstanceIdsToRetry.add(incident.getProcessInstanceId()))
        .forEach(
            incident -> {
              final VariableValueDto variableValueDto = new VariableValueDto();
              variableValueDto.setType(VariableType.BOOLEAN);
              variableValueDto.setValue(true);
              // the incident in the process is being generated by accessing a variable
              // 'missingVariable' which
              // is not available during the start of the process. By adding it afterwards and then
              // increasing the
              // retry the incident can be resolved.
              engineClient.addVariableToProcessInstance(
                  incident.getProcessInstanceId(), "missingVariable", variableValueDto);
            });
    engineClient.increaseJobRetry(processInstanceIdsToRetry);
  }
}
