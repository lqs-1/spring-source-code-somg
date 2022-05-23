/*
 * Copyright 2002-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.security.oauth2.client;

import org.junit.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.TestClientRegistrations;
import org.springframework.security.oauth2.core.TestOAuth2AccessTokens;
import reactor.core.publisher.Mono;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link DelegatingReactiveOAuth2AuthorizedClientProvider}.
 *
 * @author Joe Grandja
 */
public class DelegatingReactiveOAuth2AuthorizedClientProviderTests {

	@Test
	public void constructorWhenProvidersIsEmptyThenThrowIllegalArgumentException() {
		assertThatThrownBy(() -> new DelegatingReactiveOAuth2AuthorizedClientProvider(new ReactiveOAuth2AuthorizedClientProvider[0]))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new DelegatingReactiveOAuth2AuthorizedClientProvider(Collections.emptyList()))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	public void authorizeWhenContextIsNullThenThrowIllegalArgumentException() {
		DelegatingReactiveOAuth2AuthorizedClientProvider delegate = new DelegatingReactiveOAuth2AuthorizedClientProvider(
				mock(ReactiveOAuth2AuthorizedClientProvider.class));
		assertThatThrownBy(() -> delegate.authorize(null).block())
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("context cannot be null");
	}

	@Test
	public void authorizeWhenProviderCanAuthorizeThenReturnAuthorizedClient() {
		Authentication principal = new TestingAuthenticationToken("principal", "password");
		ClientRegistration clientRegistration = TestClientRegistrations.clientRegistration().build();
		OAuth2AuthorizedClient authorizedClient = new OAuth2AuthorizedClient(
				clientRegistration, principal.getName(), TestOAuth2AccessTokens.noScopes());

		ReactiveOAuth2AuthorizedClientProvider authorizedClientProvider1 = mock(ReactiveOAuth2AuthorizedClientProvider.class);
		when(authorizedClientProvider1.authorize(any())).thenReturn(Mono.empty());
		ReactiveOAuth2AuthorizedClientProvider authorizedClientProvider2 = mock(ReactiveOAuth2AuthorizedClientProvider.class);
		when(authorizedClientProvider2.authorize(any())).thenReturn(Mono.empty());
		ReactiveOAuth2AuthorizedClientProvider authorizedClientProvider3 = mock(ReactiveOAuth2AuthorizedClientProvider.class);
		when(authorizedClientProvider3.authorize(any())).thenReturn(Mono.just(authorizedClient));

		DelegatingReactiveOAuth2AuthorizedClientProvider delegate = new DelegatingReactiveOAuth2AuthorizedClientProvider(
				authorizedClientProvider1, authorizedClientProvider2, authorizedClientProvider3);
		OAuth2AuthorizationContext context = OAuth2AuthorizationContext.withClientRegistration(clientRegistration)
				.principal(principal)
				.build();
		OAuth2AuthorizedClient reauthorizedClient = delegate.authorize(context).block();
		assertThat(reauthorizedClient).isSameAs(authorizedClient);
	}

	@Test
	public void authorizeWhenProviderCantAuthorizeThenReturnNull() {
		ClientRegistration clientRegistration = TestClientRegistrations.clientRegistration().build();
		OAuth2AuthorizationContext context = OAuth2AuthorizationContext.withClientRegistration(clientRegistration)
				.principal(new TestingAuthenticationToken("principal", "password"))
				.build();

		ReactiveOAuth2AuthorizedClientProvider authorizedClientProvider1 = mock(ReactiveOAuth2AuthorizedClientProvider.class);
		when(authorizedClientProvider1.authorize(any())).thenReturn(Mono.empty());
		ReactiveOAuth2AuthorizedClientProvider authorizedClientProvider2 = mock(ReactiveOAuth2AuthorizedClientProvider.class);
		when(authorizedClientProvider2.authorize(any())).thenReturn(Mono.empty());

		DelegatingReactiveOAuth2AuthorizedClientProvider delegate = new DelegatingReactiveOAuth2AuthorizedClientProvider(
				authorizedClientProvider1, authorizedClientProvider2);
		assertThat(delegate.authorize(context).block()).isNull();
	}
}
