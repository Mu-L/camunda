/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.db.ZeebeDbInconsistentException;
import io.camunda.zeebe.engine.state.mutable.MutableAuthorizationState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
public class AuthorizationStateTest {
  private MutableProcessingState processingState;
  private MutableAuthorizationState authorizationState;

  @BeforeEach
  public void setup() {
    authorizationState = processingState.getAuthorizationState();
  }

  @DisplayName("should return null if no authorization for owner and resource is not exist")
  @Test
  void shouldReturnNullIfNoAuthorizationForOwnerAndResourceExists() {
    // when
    final var persistedAuth =
        authorizationState.getResourceIdentifiers(1L, AuthorizationOwnerType.USER, "resource");
    // then
    assertThat(persistedAuth).isNull();
  }

  @DisplayName(
      "should create authorization if an authorization for the owner and resource pair does not exist")
  @Test
  void shouldCreateIfUsernameDoesNotExist() {
    // when
    final AuthorizationRecord authorizationRecord =
        new AuthorizationRecord()
            .setOwnerKey(1L)
            .setOwnerType(AuthorizationOwnerType.GROUP)
            .setResourceKey("resource")
            .setResourceType("resourceType")
            .setPermissions(List.of("write:*"));
    authorizationState.createAuthorization(authorizationRecord);

    // then
    final var persistedAuthorization =
        authorizationState.getResourceIdentifiers(
            authorizationRecord.getOwnerKey(),
            authorizationRecord.getOwnerType(),
            authorizationRecord.getResourceType());
    assertThat(persistedAuthorization.getResourceIdentifiers())
        .isEqualTo(authorizationRecord.getPermissions());
  }

  @DisplayName(
      "should throw an exception when an authorization for owner and resource pair already exist")
  @Test
  void shouldThrowExceptionInCreateIfUsernameDoesNotExist() {
    final var ownerKey = 1L;
    // given
    final AuthorizationRecord authorizationRecord =
        new AuthorizationRecord()
            .setAuthorizationKey(1L)
            .setOwnerKey(ownerKey)
            .setOwnerType(AuthorizationOwnerType.GROUP)
            .setResourceKey("my-resource-key")
            .setResourceType("process-definition")
            .setPermissions(List.of("write:*"));
    authorizationState.createAuthorization(authorizationRecord);

    // when/then
    assertThatThrownBy(() -> authorizationState.createAuthorization(authorizationRecord))
        .isInstanceOf(ZeebeDbInconsistentException.class)
        .hasMessageContaining(
            "Key DbCompositeKey{first=DbLong{"
                + ownerKey
                + "}, second=DbCompositeKey{first=process-definition, second=}} in ColumnFamily RESOURCE_IDS_BY_OWNER_KEY_RESOURCE_TYPE_AND_PERMISSION already exists");
  }

  @DisplayName("should return the correct authorization")
  @Test
  void shouldReturnCorrectAuthorization() {
    // given
    final AuthorizationRecord authorizationRecordOne =
        new AuthorizationRecord()
            .setAuthorizationKey(1L)
            .setOwnerKey(1L)
            .setOwnerType(AuthorizationOwnerType.GROUP)
            .setResourceKey("resource")
            .setResourceType("resourceType")
            .setPermissions(List.of("read:*"));
    authorizationState.createAuthorization(authorizationRecordOne);

    final AuthorizationRecord authorizationRecordTwo =
        new AuthorizationRecord()
            .setAuthorizationKey(2L)
            .setOwnerKey(2L)
            .setOwnerType(AuthorizationOwnerType.GROUP)
            .setResourceKey("resource")
            .setResourceType("resourceType")
            .setPermissions(List.of("write:*"));
    authorizationState.createAuthorization(authorizationRecordTwo);

    final var authorizationOne =
        authorizationState.getResourceIdentifiers(
            authorizationRecordOne.getOwnerKey(),
            authorizationRecordOne.getOwnerType(),
            authorizationRecordOne.getResourceType());

    final var authorizationTwo =
        authorizationState.getResourceIdentifiers(
            authorizationRecordTwo.getOwnerKey(),
            authorizationRecordTwo.getOwnerType(),
            authorizationRecordTwo.getResourceType());

    assertThat(authorizationOne).isNotEqualTo(authorizationTwo);
    assertThat(authorizationOne.getResourceIdentifiers())
        .isEqualTo(authorizationRecordOne.getPermissions());
    assertThat(authorizationTwo.getResourceIdentifiers())
        .isEqualTo(authorizationRecordTwo.getPermissions());
  }
}
