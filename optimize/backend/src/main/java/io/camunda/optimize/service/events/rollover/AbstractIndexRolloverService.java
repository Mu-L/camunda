/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.events.rollover;

import io.camunda.optimize.service.AbstractScheduledService;
import io.camunda.optimize.service.db.DatabaseClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.support.PeriodicTrigger;

public abstract class AbstractIndexRolloverService extends AbstractScheduledService {

  private static final Logger log =
      org.slf4j.LoggerFactory.getLogger(AbstractIndexRolloverService.class);
  protected final DatabaseClient databaseClient;

  protected AbstractIndexRolloverService(final DatabaseClient databaseClient) {
    this.databaseClient = databaseClient;
  }

  public List<String> triggerRollover() {
    final List<String> rolledOverIndexAliases = new ArrayList<>();
    getAliasesToConsiderRolling()
        .forEach(
            indexAlias -> {
              try {
                final boolean isRolledOver =
                    databaseClient.triggerRollover(indexAlias, getMaxIndexSizeGB());
                if (isRolledOver) {
                  rolledOverIndexAliases.add(indexAlias);
                }
              } catch (final Exception e) {
                log.warn("Failed rolling over index {}, will try again next time.", indexAlias, e);
              }
            });
    return rolledOverIndexAliases;
  }

  @Override
  protected void run() {
    triggerRollover();
  }

  @Override
  protected Trigger createScheduleTrigger() {
    return new PeriodicTrigger(Duration.ofMinutes(getScheduleIntervalInMinutes()));
  }

  protected abstract Set<String> getAliasesToConsiderRolling();

  protected abstract int getMaxIndexSizeGB();

  protected abstract int getScheduleIntervalInMinutes();
}
