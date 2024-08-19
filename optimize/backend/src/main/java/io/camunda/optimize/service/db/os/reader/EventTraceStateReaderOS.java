/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.reader;

import static io.camunda.optimize.service.db.DatabaseConstants.LIST_FETCH_LIMIT;
import static io.camunda.optimize.service.db.DatabaseConstants.MAX_RESPONSE_SIZE_LIMIT;
import static io.camunda.optimize.service.db.schema.index.events.EventTraceStateIndex.EVENT_NAME;
import static io.camunda.optimize.service.db.schema.index.events.EventTraceStateIndex.EVENT_TRACE;
import static io.camunda.optimize.service.db.schema.index.events.EventTraceStateIndex.GROUP;
import static io.camunda.optimize.service.db.schema.index.events.EventTraceStateIndex.SOURCE;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.query.event.process.EventTypeDto;
import io.camunda.optimize.dto.optimize.query.event.sequence.EventTraceStateDto;
import io.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import io.camunda.optimize.service.db.os.externalcode.client.dsl.QueryDSL;
import io.camunda.optimize.service.db.reader.EventTraceStateReader;
import io.camunda.optimize.service.db.schema.index.events.EventTraceStateIndex;
import java.util.List;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery.Builder;
import org.opensearch.client.opensearch._types.query_dsl.ChildScoreMode;
import org.opensearch.client.opensearch._types.query_dsl.FunctionScore;
import org.opensearch.client.opensearch._types.query_dsl.FunctionScoreQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.RandomScoreFunction;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.slf4j.Logger;

public class EventTraceStateReaderOS implements EventTraceStateReader {

  private static final Logger log =
      org.slf4j.LoggerFactory.getLogger(EventTraceStateReaderOS.class);
  private final String indexKey;
  private final OptimizeOpenSearchClient osClient;
  private final ObjectMapper objectMapper;

  public EventTraceStateReaderOS(
      final String indexKey,
      final OptimizeOpenSearchClient osClient,
      final ObjectMapper objectMapper) {
    this.indexKey = indexKey;
    this.osClient = osClient;
    this.objectMapper = objectMapper;
  }

  @Override
  public List<EventTraceStateDto> getEventTraceStateForTraceIds(final List<String> traceIds) {
    log.debug("Fetching event trace states for trace ids {}", traceIds);
    final SearchRequest.Builder searchRequest =
        new SearchRequest.Builder()
            .index(getIndexName(indexKey))
            .size(LIST_FETCH_LIMIT)
            .query(QueryDSL.stringTerms(EventTraceStateIndex.TRACE_ID, traceIds));

    final String errorMsg =
        String.format("Was not able to fetch event trace states with trace ids [%s]", traceIds);
    final SearchResponse<EventTraceStateDto> searchResponse =
        osClient.search(searchRequest, EventTraceStateDto.class, errorMsg);

    return searchResponse.hits().hits().stream().map(Hit::source).toList();
  }

  @Override
  public List<EventTraceStateDto> getTracesContainingAtLeastOneEventFromEach(
      final List<EventTypeDto> startEvents,
      final List<EventTypeDto> endEvents,
      final int maxResultsSize) {
    log.debug(
        "Fetching up to {} random event trace states containing given events", maxResultsSize);

    final Query containsStartEventQuery = createContainsAtLeastOneEventFromQuery(startEvents);
    final Query containsEndEventQuery = createContainsAtLeastOneEventFromQuery(endEvents);
    final Query functionScoreQuery =
        new FunctionScoreQuery.Builder()
            .query(QueryDSL.and(containsStartEventQuery, containsEndEventQuery))
            .functions(
                new FunctionScore.Builder()
                    .randomScore(new RandomScoreFunction.Builder().build())
                    .build())
            .build()
            .toQuery();
    final SearchRequest.Builder searchRequest =
        new SearchRequest.Builder()
            .index(getIndexName(indexKey))
            .query(functionScoreQuery)
            .size(maxResultsSize);
    final SearchResponse<EventTraceStateDto> searchResponse =
        osClient.search(
            searchRequest, EventTraceStateDto.class, "Was not able to fetch event trace states");
    return searchResponse.hits().hits().stream().map(Hit::source).toList();
  }

  @Override
  public List<EventTraceStateDto> getTracesWithTraceIdIn(final List<String> traceIds) {
    log.debug("Fetching trace states with trace ID in [{}]", traceIds);

    final SearchRequest.Builder searchRequest =
        new SearchRequest.Builder()
            .query(QueryDSL.stringTerms(EventTraceStateIndex.TRACE_ID, traceIds))
            .size(MAX_RESPONSE_SIZE_LIMIT)
            .index(getIndexName(indexKey));

    final SearchResponse<EventTraceStateDto> searchResponse =
        osClient.search(
            searchRequest,
            EventTraceStateDto.class,
            "Was not able to fetch event trace states for given trace IDs");
    return searchResponse.hits().hits().stream().map(Hit::source).toList();
  }

  private Query createContainsAtLeastOneEventFromQuery(final List<EventTypeDto> startEvents) {
    final BoolQuery.Builder containStartEventQuery = new Builder();
    startEvents.forEach(
        startEvent -> {
          final BoolQuery.Builder containsEventQuery =
              new Builder()
                  .must(QueryDSL.term(getEventTraceNestedField(SOURCE), startEvent.getSource()))
                  .must(
                      QueryDSL.term(
                          getEventTraceNestedField(EVENT_NAME), startEvent.getEventName()));
          if (startEvent.getGroup() == null) {
            containsEventQuery.mustNot(QueryDSL.exists(getEventTraceNestedField(GROUP)));
          } else {
            containsEventQuery.must(
                QueryDSL.term(getEventTraceNestedField(GROUP), startEvent.getGroup()));
          }
          containStartEventQuery.should(
              QueryDSL.nested(
                  EVENT_TRACE, containsEventQuery.build().toQuery(), ChildScoreMode.None));
        });
    return containStartEventQuery.build().toQuery();
  }
}
