/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.search.query;

import io.camunda.service.search.filter.DecisionInstanceFilter;
import io.camunda.service.search.filter.DecisionInstanceFilter.Builder;
import io.camunda.service.search.filter.FilterBuilders;
import io.camunda.service.search.page.SearchQueryPage;
import io.camunda.service.search.result.DecisionInstanceQueryResultConfig;
import io.camunda.service.search.result.QueryResultConfigBuilders;
import io.camunda.service.search.sort.DecisionInstanceSort;
import io.camunda.service.search.sort.SortOptionBuilders;
import io.camunda.util.ObjectBuilder;
import java.util.Objects;
import java.util.function.Function;

public record DecisionInstanceQuery(
    DecisionInstanceFilter filter,
    DecisionInstanceSort sort,
    SearchQueryPage page,
    DecisionInstanceQueryResultConfig resultConfig)
    implements TypedSearchQuery<DecisionInstanceFilter, DecisionInstanceSort> {

  public static DecisionInstanceQuery of(
      final Function<Builder, ObjectBuilder<DecisionInstanceQuery>> fn) {
    return fn.apply(new Builder()).build();
  }

  public static final class Builder extends SearchQueryBase.AbstractQueryBuilder<Builder>
      implements TypedSearchQueryBuilder<
          DecisionInstanceQuery, Builder, DecisionInstanceFilter, DecisionInstanceSort> {

    private static final DecisionInstanceFilter EMPTY_FILTER =
        FilterBuilders.decisionInstance().build();
    private static final DecisionInstanceSort EMPTY_SORT =
        SortOptionBuilders.decisionInstance().build();
    private static final DecisionInstanceQueryResultConfig DEFAULT_RESULT_CONFIG =
        QueryResultConfigBuilders.decisionInstance(
            r -> r.evaluatedInputs().exclude().evaluatedOutputs().exclude());

    private DecisionInstanceFilter filter;
    private DecisionInstanceSort sort;
    private DecisionInstanceQueryResultConfig resultConfig;

    @Override
    public Builder filter(final DecisionInstanceFilter value) {
      filter = value;
      return this;
    }

    @Override
    public Builder sort(final DecisionInstanceSort value) {
      sort = value;
      return this;
    }

    public Builder resultConfig(final DecisionInstanceQueryResultConfig value) {
      resultConfig = value;
      return this;
    }

    public Builder filter(
        final Function<DecisionInstanceFilter.Builder, ObjectBuilder<DecisionInstanceFilter>> fn) {
      return filter(FilterBuilders.decisionInstance(fn));
    }

    public Builder sort(
        final Function<DecisionInstanceSort.Builder, ObjectBuilder<DecisionInstanceSort>> fn) {
      return sort(SortOptionBuilders.decisionInstance(fn));
    }

    public Builder resultConfig(
        final Function<
                DecisionInstanceQueryResultConfig.Builder,
                ObjectBuilder<DecisionInstanceQueryResultConfig>>
            fn) {
      return resultConfig(QueryResultConfigBuilders.decisionInstance(fn));
    }

    @Override
    protected Builder self() {
      return this;
    }

    @Override
    public DecisionInstanceQuery build() {
      filter = Objects.requireNonNullElse(filter, EMPTY_FILTER);
      sort = Objects.requireNonNullElse(sort, EMPTY_SORT);
      resultConfig = Objects.requireNonNullElse(resultConfig, DEFAULT_RESULT_CONFIG);
      return new DecisionInstanceQuery(filter, sort, page(), resultConfig);
    }
  }
}
