/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest;

import static io.camunda.zeebe.auth.api.JwtAuthorizationBuilder.EXTERNAL_TOKEN_CLAIM_PREFIX;
import static org.junit.jupiter.api.Assertions.*;

import io.camunda.authentication.entity.CamundaUser.CamundaUserBuilder;
import io.camunda.zeebe.auth.impl.Authorization;
import io.camunda.zeebe.auth.impl.JwtAuthorizationDecoder;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

class RequestMapperTest {

  @Mock private RequestAttributes requestAttributes;

  @BeforeEach
  void setup() throws Exception {
    MockitoAnnotations.openMocks(this).close();
    RequestContextHolder.setRequestAttributes(requestAttributes);
  }

  @Test
  void tokenContainsUserKeyWithBasicAuth() {

    // given
    final long userKey = 1L;
    setUsernamePasswordAuthenticationInContext(userKey);

    // when
    final String token = RequestMapper.getAuthentication().token();

    // then
    final JwtAuthorizationDecoder decoder = new JwtAuthorizationDecoder(token);
    final var decodedToken = decoder.decode();
    assertNotNull(decodedToken);
    assertTrue(decodedToken.containsKey(Authorization.AUTHORIZED_USER_KEY));
    assertEquals(decodedToken.get(Authorization.AUTHORIZED_USER_KEY), userKey);
  }

  @Test
  void tokenContainsExtraClaimsWithOidcAuth() {

    // given
    final String sub1 = "sub1";
    final String aud1 = "aud1";
    setJwtAuthenticationInContext(sub1, aud1);

    // when
    final String token = RequestMapper.getAuthentication().token();

    // then
    final JwtAuthorizationDecoder decoder = new JwtAuthorizationDecoder(token);
    final var decodedToken = decoder.decode();
    assertNotNull(decodedToken);
    assertTrue(decodedToken.containsKey(EXTERNAL_TOKEN_CLAIM_PREFIX + "sub"));
    assertTrue(decodedToken.containsKey(EXTERNAL_TOKEN_CLAIM_PREFIX + "aud"));
    assertEquals(decodedToken.get(EXTERNAL_TOKEN_CLAIM_PREFIX + "sub"), sub1);
    assertEquals(decodedToken.get(EXTERNAL_TOKEN_CLAIM_PREFIX + "aud"), aud1);
  }

  private void setJwtAuthenticationInContext(final String sub, final String aud) {
    final Jwt jwt =
        new Jwt(
            Authorization.jwtEncoder()
                .withIssuer("issuer1")
                .withAudience(aud)
                .withSubject(sub)
                .build(),
            Instant.ofEpochSecond(10),
            Instant.ofEpochSecond(100),
            Map.of("alg", "RSA256"),
            Map.of("sub", sub, "aud", aud));
    final JwtAuthenticationToken jwtAuthenticationToken = new JwtAuthenticationToken(jwt);
    SecurityContextHolder.getContext().setAuthentication(jwtAuthenticationToken);
  }

  private void setUsernamePasswordAuthenticationInContext(final long userKey) {
    final UsernamePasswordAuthenticationToken authenticationToken =
        new UsernamePasswordAuthenticationToken(
            CamundaUserBuilder.aCamundaUser()
                .withUsername("admin")
                .withPassword("admin")
                .withUserKey(userKey)
                .build(),
            null);
    SecurityContextHolder.getContext().setAuthentication(authenticationToken);
  }
}
