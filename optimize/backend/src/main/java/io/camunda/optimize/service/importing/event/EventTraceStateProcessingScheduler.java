/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.event;

import io.camunda.optimize.service.AbstractScheduledService;
import io.camunda.optimize.service.importing.ImportMediator;
import io.camunda.optimize.service.importing.event.mediator.PersistEventIndexHandlerStateMediator;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.EventBasedProcessConfiguration;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.support.PeriodicTrigger;
import org.springframework.stereotype.Component;

@Component
public class EventTraceStateProcessingScheduler extends AbstractScheduledService {

  private static final Logger log =
      org.slf4j.LoggerFactory.getLogger(EventTraceStateProcessingScheduler.class);
  private final ConfigurationService configurationService;

  private final EventTraceImportMediatorManager eventTraceImportMediatorManager;
  private final PersistEventIndexHandlerStateMediator eventProcessingProgressMediator;

  public EventTraceStateProcessingScheduler(
      final ConfigurationService configurationService,
      final EventTraceImportMediatorManager eventTraceImportMediatorManager,
      final PersistEventIndexHandlerStateMediator eventProcessingProgressMediator) {
    this.configurationService = configurationService;
    this.eventTraceImportMediatorManager = eventTraceImportMediatorManager;
    this.eventProcessingProgressMediator = eventProcessingProgressMediator;
  }

  @PostConstruct
  public void init() {
    if (getEventBasedProcessConfiguration().getEventImport().isEnabled()) {
      startScheduling();
    }
  }

  public Future<Void> runImportRound() {
    return runImportRound(false);
  }

  public Future<Void> runImportRound(final boolean forceImport) {
    final List<ImportMediator> allImportMediators = getImportMediators();
    final List<ImportMediator> currentImportRound =
        allImportMediators.stream()
            .filter(mediator -> forceImport || mediator.canImport())
            .collect(Collectors.toList());

    if (log.isDebugEnabled()) {
      log.debug(
          "Scheduling import round for {}",
          currentImportRound.stream()
              .map(mediator1 -> mediator1.getClass().getSimpleName())
              .collect(Collectors.joining(",")));
    }

    final CompletableFuture<?>[] importTaskFutures =
        currentImportRound.stream()
            .map(ImportMediator::runImport)
            .toArray(CompletableFuture[]::new);

    if (importTaskFutures.length == 0 && !forceImport) {
      doBackoff(allImportMediators);
    }

    return CompletableFuture.allOf(importTaskFutures);
  }

  public List<ImportMediator> getImportMediators() {
    return Stream.concat(
            Stream.of(eventProcessingProgressMediator),
            eventTraceImportMediatorManager.getEventTraceImportMediators().stream())
        .collect(Collectors.toList());
  }

  @Override
  protected void run() {
    if (isScheduledToRun()) {
      runImportRound();
    }
  }

  @Override
  protected Trigger createScheduleTrigger() {
    return new PeriodicTrigger(Duration.ZERO);
  }

  @Override
  public synchronized boolean startScheduling() {
    log.info("Scheduling event pre-aggregation.");
    return super.startScheduling();
  }

  @PreDestroy
  @Override
  public synchronized void stopScheduling() {
    log.info("Stop scheduling event pre-aggregation.");
    super.stopScheduling();
  }

  private void doBackoff(final List<ImportMediator> mediators) {
    final long timeToSleep =
        mediators.stream().map(ImportMediator::getBackoffTimeInMs).min(Long::compare).orElse(5000L);
    try {
      log.debug("No imports to schedule. Scheduler is sleeping for [{}] ms.", timeToSleep);
      Thread.sleep(timeToSleep);
    } catch (final InterruptedException e) {
      log.warn("Scheduler was interrupted while sleeping.", e);
      Thread.currentThread().interrupt();
    }
  }

  private EventBasedProcessConfiguration getEventBasedProcessConfiguration() {
    return configurationService.getEventBasedProcessConfiguration();
  }

  public PersistEventIndexHandlerStateMediator getEventProcessingProgressMediator() {
    return eventProcessingProgressMediator;
  }
}
