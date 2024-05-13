/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useQuery} from '@tanstack/react-query';
import {api} from 'modules/api';
import {RequestError, request} from 'modules/request';
import {Process} from 'modules/types';

const HTTP_STATUS_FORBIDDEN = 403;
const HTTP_STATUS_NOT_FOUND = 404;

type Data = Process;

function useProcessDefinition(
  processDefinitionId: string | undefined | null,
  options?: {enabled?: boolean},
) {
  return useQuery<Data, RequestError>({
    queryKey: ['processDefinition', processDefinitionId],
    queryFn: async () => {
      const {response, error} = await request(
        api.getProcess({processDefinitionId: processDefinitionId!}),
      );

      if (error !== null && error.variant === 'failed-response') {
        const {status} = error.response;
        if (
          status === HTTP_STATUS_FORBIDDEN ||
          status === HTTP_STATUS_NOT_FOUND
        ) {
          return null;
        }
      }

      if (response !== null) {
        return response.json();
      }

      throw error ?? new Error('Failed to fetch process instances');
    },
    enabled:
      (options?.enabled ?? true) &&
      processDefinitionId !== undefined &&
      processDefinitionId !== null,
    refetchOnReconnect: false,
    refetchOnWindowFocus: false,
  });
}

export {useProcessDefinition};
