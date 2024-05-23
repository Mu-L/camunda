/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.opensearch.transform;

import static io.camunda.operate.schema.templates.OperationTemplate.BATCH_OPERATION_ID;
import static io.camunda.operate.schema.templates.OperationTemplate.BATCH_OPERATION_ID_AGGREGATION;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.stringTerms;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.term;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.QueryType.ALL;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.searchRequestBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.entities.BatchOperationEntity;
import io.camunda.operate.entities.OperationEntity;
import io.camunda.operate.entities.OperationState;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.schema.templates.BatchOperationTemplate;
import io.camunda.operate.schema.templates.OperationTemplate;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.store.opensearch.dsl.AggregationDSL;
import io.camunda.operate.webapp.opensearch.reader.OpensearchOperationReader;
import io.camunda.operate.webapp.rest.dto.operation.BatchOperationDto;
import io.camunda.operate.webapp.transform.DataAggregator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.opensearch.client.opensearch._types.aggregations.Aggregate;
import org.opensearch.client.opensearch._types.aggregations.StringTermsAggregate;
import org.opensearch.client.opensearch._types.aggregations.StringTermsBucket;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchRequest.Builder;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchDataAggregator implements DataAggregator {

  private static final Logger LOGGER = LoggerFactory.getLogger(OpensearchDataAggregator.class);
  @Autowired protected ObjectMapper objectMapper;
  @Autowired protected RichOpenSearchClient richOpenSearchClient;
  @Autowired private OpensearchOperationReader operationReader;
  @Autowired private OperationTemplate operationTemplate;
  @Autowired private BatchOperationTemplate batchOperationTemplate;

  @Override
  public List<BatchOperationDto> enrichBatchEntitiesWithMetadata(
      final List<BatchOperationEntity> batchEntities) {

    final List<BatchOperationDto> resultDtos = new ArrayList<>(batchEntities.size());
    if (batchEntities.isEmpty()) {
      return resultDtos;
    }

    final Map<String, BatchOperationEntity> entityMap =
        batchEntities.stream()
            .collect(Collectors.toMap(BatchOperationEntity::getId, entity -> entity));
    final List<String> idList = entityMap.keySet().stream().toList();

    final var searchRequestBuilder = getSearchRequestByIdWithMetadata(idList);
    final StringTermsAggregate idAggregate;

    try {
      final SearchResponse<OperationEntity> searchResponse =
          richOpenSearchClient.doc().search(searchRequestBuilder, OperationEntity.class);
      idAggregate = searchResponse.aggregations().get(BATCH_OPERATION_ID_AGGREGATION).sterms();
      for (final StringTermsBucket bucket : idAggregate.buckets().array()) {
        final Aggregate metadataAggregate =
            bucket.aggregations().get(OperationTemplate.METADATA_AGGREGATION);

        final Integer failedCount =
            (int)
                metadataAggregate
                    .filters()
                    .buckets()
                    .keyed()
                    .get(BatchOperationTemplate.FAILED_OPERATIONS_COUNT)
                    .docCount();
        final Integer completedCount =
            (int)
                metadataAggregate
                    .filters()
                    .buckets()
                    .keyed()
                    .get(BatchOperationTemplate.COMPLETED_OPERATIONS_COUNT)
                    .docCount();

        final BatchOperationDto batchOperationDto =
            BatchOperationDto.createFrom(entityMap.get(bucket.key()), objectMapper)
                .setFailedOperationsCount(failedCount)
                .setCompletedOperationsCount(completedCount);
        resultDtos.add(batchOperationDto);
      }
    } catch (final OperateRuntimeException e) {
      final String message =
          String.format(
              "Exception occurred, while searching for batch operation metadata.", e.getMessage());
      LOGGER.error(message, e);
      throw new OperateRuntimeException(message, e);
    }

    return resultDtos;
  }

  public Builder getSearchRequestByIdWithMetadata(final List<String> batchOperationIds) {
    final Query idsQuery = stringTerms(BATCH_OPERATION_ID, batchOperationIds);
    final Query failedOperationQuery = term(OperationTemplate.STATE, OperationState.FAILED.name());
    final Query completedOperationQuery =
        term(OperationTemplate.STATE, OperationState.COMPLETED.name());
    final var searchRequestBuilder =
        searchRequestBuilder(operationTemplate, ALL)
            .query(idsQuery)
            .aggregations(
                BATCH_OPERATION_ID_AGGREGATION,
                AggregationDSL.withSubaggregations(
                    AggregationDSL.termAggregation(BATCH_OPERATION_ID, batchOperationIds.size()),
                    Map.of(
                        OperationTemplate.METADATA_AGGREGATION,
                        AggregationDSL.filtersAggregation(
                                Map.of(
                                    BatchOperationTemplate.FAILED_OPERATIONS_COUNT,
                                    failedOperationQuery,
                                    BatchOperationTemplate.COMPLETED_OPERATIONS_COUNT,
                                    completedOperationQuery))
                            ._toAggregation())));
    return searchRequestBuilder;
  }
}
