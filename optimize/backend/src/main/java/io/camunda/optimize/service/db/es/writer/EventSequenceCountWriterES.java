/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.writer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.query.event.sequence.EventSequenceCountDto;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.writer.EventSequenceCountWriter;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.elasticsearch.xcontent.XContentType;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;

@Conditional(ElasticSearchCondition.class)
public class EventSequenceCountWriterES implements EventSequenceCountWriter {

  private static final Logger log =
      org.slf4j.LoggerFactory.getLogger(EventSequenceCountWriterES.class);
  private final String indexKey;
  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;

  public EventSequenceCountWriterES(
      final String indexKey,
      final OptimizeElasticsearchClient esClient,
      final ObjectMapper objectMapper) {
    this.indexKey = indexKey;
    this.esClient = esClient;
    this.objectMapper = objectMapper;
  }

  @Override
  public void updateEventSequenceCountsWithAdjustments(
      final List<EventSequenceCountDto> eventSequenceCountDtos) {
    log.debug(
        "Making adjustments to [{}] event sequence counts in elasticsearch",
        eventSequenceCountDtos.size());
    eventSequenceCountDtos.forEach(EventSequenceCountDto::generateIdForEventSequenceCountDto);

    final BulkRequest bulkRequest = new BulkRequest();
    for (final EventSequenceCountDto eventSequenceCountDto : eventSequenceCountDtos) {
      bulkRequest.add(createEventSequenceCountUpsertRequest(eventSequenceCountDto));
    }

    if (bulkRequest.numberOfActions() > 0) {
      try {
        final BulkResponse bulkResponse = esClient.bulk(bulkRequest);
        if (bulkResponse.hasFailures()) {
          final String errorMessage =
              String.format(
                  "There were failures while writing event sequence counts. Received error message: %s",
                  bulkResponse.buildFailureMessage());
          throw new OptimizeRuntimeException(errorMessage);
        }
      } catch (final IOException e) {
        final String errorMessage = "There were errors while writing event sequence counts.";
        log.error(errorMessage, e);
        throw new OptimizeRuntimeException(errorMessage, e);
      }
    }
  }

  private UpdateRequest createEventSequenceCountUpsertRequest(
      final EventSequenceCountDto eventSequenceCountDto) {
    IndexRequest indexRequest = null;
    try {
      indexRequest =
          new IndexRequest(getIndexName(indexKey))
              .id(eventSequenceCountDto.getId())
              .source(objectMapper.writeValueAsString(eventSequenceCountDto), XContentType.JSON);
    } catch (final JsonProcessingException e) {
      final String errorMessage =
          "There were errors while json processing of creating of the event sequence counts.";
      log.error(errorMessage, e);
    }
    final UpdateRequest updateRequest;
    updateRequest =
        new UpdateRequest()
            .index(getIndexName(indexKey))
            .id(eventSequenceCountDto.getId())
            .script(
                new Script(
                    ScriptType.INLINE,
                    Script.DEFAULT_SCRIPT_LANG,
                    "ctx._source.count += params.adjustmentRequired",
                    Collections.singletonMap(
                        "adjustmentRequired", eventSequenceCountDto.getCount())))
            .upsert(indexRequest);
    return updateRequest;
  }
}
