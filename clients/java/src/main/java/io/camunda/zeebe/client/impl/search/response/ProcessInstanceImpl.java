/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.zeebe.client.impl.search.response;

import io.camunda.client.protocol.rest.ProcessInstanceItem;
import io.camunda.zeebe.client.api.search.response.ProcessInstance;

public class ProcessInstanceImpl implements ProcessInstance {

  private final String tenantId;
  private final Long key;
  private final Long processDefinitionKey;
  private final Integer processVersion;
  private final String bpmnProcessId;
  private final String startDate;
  private final String endDate;

  public ProcessInstanceImpl(final ProcessInstanceItem item) {
    tenantId = item.getTenantId();
    key = item.getKey();
    processDefinitionKey = item.getProcessDefinitionKey();
    processVersion = item.getProcessVersion();
    bpmnProcessId = item.getBpmnProcessId();
    startDate = item.getStartDate();
    endDate = item.getEndDate();
  }

  @Override
  public long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  @Override
  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  @Override
  public int getVersion() {
    return processVersion;
  }

  @Override
  public long getProcessInstanceKey() {
    return key;
  }

  @Override
  public String getTenantId() {
    return tenantId;
  }

  @Override
  public String getStartDate() {
    return startDate;
  }

  @Override
  public String getEndDate() {
    return endDate;
  }
}
