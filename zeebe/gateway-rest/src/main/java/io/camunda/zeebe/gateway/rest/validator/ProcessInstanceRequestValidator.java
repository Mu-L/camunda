/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.validator;

import static io.camunda.zeebe.gateway.rest.validator.ErrorMessages.ERROR_MESSAGE_AT_LEAST_ONE_FIELD;
import static io.camunda.zeebe.gateway.rest.validator.ErrorMessages.ERROR_MESSAGE_INVALID_ATTRIBUTE_VALUE;
import static io.camunda.zeebe.gateway.rest.validator.ErrorMessages.ERROR_MESSAGE_ONLY_ONE_FIELD;
import static io.camunda.zeebe.gateway.rest.validator.RequestValidator.validate;

import io.camunda.zeebe.gateway.protocol.rest.CancelProcessInstanceRequest;
import io.camunda.zeebe.gateway.protocol.rest.CreateProcessInstanceRequest;
import java.util.List;
import java.util.Optional;
import org.springframework.http.ProblemDetail;

public class ProcessInstanceRequestValidator {

  public static Optional<ProblemDetail> validateCreateProcessInstanceRequest(
      final CreateProcessInstanceRequest request) {
    return validate(
        violations -> {
          if (request.getBpmnProcessId() == null && request.getProcessDefinitionKey() == null) {
            violations.add(
                ERROR_MESSAGE_AT_LEAST_ONE_FIELD.formatted(
                    List.of("bpmnProcessId", "processDefinitionKey")));
          }
          if (request.getBpmnProcessId() != null && request.getProcessDefinitionKey() != null) {
            violations.add(
                ERROR_MESSAGE_ONLY_ONE_FIELD.formatted(
                    List.of("bpmnProcessId", "processDefinitionKey")));
          }
          validateOperationReference(request.getOperationReference(), violations);
        });
  }

  public static Optional<ProblemDetail> validateCancelProcessInstanceRequest(
      final CancelProcessInstanceRequest request) {
    return validate(
        violations -> {
          if (request != null) {
            validateOperationReference(request.getOperationReference(), violations);
          }
        });
  }

  private static void validateOperationReference(
      final Long operationReference, final List<String> violations) {
    if (operationReference != null && operationReference < 1) {
      violations.add(
          ERROR_MESSAGE_INVALID_ATTRIBUTE_VALUE.formatted(
              "operationReference", operationReference, "> 0"));
    }
  }
}
