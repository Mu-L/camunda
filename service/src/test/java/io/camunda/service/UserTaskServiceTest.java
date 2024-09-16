/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.search.clients.UserTaskSearchClient;
import io.camunda.service.entities.UserTaskEntity;
import io.camunda.service.search.filter.UserTaskFilter;
import io.camunda.service.search.filter.UserTaskFilter.Builder;
import io.camunda.service.search.query.SearchQueryBuilders;
import io.camunda.service.search.query.SearchQueryResult;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.util.Either;
import java.util.List;
import org.assertj.core.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class UserTaskServiceTest {

  private UserTaskServices services;
  private UserTaskSearchClient client;

  @BeforeEach
  public void before() {
    client = mock(UserTaskSearchClient.class);
    services = new UserTaskServices(mock(BrokerClient.class), client, null);
  }

  @Test
  public void shouldReturnUserTasks() {
    // given
    final var result = mock(SearchQueryResult.class);
    when(client.searchUserTasks(any(), any())).thenReturn(Either.right(result));

    final UserTaskFilter filter = new Builder().build();
    final var searchQuery = SearchQueryBuilders.userTaskSearchQuery((b) -> b.filter(filter));

    // when
    final SearchQueryResult<UserTaskEntity> searchQueryResult = services.search(searchQuery);

    // then
    assertThat(searchQueryResult).isEqualTo(result);
  }

  @Test
  public void shouldReturnSingleUserTask() {
    // given
    final var entity = mock(UserTaskEntity.class);
    final var result = new SearchQueryResult<>(1, List.of(entity), Arrays.array());
    when(client.searchUserTasks(any(), any())).thenReturn(Either.right(result));

    // when
    final var searchQueryResult = services.getByKey(1L);

    // then
    assertThat(searchQueryResult).isEqualTo(entity);
  }

}
