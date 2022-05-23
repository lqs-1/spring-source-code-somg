/*
 * Copyright 2002-2020 the original author or authors.
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
package org.springframework.security.oauth2.client.web;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.ClientAuthorizationException;
import org.springframework.security.oauth2.client.OAuth2AuthorizationContext;
import org.springframework.security.oauth2.client.OAuth2AuthorizationFailureHandler;
import org.springframework.security.oauth2.client.OAuth2AuthorizationSuccessHandler;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.RemoveAuthorizedClientOAuth2AuthorizationFailureHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.TestClientRegistrations;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.TestOAuth2AccessTokens;
import org.springframework.security.oauth2.core.TestOAuth2RefreshTokens;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.util.StringUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link DefaultOAuth2AuthorizedClientManager}.
 *
 * @author Joe Grandja
 */
public class DefaultOAuth2AuthorizedClientManagerTests {
	private ClientRegistrationRepository clientRegistrationRepository;
	private OAuth2AuthorizedClientRepository authorizedClientRepository;
	private OAuth2AuthorizedClientProvider authorizedClientProvider;
	private Function contextAttributesMapper;
	private OAuth2AuthorizationSuccessHandler authorizationSuccessHandler;
	private OAuth2AuthorizationFailureHandler authorizationFailureHandler;
	private DefaultOAuth2AuthorizedClientManager authorizedClientManager;
	private ClientRegistration clientRegistration;
	private Authentication principal;
	private OAuth2AuthorizedClient authorizedClient;
	private MockHttpServletRequest request;
	private MockHttpServletResponse response;
	private ArgumentCaptor<OAuth2AuthorizationContext> authorizationContextCaptor;

	@SuppressWarnings("unchecked")
	@Before
	public void setup() {
		this.clientRegistrationRepository = mock(ClientRegistrationRepository.class);
		this.authorizedClientRepository = mock(OAuth2AuthorizedClientRepository.class);
		this.authorizedClientProvider = mock(OAuth2AuthorizedClientProvider.class);
		this.contextAttributesMapper = mock(Function.class);
		this.authorizationSuccessHandler = spy(new OAuth2AuthorizationSuccessHandler() {
			@Override
			public void onAuthorizationSuccess(OAuth2AuthorizedClient authorizedClient, Authentication principal, Map<String, Object> attributes) {
				authorizedClientRepository.saveAuthorizedClient(authorizedClient, principal,
						(HttpServletRequest) attributes.get(HttpServletRequest.class.getName()),
						(HttpServletResponse) attributes.get(HttpServletResponse.class.getName()));
			}
		});
		this.authorizationFailureHandler = spy(new RemoveAuthorizedClientOAuth2AuthorizationFailureHandler(
				(clientRegistrationId, principal, attributes) ->
						authorizedClientRepository.removeAuthorizedClient(clientRegistrationId, principal,
								(HttpServletRequest) attributes.get(HttpServletRequest.class.getName()),
								(HttpServletResponse) attributes.get(HttpServletResponse.class.getName()))));
		this.authorizedClientManager = new DefaultOAuth2AuthorizedClientManager(
				this.clientRegistrationRepository, this.authorizedClientRepository);
		this.authorizedClientManager.setAuthorizedClientProvider(this.authorizedClientProvider);
		this.authorizedClientManager.setContextAttributesMapper(this.contextAttributesMapper);
		this.authorizedClientManager.setAuthorizationSuccessHandler(this.authorizationSuccessHandler);
		this.authorizedClientManager.setAuthorizationFailureHandler(this.authorizationFailureHandler);
		this.clientRegistration = TestClientRegistrations.clientRegistration().build();
		this.principal = new TestingAuthenticationToken("principal", "password");
		this.authorizedClient = new OAuth2AuthorizedClient(this.clientRegistration, this.principal.getName(),
				TestOAuth2AccessTokens.scopes("read", "write"), TestOAuth2RefreshTokens.refreshToken());
		this.request = new MockHttpServletRequest();
		this.response = new MockHttpServletResponse();
		this.authorizationContextCaptor = ArgumentCaptor.forClass(OAuth2AuthorizationContext.class);
	}

