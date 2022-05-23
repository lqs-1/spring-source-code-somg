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
package org.springframework.security.config.http;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.config.test.SpringTestRule;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.test.context.annotation.SecurityTestExecutionListeners;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.oauth2.core.endpoint.TestOAuth2AccessTokenResponses.accessTokenResponse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests for {@link OAuth2ClientBeanDefinitionParser}.
 *
 * @author Joe Grandja
 */
@RunWith(SpringJUnit4ClassRunner.class)
@SecurityTestExecutionListeners
public class OAuth2ClientBeanDefinitionParserTests {
	private static final String CONFIG_LOCATION_PREFIX = "classpath:org/springframework/security/config/http/OAuth2ClientBeanDefinitionParserTests";

	@Rule
	public final SpringTestRule spring = new SpringTestRule();

	@Autowired
	private ClientRegistrationRepository clientRegistrationRepository;

	@Autowired(required = false)
	private OAuth2AuthorizedClientRepository authorizedClientRepository;

	@Autowired(required = false)
	private OAuth2AuthorizedClientService authorizedClientService;

	@Autowired(required = false)
	private AuthorizationRequestRepository<OAuth2AuthorizationRequest> authorizationRequestRepository;

	@Autowired(required = false)
	private OAuth2AuthorizationRequestResolver authorizationRequestResolver;

	@Autowired(required = false)
	private OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> accessTokenResponseClient;

	@Autowired
	private MockMvc mvc;

	@Test
	public void requestWhenAuthorizeThenRedirect() throws Exception {
		this.spring.configLocations(xml("Minimal")).autowire();

		MvcResult result = this.mvc.perform(get("/oauth2/authorization/google"))
				.andExpect(status().is3xxRedirection())
				.andReturn();
		assertThat(result.getResponse().getRedirectedUrl()).matches(
				"https://accounts.google.com/o/oauth2/v2/auth\\?" +
						"response_type=code&client_id=google-client-id&" +
						"scope=scope1%20scope2&state=.{15,}&redirect_uri=http://localhost/callback/google");
	}

	@Test
	public void requestWhenCustomClientRegistrationRepositoryThenCalled() throws Exception {
		this.spring.configLocations(xml("CustomClientRegistrationRepository")).autowire();

		ClientRegistration clientRegistration = CommonOAuth2Provider.GOOGLE.getBuilder("google")
				.clientId("google-client-id")
				.clientSecret("google-client-secret")
				.redirectUriTemplate("http://localhost/callback/google")
				.scope("scope1", "scope2")
				.build();
		when(this.clientRegistrationRepository.findByRegistrationId(any())).thenReturn(clientRegistration);

		MvcResult result = this.mvc.perform(get("/oauth2/authorization/google"))
				.andExpect(status().is3xxRedirection())
				.andReturn();
		assertThat(result.getResponse().getRedirectedUrl()).matches(
				"https://accounts.google.com/o/oauth2/v2/auth\\?" +
						"response_type=code&client_id=google-client-id&" +
						"scope=scope1%20scope2&state=.{15,}&redirect_uri=http://localhost/callback/google");

		verify(this.clientRegistrationRepository).findByRegistrationId(any());
	}

	@Test
	public void requestWhenCustomAuthorizationRequestResolverThenCalled() throws Exception {
		this.spring.configLocations(xml("CustomConfiguration")).autowire();

		ClientRegistration clientRegistration = this.clientRegistrationRepository.findByRegistrationId("google");

		OAuth2AuthorizationRequest authorizationRequest = createAuthorizationRequest(clientRegistration);
		when(this.authorizationRequestResolver.resolve(any())).thenReturn(authorizationRequest);

		this.mvc.perform(get("/oauth2/authorization/google"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl(
						"https://accounts.google.com/o/oauth2/v2/auth?" +
								"response_type=code&client_id=google-client-id&" +
								"scope=scope1%20scope2&state=state&redirect_uri=http://localhost/callback/google"));

		verify(this.authorizationRequestResolver).resolve(any());
	}

	@Test
	public void requestWhenAuthorizationResponseMatchThenProcess() throws Exception {
		this.spring.configLocations(xml("CustomConfiguration")).autowire();

		ClientRegistration clientRegistration = this.clientRegistrationRepository.findByRegistrationId("google");

		OAuth2AuthorizationRequest authorizationRequest = createAuthorizationRequest(clientRegistration);
		when(this.authorizationRequestRepository.loadAuthorizationRequest(any()))
				.thenReturn(authorizationRequest);
		when(this.authorizationRequestRepository.removeAuthorizationRequest(any(), any()))
				.thenReturn(authorizationRequest);

		OAuth2AccessTokenResponse accessTokenResponse = accessTokenResponse().build();
		when(this.accessTokenResponseClient.getTokenResponse(any())).thenReturn(accessTokenResponse);

		MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
		params.add("code", "code123");
		params.add("state", authorizationRequest.getState());
		this.mvc.perform(get(authorizationRequest.getRedirectUri()).params(params))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl(authorizationRequest.getRedirectUri()));

		ArgumentCaptor<OAuth2AuthorizedClient> authorizedClientCaptor =
				ArgumentCaptor.forClass(OAuth2AuthorizedClient.class);
		verify(this.authorizedClientRepository).saveAuthorizedClient(
				authorizedClientCaptor.capture(), any(), any(), any());
		OAuth2AuthorizedClient authorizedClient = authorizedClientCaptor.getValue();
		assertThat(authorizedClient.getClientRegistration()).isEqualTo(clientRegistration);
		assertThat(authorizedClient.getAccessToken()).isEqualTo(accessTokenResponse.getAccessToken());
	}

	@WithMockUser
	@Test
	public void requestWhenCustomAuthorizedClientServiceThenCalled() throws Exception {
		this.spring.configLocations(xml("CustomAuthorizedClientService")).autowire();

		ClientRegistration clientRegistration = this.clientRegistrationRepository.findByRegistrationId("google");

		OAuth2AuthorizationRequest authorizationRequest = createAuthorizationRequest(clientRegistration);
		when(this.authorizationRequestRepository.loadAuthorizationRequest(any()))
				.thenReturn(authorizationRequest);
		when(this.authorizationRequestRepository.removeAuthorizationRequest(any(), any()))
				.thenReturn(authorizationRequest);

		OAuth2AccessTokenResponse accessTokenResponse = accessTokenResponse().build();
		when(this.accessTokenResponseClient.getTokenResponse(any())).thenReturn(accessTokenResponse);

		MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
		params.add("code", "code123");
		params.add("state", authorizationRequest.getState());
		this.mvc.perform(get(authorizationRequest.getRedirectUri()).params(params))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl(authorizationRequest.getRedirectUri()));

		verify(this.authorizedClientService).saveAuthorizedClient(any(), any());
	}

	private static OAuth2AuthorizationRequest createAuthorizationRequest(ClientRegistration clientRegistration) {
		Map<String, Object> attributes = new HashMap<>();
		attributes.put(OAuth2ParameterNames.REGISTRATION_ID, clientRegistration.getRegistrationId());
		return OAuth2AuthorizationRequest.authorizationCode()
				.authorizationUri(clientRegistration.getProviderDetails().getAuthorizationUri())
				.clientId(clientRegistration.getClientId())
				.redirectUri(clientRegistration.getRedirectUriTemplate())
				.scopes(clientRegistration.getScopes())
				.state("state")
				.attributes(attributes)
				.build();
	}

	private static String xml(String configName) {
		return CONFIG_LOCATION_PREFIX + "-" + configName + ".xml";
	}
}
