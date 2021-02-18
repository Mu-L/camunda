/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.events;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.camunda.optimize.dto.optimize.query.event.DeletableEventDto;
import org.camunda.optimize.dto.optimize.query.event.EventSearchRequestDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventDto;
import org.camunda.optimize.dto.optimize.rest.Page;
import org.camunda.optimize.service.es.reader.ExternalEventReader;
import org.camunda.optimize.service.es.writer.EventProcessInstanceWriter;
import org.camunda.optimize.service.es.writer.EventProcessInstanceWriterFactory;
import org.camunda.optimize.service.es.writer.ExternalEventWriter;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Component
@Slf4j
public class ExternalEventService implements EventFetcherService<EventDto> {

  private final ExternalEventReader externalEventReader;
  private final ExternalEventWriter externalEventWriter;
  private final EventProcessInstanceWriter eventInstanceWriter;

  public ExternalEventService(final ExternalEventReader externalEventReader,
                              final ExternalEventWriter externalEventWriter,
                              final EventProcessInstanceWriterFactory eventProcessInstanceWriterFactory) {
    this.externalEventReader = externalEventReader;
    this.externalEventWriter = externalEventWriter;
    this.eventInstanceWriter = eventProcessInstanceWriterFactory.createAllEventProcessInstanceWriter();
  }

  public Page<DeletableEventDto> getEventsForRequest(final EventSearchRequestDto eventSearchRequestDto) {
    return externalEventReader.getEventsForRequest(eventSearchRequestDto);
  }

  public void saveEventBatch(final List<EventDto> eventDtos) {
    externalEventWriter.upsertEvents(eventDtos);
  }

  @Override
  public List<EventDto> getEventsIngestedAfter(final Long ingestTimestamp, final int limit) {
    return externalEventReader.getEventsIngestedAfter(ingestTimestamp, limit);
  }

  @Override
  public List<EventDto> getEventsIngestedAt(final Long ingestTimestamp) {
    return externalEventReader.getEventsIngestedAt(ingestTimestamp);
  }

  public Pair<Optional<OffsetDateTime>, Optional<OffsetDateTime>> getMinAndMaxIngestedTimestampsForAllEvents() {
    return externalEventReader.getMinAndMaxIngestedTimestamps();
  }

  public Pair<Optional<OffsetDateTime>, Optional<OffsetDateTime>> getMinAndMaxIngestedTimestampsForGroups(
    final List<String> eventGroups) {
    return externalEventReader.getMinAndMaxIngestedTimestampsForGroups(eventGroups);
  }

  public void deleteEvents(final List<String> eventIdsToDelete) {
    eventInstanceWriter.deleteEventsWithIdsInFromAllInstances(eventIdsToDelete);
    externalEventWriter.deleteEventsWithIdsIn(eventIdsToDelete);
  }

}