	@Test
	public void constructorWhenClientRegistrationRepositoryIsNullThenThrowIllegalArgumentException() {
		assertThatThrownBy(() -> new DefaultOAuth2AuthorizedClientManager(null, this.authorizedClientRepository))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("clientRegistrationRepository cannot be null");
	}

	@Test
	public void constructorWhenOAuth2AuthorizedClientRepositoryIsNullThenThrowIllegalArgumentException() {
		assertThatThrownBy(() -> new DefaultOAuth2AuthorizedClientManager(this.clientRegistrationRepository, null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("authorizedClientRepository cannot be null");
	}

	@Test
	public void setAuthorizedClientProviderWhenNullThenThrowIllegalArgumentException() {
		assertThatThrownBy(() -> this.authorizedClientManager.setAuthorizedClientProvider(null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("authorizedClientProvider cannot be null");
	}

	@Test
	public void setContextAttributesMapperWhenNullThenThrowIllegalArgumentException() {
		assertThatThrownBy(() -> this.authorizedClientManager.setContextAttributesMapper(null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("contextAttributesMapper cannot be null");
	}

	@Test
	public void setAuthorizationSuccessHandlerWhenNullThenThrowIllegalArgumentException() {
		assertThatThrownBy(() -> this.authorizedClientManager.setAuthorizationSuccessHandler(null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("authorizationSuccessHandler cannot be null");
	}

	@Test
	public void setAuthorizationFailureHandlerWhenNullThenThrowIllegalArgumentException() {
		assertThatThrownBy(() -> this.authorizedClientManager.setAuthorizationFailureHandler(null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("authorizationFailureHandler cannot be null");
	}

	@Test
	public void authorizeWhenRequestIsNullThenThrowIllegalArgumentException() {
		assertThatThrownBy(() -> this.authorizedClientManager.authorize(null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("authorizeRequest cannot be null");
	}

	@Test
	public void authorizeWhenHttpServletRequestIsNullThenThrowIllegalArgumentException() {
		OAuth2AuthorizeRequest authorizeRequest = OAuth2AuthorizeRequest.withClientRegistrationId(this.clientRegistration.getRegistrationId())
				.principal(this.principal)
				.build();
		assertThatThrownBy(() -> this.authorizedClientManager.authorize(authorizeRequest))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("servletRequest cannot be null");
	}

	@Test
	public void authorizeWhenHttpServletResponseIsNullThenThrowIllegalArgumentException() {
		OAuth2AuthorizeRequest authorizeRequest = OAuth2AuthorizeRequest.withClientRegistrationId(this.clientRegistration.getRegistrationId())
				.principal(this.principal)
				.attribute(HttpServletRequest.class.getName(), this.request)
				.build();
		assertThatThrownBy(() -> this.authorizedClientManager.authorize(authorizeRequest))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("servletResponse cannot be null");
	}

	@Test
	public void authorizeWhenClientRegistrationNotFoundThenThrowIllegalArgumentException() {
		OAuth2AuthorizeRequest authorizeRequest = OAuth2AuthorizeRequest.withClientRegistrationId("invalid-registration-id")
				.principal(this.principal)
				.attributes(attrs -> {
					attrs.put(HttpServletRequest.class.getName(), this.request);
					attrs.put(HttpServletResponse.class.getName(), this.response);
				})
				.build();
		assertThatThrownBy(() -> this.authorizedClientManager.authorize(authorizeRequest))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Could not find ClientRegistration with id 'invalid-registration-id'");
	}

	@SuppressWarnings("unchecked")
	@Test
	public void authorizeWhenNotAuthorizedAndUnsupportedProviderThenNotAuthorized() {
		when(this.clientRegistrationRepository.findByRegistrationId(
				eq(this.clientRegistration.getRegistrationId()))).thenReturn(this.clientRegistration);

		OAuth2AuthorizeRequest authorizeRequest = OAuth2AuthorizeRequest.withClientRegistrationId(this.clientRegistration.getRegistrationId())
				.principal(this.principal)
				.attributes(attrs -> {
					attrs.put(HttpServletRequest.class.getName(), this.request);
					attrs.put(HttpServletResponse.class.getName(), this.response);
				})
				.build();
		OAuth2AuthorizedClient authorizedClient = this.authorizedClientManager.authorize(authorizeRequest);

		verify(this.authorizedClientProvider).authorize(this.authorizationContextCaptor.capture());
		verify(this.contextAttributesMapper).apply(eq(authorizeRequest));

		OAuth2AuthorizationContext authorizationContext = this.authorizationContextCaptor.getValue();
		assertThat(authorizationContext.getClientRegistration()).isEqualTo(this.clientRegistration);
		assertThat(authorizationContext.getAuthorizedClient()).isNull();
		assertThat(authorizationContext.getPrincipal()).isEqualTo(this.principal);

		assertThat(authorizedClient).isNull();
		verifyNoInteractions(this.authorizationSuccessHandler);
		verify(this.authorizedClientRepository, never()).saveAuthorizedClient(any(), any(), any(), any());
	}

	@SuppressWarnings("unchecked")
	@Test
	public void authorizeWhenNotAuthorizedAndSupportedProviderThenAuthorized() {
		when(this.clientRegistrationRepository.findByRegistrationId(
				eq(this.clientRegistration.getRegistrationId()))).thenReturn(this.clientRegistration);

		when(this.authorizedClientProvider.authorize(any(OAuth2AuthorizationContext.class))).thenReturn(this.authorizedClient);

		OAuth2AuthorizeRequest authorizeRequest = OAuth2AuthorizeRequest.withClientRegistrationId(this.clientRegistration.getRegistrationId())
				.principal(this.principal)
				.attributes(attrs -> {
					attrs.put(HttpServletRequest.class.getName(), this.request);
					attrs.put(HttpServletResponse.class.getName(), this.response);
				})
				.build();
		OAuth2AuthorizedClient authorizedClient = this.authorizedClientManager.authorize(authorizeRequest);

		verify(this.authorizedClientProvider).authorize(this.authorizationContextCaptor.capture());
		verify(this.contextAttributesMapper).apply(eq(authorizeRequest));

		OAuth2AuthorizationContext authorizationContext = this.authorizationContextCaptor.getValue();
		assertThat(authorizationContext.getClientRegistration()).isEqualTo(this.clientRegistration);
		assertThat(authorizationContext.getAuthorizedClient()).isNull();
		assertThat(authorizationContext.getPrincipal()).isEqualTo(this.principal);

		assertThat(authorizedClient).isSameAs(this.authorizedClient);
		verify(this.authorizationSuccessHandler).onAuthorizationSuccess(
				eq(this.authorizedClient), eq(this.principal), any());
		verify(this.authorizedClientRepository).saveAuthorizedClient(
				eq(this.authorizedClient), eq(this.principal), eq(this.request), eq(this.response));
	}

	@SuppressWarnings("unchecked")
	@Test
	public void authorizeWhenAuthorizedAndSupportedProviderThenReauthorized() {
		when(this.clientRegistrationRepository.findByRegistrationId(
				eq(this.clientRegistration.getRegistrationId()))).thenReturn(this.clientRegistration);
		when(this.authorizedClientRepository.loadAuthorizedClient(
				eq(this.clientRegistration.getRegistrationId()), eq(this.principal), eq(this.request))).thenReturn(this.authorizedClient);

		OAuth2AuthorizedClient reauthorizedClient = new OAuth2AuthorizedClient(
				this.clientRegistration, this.principal.getName(),
				TestOAuth2AccessTokens.noScopes(), TestOAuth2RefreshTokens.refreshToken());

		when(this.authorizedClientProvider.authorize(any(OAuth2AuthorizationContext.class))).thenReturn(reauthorizedClient);

		OAuth2AuthorizeRequest authorizeRequest = OAuth2AuthorizeRequest.withClientRegistrationId(this.clientRegistration.getRegistrationId())
				.principal(this.principal)
				.attributes(attrs -> {
					attrs.put(HttpServletRequest.class.getName(), this.request);
					attrs.put(HttpServletResponse.class.getName(), this.response);
				})
				.build();
		OAuth2AuthorizedClient authorizedClient = this.authorizedClientManager.authorize(authorizeRequest);

		verify(this.authorizedClientProvider).authorize(this.authorizationContextCaptor.capture());
		verify(this.contextAttributesMapper).apply(any());

		OAuth2AuthorizationContext authorizationContext = this.authorizationContextCaptor.getValue();
		assertThat(authorizationContext.getClientRegistration()).isEqualTo(this.clientRegistration);
		assertThat(authorizationContext.getAuthorizedClient()).isSameAs(this.authorizedClient);
		assertThat(authorizationContext.getPrincipal()).isEqualTo(this.principal);

		assertThat(authorizedClient).isSameAs(reauthorizedClient);
		verify(this.authorizationSuccessHandler).onAuthorizationSuccess(
				eq(reauthorizedClient), eq(this.principal), any());
		verify(this.authorizedClientRepository).saveAuthorizedClient(
				eq(reauthorizedClient), eq(this.principal), eq(this.request), eq(this.response));
	}

	@Test
	public void authorizeWhenRequestParameterUsernamePasswordThenMappedToContext() {
		when(this.clientRegistrationRepository.findByRegistrationId(
				eq(this.clientRegistration.getRegistrationId()))).thenReturn(this.clientRegistration);

		when(this.authorizedClientProvider.authorize(any(OAuth2AuthorizationContext.class))).thenReturn(this.authorizedClient);

		// Set custom contextAttributesMapper
		this.authorizedClientManager.setContextAttributesMapper(authorizeRequest -> {
			Map<String, Object> contextAttributes = new HashMap<>();
			HttpServletRequest servletRequest = authorizeRequest.getAttribute(HttpServletRequest.class.getName());
			String username = servletRequest.getParameter(OAuth2ParameterNames.USERNAME);
			String password = servletRequest.getParameter(OAuth2ParameterNames.PASSWORD);
			if (StringUtils.hasText(username) && StringUtils.hasText(password)) {
				contextAttributes.put(OAuth2AuthorizationContext.USERNAME_ATTRIBUTE_NAME, username);
				contextAttributes.put(OAuth2AuthorizationContext.PASSWORD_ATTRIBUTE_NAME, password);
			}
			return contextAttributes;
		});

		this.request.addParameter(OAuth2ParameterNames.USERNAME, "username");
		this.request.addParameter(OAuth2ParameterNames.PASSWORD, "password");

		OAuth2AuthorizeRequest authorizeRequest = OAuth2AuthorizeRequest.withClientRegistrationId(this.clientRegistration.getRegistrationId())
				.principal(this.principal)
				.attributes(attrs -> {
					attrs.put(HttpServletRequest.class.getName(), this.request);
					attrs.put(HttpServletResponse.class.getName(), this.response);
				})
				.build();
		this.authorizedClientManager.authorize(authorizeRequest);

		verify(this.authorizedClientProvider).authorize(this.authorizationContextCaptor.capture());

		OAuth2AuthorizationContext authorizationContext = this.authorizationContextCaptor.getValue();
		String username = authorizationContext.getAttribute(OAuth2AuthorizationContext.USERNAME_ATTRIBUTE_NAME);
		assertThat(username).isEqualTo("username");
		String password = authorizationContext.getAttribute(OAuth2AuthorizationContext.PASSWORD_ATTRIBUTE_NAME);
		assertThat(password).isEqualTo("password");
	}

	@SuppressWarnings("unchecked")
	@Test
	public void reauthorizeWhenUnsupportedProviderThenNotReauthorized() {
		OAuth2AuthorizeRequest reauthorizeRequest = OAuth2AuthorizeRequest.withAuthorizedClient(this.authorizedClient)
				.principal(this.principal)
				.attributes(attrs -> {
					attrs.put(HttpServletRequest.class.getName(), this.request);
					attrs.put(HttpServletResponse.class.getName(), this.response);
				})
				.build();
		OAuth2AuthorizedClient authorizedClient = this.authorizedClientManager.authorize(reauthorizeRequest);

		verify(this.authorizedClientProvider).authorize(this.authorizationContextCaptor.capture());
		verify(this.contextAttributesMapper).apply(eq(reauthorizeRequest));

		OAuth2AuthorizationContext authorizationContext = this.authorizationContextCaptor.getValue();
		assertThat(authorizationContext.getClientRegistration()).isEqualTo(this.clientRegistration);
		assertThat(authorizationContext.getAuthorizedClient()).isSameAs(this.authorizedClient);
		assertThat(authorizationContext.getPrincipal()).isEqualTo(this.principal);

		assertThat(authorizedClient).isSameAs(this.authorizedClient);
		verifyNoInteractions(this.authorizationSuccessHandler);
		verify(this.authorizedClientRepository, never()).saveAuthorizedClient(
				any(OAuth2AuthorizedClient.class), eq(this.principal), eq(this.request), eq(this.response));
	}

	@SuppressWarnings("unchecked")
	@Test
	public void reauthorizeWhenSupportedProviderThenReauthorized() {
		OAuth2AuthorizedClient reauthorizedClient = new OAuth2AuthorizedClient(
				this.clientRegistration, this.principal.getName(),
				TestOAuth2AccessTokens.noScopes(), TestOAuth2RefreshTokens.refreshToken());

		when(this.authorizedClientProvider.authorize(any(OAuth2AuthorizationContext.class))).thenReturn(reauthorizedClient);

		OAuth2AuthorizeRequest reauthorizeRequest = OAuth2AuthorizeRequest.withAuthorizedClient(this.authorizedClient)
				.principal(this.principal)
				.attributes(attrs -> {
					attrs.put(HttpServletRequest.class.getName(), this.request);
					attrs.put(HttpServletResponse.class.getName(), this.response);
				})
				.build();
		OAuth2AuthorizedClient authorizedClient = this.authorizedClientManager.authorize(reauthorizeRequest);

		verify(this.authorizedClientProvider).authorize(this.authorizationContextCaptor.capture());
		verify(this.contextAttributesMapper).apply(eq(reauthorizeRequest));

		OAuth2AuthorizationContext authorizationContext = this.authorizationContextCaptor.getValue();
		assertThat(authorizationContext.getClientRegistration()).isEqualTo(this.clientRegistration);
		assertThat(authorizationContext.getAuthorizedClient()).isSameAs(this.authorizedClient);
		assertThat(authorizationContext.getPrincipal()).isEqualTo(this.principal);

		assertThat(authorizedClient).isSameAs(reauthorizedClient);
		verify(this.authorizationSuccessHandler).onAuthorizationSuccess(
				eq(reauthorizedClient), eq(this.principal), any());
		verify(this.authorizedClientRepository).saveAuthorizedClient(
				eq(reauthorizedClient), eq(this.principal), eq(this.request), eq(this.response));
	}

	@Test
	public void reauthorizeWhenRequestParameterScopeThenMappedToContext() {
		OAuth2AuthorizedClient reauthorizedClient = new OAuth2AuthorizedClient(
				this.clientRegistration, this.principal.getName(),
				TestOAuth2AccessTokens.noScopes(), TestOAuth2RefreshTokens.refreshToken());

		when(this.authorizedClientProvider.authorize(any(OAuth2AuthorizationContext.class))).thenReturn(reauthorizedClient);

		// Override the mock with the default
		this.authorizedClientManager.setContextAttributesMapper(
				new DefaultOAuth2AuthorizedClientManager.DefaultContextAttributesMapper());

		this.request.addParameter(OAuth2ParameterNames.SCOPE, "read write");

		OAuth2AuthorizeRequest reauthorizeRequest = OAuth2AuthorizeRequest.withAuthorizedClient(this.authorizedClient)
				.principal(this.principal)
				.attributes(attrs -> {
					attrs.put(HttpServletRequest.class.getName(), this.request);
					attrs.put(HttpServletResponse.class.getName(), this.response);
				})
				.build();
		this.authorizedClientManager.authorize(reauthorizeRequest);

		verify(this.authorizedClientProvider).authorize(this.authorizationContextCaptor.capture());

		OAuth2AuthorizationContext authorizationContext = this.authorizationContextCaptor.getValue();
		String[] requestScopeAttribute = authorizationContext.getAttribute(OAuth2AuthorizationContext.REQUEST_SCOPE_ATTRIBUTE_NAME);
		assertThat(requestScopeAttribute).contains("read", "write");
	}

	@Test
	public void reauthorizeWhenErrorCodeMatchThenRemoveAuthorizedClient() {
		ClientAuthorizationException authorizationException = new ClientAuthorizationException(
				new OAuth2Error(OAuth2ErrorCodes.INVALID_GRANT, null, null),
				this.clientRegistration.getRegistrationId());

		when(this.authorizedClientProvider.authorize(any(OAuth2AuthorizationContext.class)))
				.thenThrow(authorizationException);

		OAuth2AuthorizeRequest reauthorizeRequest = OAuth2AuthorizeRequest.withAuthorizedClient(this.authorizedClient)
				.principal(this.principal)
				.attributes(attrs -> {
					attrs.put(HttpServletRequest.class.getName(), this.request);
					attrs.put(HttpServletResponse.class.getName(), this.response);
				})
				.build();

		assertThatCode(() -> this.authorizedClientManager.authorize(reauthorizeRequest))
				.isEqualTo(authorizationException);

		verify(this.authorizationFailureHandler).onAuthorizationFailure(
				eq(authorizationException), eq(this.principal), any());
		verify(this.authorizedClientRepository).removeAuthorizedClient(
				eq(this.clientRegistration.getRegistrationId()), eq(this.principal), eq(this.request), eq(this.response));
	}

	@Test
	public void reauthorizeWhenErrorCodeDoesNotMatchThenDoNotRemoveAuthorizedClient() {
		ClientAuthorizationException authorizationException = new ClientAuthorizationException(
				new OAuth2Error("non-matching-error-code", null, null),
				this.clientRegistration.getRegistrationId());

		when(this.authorizedClientProvider.authorize(any(OAuth2AuthorizationContext.class)))
				.thenThrow(authorizationException);

		OAuth2AuthorizeRequest reauthorizeRequest = OAuth2AuthorizeRequest.withAuthorizedClient(this.authorizedClient)
				.principal(this.principal)
				.attributes(attrs -> {
					attrs.put(HttpServletRequest.class.getName(), this.request);
					attrs.put(HttpServletResponse.class.getName(), this.response);
				})
				.build();

		assertThatCode(() -> this.authorizedClientManager.authorize(reauthorizeRequest))
				.isEqualTo(authorizationException);

		verify(this.authorizationFailureHandler).onAuthorizationFailure(
				eq(authorizationException), eq(this.principal), any());
		verifyNoInteractions(this.authorizedClientRepository);
	}
}
