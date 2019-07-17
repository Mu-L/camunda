/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import org.camunda.optimize.dto.optimize.importing.FlowNodeEventDto;
import org.camunda.optimize.dto.optimize.importing.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.importing.SimpleEventDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.script.Script;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.EVENTS;
import static org.camunda.optimize.service.es.writer.ElasticsearchWriterUtil.createDefaultScript;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROC_INSTANCE_TYPE;

@AllArgsConstructor
@Component
public abstract class AbstractActivityInstanceWriter {
  protected final Logger logger = LoggerFactory.getLogger(getClass());

  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;

  public void importActivityInstances(List<FlowNodeEventDto> events) throws Exception {
    logger.debug("Writing [{}] activity instances to elasticsearch", events.size());

    BulkRequest addEventToProcessInstanceBulkRequest = new BulkRequest();
    Map<String, List<FlowNodeEventDto>> processInstanceToEvents = new HashMap<>();
    for (FlowNodeEventDto e : events) {
      if (!processInstanceToEvents.containsKey(e.getProcessInstanceId())) {
        processInstanceToEvents.put(e.getProcessInstanceId(), new ArrayList<>());
      }
      processInstanceToEvents.get(e.getProcessInstanceId()).add(e);
    }

    for (Map.Entry<String, List<FlowNodeEventDto>> entry : processInstanceToEvents.entrySet()) {
      addActivityInstancesToProcessInstanceRequest(
        addEventToProcessInstanceBulkRequest,
        entry.getValue(),
        entry.getKey()
      );
    }

    BulkResponse bulkResponse = esClient.bulk(addEventToProcessInstanceBulkRequest, RequestOptions.DEFAULT);
    if (bulkResponse.hasFailures()) {
      String errorMessage = String.format(
        "There were failures while writing activity instance with message: %s",
        bulkResponse.buildFailureMessage()
      );
      throw new OptimizeRuntimeException(errorMessage);
    }
  }

  private void addActivityInstancesToProcessInstanceRequest(BulkRequest addEventToProcessInstanceBulkRequest,
                                                            List<FlowNodeEventDto> processEvents,
                                                            String processInstanceId) throws IOException {

    final List<SimpleEventDto> simpleEvents = getSimpleEventDtos(processEvents);
    final Map<String, Object> params = new HashMap<>();
    // see https://discuss.elastic.co/t/how-to-update-nested-objects-in-elasticsearch-2-2-script-via-java-api/43135
    final List jsonMap = objectMapper.readValue(objectMapper.writeValueAsString(simpleEvents), List.class);
    params.put(EVENTS, jsonMap);
    final Script updateScript = createDefaultScript(createInlineUpdateScript(), params);

    final FlowNodeEventDto e = getFirst(processEvents);
    final ProcessInstanceDto procInst = new ProcessInstanceDto()
      .setProcessInstanceId(e.getProcessInstanceId())
      .setEngine(e.getEngineAlias())
      .setEvents(simpleEvents);
    final String newEntryIfAbsent = objectMapper.writeValueAsString(procInst);

    final UpdateRequest request = new UpdateRequest(PROC_INSTANCE_TYPE, PROC_INSTANCE_TYPE, processInstanceId)
      .script(updateScript)
      .upsert(newEntryIfAbsent, XContentType.JSON)
      .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);

    addEventToProcessInstanceBulkRequest.add(request);
  }

  protected abstract String createInlineUpdateScript();

  private FlowNodeEventDto getFirst(List<FlowNodeEventDto> processEvents) {
    return processEvents.get(0);
  }

  private List<SimpleEventDto> getSimpleEventDtos(List<FlowNodeEventDto> processEvents) {
    List<SimpleEventDto> simpleEvents = new ArrayList<>();
    for (FlowNodeEventDto e : processEvents) {
      SimpleEventDto simpleEventDto = new SimpleEventDto();
      simpleEventDto.setDurationInMs(e.getDurationInMs());
      simpleEventDto.setActivityId(e.getActivityId());
      simpleEventDto.setId(e.getId());
      simpleEventDto.setActivityType(e.getActivityType());
      simpleEventDto.setStartDate(e.getStartDate());
      simpleEventDto.setEndDate(e.getEndDate());
      simpleEvents.add(simpleEventDto);
    }
    return simpleEvents;
  }

}