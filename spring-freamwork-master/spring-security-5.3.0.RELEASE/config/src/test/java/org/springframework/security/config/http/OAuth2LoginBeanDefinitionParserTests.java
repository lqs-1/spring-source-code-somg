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
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.config.test.SpringTestRule;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.TestClientRegistrations;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.core.endpoint.TestOAuth2AuthorizationRequests;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.core.user.TestOAuth2Users;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoderFactory;
import org.springframework.security.oauth2.jwt.TestJwts;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.oauth2.core.endpoint.TestOAuth2AccessTokenResponses.accessTokenResponse;
import static org.springframework.security.oauth2.core.endpoint.TestOAuth2AccessTokenResponses.oidcAccessTokenResponse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Tests for {@link OAuth2LoginBeanDefinitionParser}.
 *
 * @author Ruby Hartono
 */
public class OAuth2LoginBeanDefinitionParserTests {
	private static final String CONFIG_LOCATION_PREFIX = "classpath:org/springframework/security/config/http/OAuth2LoginBeanDefinitionParserTests";

	@Rule
	public final SpringTestRule spring = new SpringTestRule();

	@Autowired
	private ClientRegistrationRepository clientRegistrationRepository;

	@Autowired(required = false)
	private OAuth2AuthorizedClientRepository authorizedClientRepository;

	@Autowired(required = false)
	private OAuth2AuthorizedClientService authorizedClientService;

	@Autowired(required = false)
	private ApplicationListener<AuthenticationSuccessEvent> authenticationSuccessListener;

	@Autowired(required = false)
	private AuthorizationRequestRepository<OAuth2AuthorizationRequest> authorizationRequestRepository;

	@Autowired(required = false)
	private OAuth2AuthorizationRequestResolver authorizationRequestResolver;

	@Autowired(required = false)
	private OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> accessTokenResponseClient;

	@Autowired(required = false)
	private OAuth2UserService<OAuth2UserRequest, OAuth2User> oauth2UserService;

	@Autowired(required = false)
	private JwtDecoderFactory<ClientRegistration> jwtDecoderFactory;

	@Autowired(required = false)
	private GrantedAuthoritiesMapper userAuthoritiesMapper;

	@Autowired(required = false)
	private AuthenticationFailureHandler authenticationFailureHandler;

	@Autowired(required = false)
	private AuthenticationSuccessHandler authenticationSuccessHandler;

	@Autowired(required = false)
	private RequestCache requestCache;

	@Autowired
	private MockMvc mvc;

	@Test
	public void requestLoginWhenMultiClientRegistrationThenReturnLoginPageWithClients() throws Exception {
		this.spring.configLocations(this.xml("MultiClientRegistration")).autowire();

		MvcResult result = this.mvc.perform(get("/login"))
				.andExpect(status().is2xxSuccessful())
				.andReturn();

		assertThat(result.getResponse().getContentAsString())
				.contains("<a href=\"/oauth2/authorization/google-login\">Google</a>");
		assertThat(result.getResponse().getContentAsString())
				.contains("<a href=\"/oauth2/authorization/github-login\">Github</a>");
	}

	// gh-5347
	@Test
	public void requestWhenSingleClientRegistrationThenAutoRedirect() throws Exception {
		this.spring.configLocations(this.xml("SingleClientRegistration")).autowire();

		this.mvc.perform(get("/"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("http://localhost/oauth2/authorization/google-login"));

		verify(requestCache).saveRequest(any(), any());
	}

	// gh-5347
	@Test
	public void requestWhenSingleClientRegistrationAndRequestFaviconNotAuthenticatedThenRedirectDefaultLoginPage()
			throws Exception {
		this.spring.configLocations(this.xml("SingleClientRegistration")).autowire();

		this.mvc.perform(get("/favicon.ico").accept(new MediaType("image", "*")))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("http://localhost/login"));
	}

	// gh-6812
	@Test
	public void requestWhenSingleClientRegistrationAndRequestXHRNotAuthenticatedThenDoesNotRedirectForAuthorization()
			throws Exception {
		this.spring.configLocations(this.xml("SingleClientRegistration")).autowire();

		this.mvc.perform(get("/").header("X-Requested-With", "XMLHttpRequest"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("http://localhost/login"));
	}

	@Test
	public void requestWhenAuthorizationRequestNotFoundThenThrowAuthenticationException() throws Exception {
		this.spring.configLocations(this.xml("SingleClientRegistration-WithCustomAuthenticationFailureHandler"))
				.autowire();

		MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
		params.add("code", "code123");
		params.add("state", "state123");
		this.mvc.perform(get("/login/oauth2/code/google").params(params));

		ArgumentCaptor<AuthenticationException> exceptionCaptor = ArgumentCaptor
				.forClass(AuthenticationException.class);
		verify(authenticationFailureHandler).onAuthenticationFailure(any(), any(), exceptionCaptor.capture());
		AuthenticationException exception = exceptionCaptor.getValue();
		assertThat(exception).isInstanceOf(OAuth2AuthenticationException.class);
		assertThat(((OAuth2AuthenticationException) exception).getError().getErrorCode())
				.isEqualTo("authorization_request_not_found");
	}

	@Test
	public void requestWhenAuthorizationResponseValidThenAuthenticate() throws Exception {
		this.spring.configLocations(this.xml("MultiClientRegistration-WithCustomConfiguration")).autowire();

		Map<String, Object> attributes = new HashMap<>();
		attributes.put(OAuth2ParameterNames.REGISTRATION_ID, "github-login");
		OAuth2AuthorizationRequest authorizationRequest = TestOAuth2AuthorizationRequests.request()
				.attributes(attributes).build();
		when(this.authorizationRequestRepository.removeAuthorizationRequest(any(), any())).thenReturn(authorizationRequest);

		OAuth2AccessTokenResponse accessTokenResponse = accessTokenResponse().build();
		when(this.accessTokenResponseClient.getTokenResponse(any())).thenReturn(accessTokenResponse);

		OAuth2User oauth2User = TestOAuth2Users.create();
		when(this.oauth2UserService.loadUser(any())).thenReturn(oauth2User);

		MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
		params.add("code", "code123");
		params.add("state", authorizationRequest.getState());
		this.mvc.perform(get("/login/oauth2/code/github-login").params(params))
				.andExpect(status().is2xxSuccessful());

		ArgumentCaptor<Authentication> authenticationCaptor = ArgumentCaptor.forClass(Authentication.class);
		verify(authenticationSuccessHandler).onAuthenticationSuccess(any(), any(), authenticationCaptor.capture());
		Authentication authentication = authenticationCaptor.getValue();
		assertThat(authentication.getPrincipal()).isInstanceOf(OAuth2User.class);
	}

	// gh-6009
	@Test
	public void requestWhenAuthorizationResponseValidThenAuthenticationSuccessEventPublished() throws Exception {
		this.spring.configLocations(this.xml("MultiClientRegistration-WithCustomConfiguration")).autowire();

		Map<String, Object> attributes = new HashMap<>();
		attributes.put(OAuth2ParameterNames.REGISTRATION_ID, "github-login");
		OAuth2AuthorizationRequest authorizationRequest = TestOAuth2AuthorizationRequests.request()
				.attributes(attributes).build();
		when(this.authorizationRequestRepository.removeAuthorizationRequest(any(), any())).thenReturn(authorizationRequest);

		OAuth2AccessTokenResponse accessTokenResponse = accessTokenResponse().build();
		when(this.accessTokenResponseClient.getTokenResponse(any())).thenReturn(accessTokenResponse);

		OAuth2User oauth2User = TestOAuth2Users.create();
		when(this.oauth2UserService.loadUser(any())).thenReturn(oauth2User);

		MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
		params.add("code", "code123");
		params.add("state", authorizationRequest.getState());
		this.mvc.perform(get("/login/oauth2/code/github-login").params(params));

		verify(authenticationSuccessListener).onApplicationEvent(any(AuthenticationSuccessEvent.class));
	}

	@Test
	public void requestWhenOidcAuthenticationResponseValidThenJwtDecoderFactoryCalled() throws Exception {
		this.spring.configLocations(this.xml("SingleClientRegistration-WithJwtDecoderFactoryAndDefaultSuccessHandler"))
				.autowire();

		Map<String, Object> attributes = new HashMap<>();
		attributes.put(OAuth2ParameterNames.REGISTRATION_ID, "google-login");
		OAuth2AuthorizationRequest authorizationRequest = TestOAuth2AuthorizationRequests.oidcRequest()
				.attributes(attributes).build();
		when(this.authorizationRequestRepository.removeAuthorizationRequest(any(), any())).thenReturn(authorizationRequest);

		OAuth2AccessTokenResponse accessTokenResponse = oidcAccessTokenResponse().build();
		when(this.accessTokenResponseClient.getTokenResponse(any())).thenReturn(accessTokenResponse);

		Jwt jwt = TestJwts.user();
		when(this.jwtDecoderFactory.createDecoder(any())).thenReturn(token -> jwt);

		MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
		params.add("code", "code123");
		params.add("state", authorizationRequest.getState());
		this.mvc.perform(get("/login/oauth2/code/google-login").params(params))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/"));

		verify(this.jwtDecoderFactory).createDecoder(any());
		verify(this.requestCache).getRequest(any(), any());
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Test
	public void requestWhenCustomGrantedAuthoritiesMapperThenCalled() throws Exception {
		this.spring.configLocations(this.xml("MultiClientRegistration-WithCustomGrantedAuthorities")).autowire();

		Map<String, Object> attributes = new HashMap<>();
		attributes.put(OAuth2ParameterNames.REGISTRATION_ID, "github-login");
		OAuth2AuthorizationRequest authorizationRequest = TestOAuth2AuthorizationRequests.request()
				.attributes(attributes).build();
		when(this.authorizationRequestRepository.removeAuthorizationRequest(any(), any())).thenReturn(authorizationRequest);

		OAuth2AccessTokenResponse accessTokenResponse = accessTokenResponse().build();
		when(this.accessTokenResponseClient.getTokenResponse(any())).thenReturn(accessTokenResponse);

		OAuth2User oauth2User = TestOAuth2Users.create();
		when(this.oauth2UserService.loadUser(any())).thenReturn(oauth2User);

		when(this.userAuthoritiesMapper.mapAuthorities(any())).thenReturn(
				(Collection) AuthorityUtils.createAuthorityList("ROLE_OAUTH2_USER"));

		MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
		params.add("code", "code123");
		params.add("state", authorizationRequest.getState());
		this.mvc.perform(get("/login/oauth2/code/github-login").params(params))
				.andExpect(status().is2xxSuccessful());

		ArgumentCaptor<Authentication> authenticationCaptor = ArgumentCaptor.forClass(Authentication.class);
		verify(authenticationSuccessHandler).onAuthenticationSuccess(any(), any(), authenticationCaptor.capture());
		Authentication authentication = authenticationCaptor.getValue();
		assertThat(authentication.getPrincipal()).isInstanceOf(OAuth2User.class);
		assertThat(authentication.getAuthorities()).hasSize(1);
		assertThat(authentication.getAuthorities()).first().isInstanceOf(SimpleGrantedAuthority.class)
				.hasToString("ROLE_OAUTH2_USER");

		// re-setup for OIDC test
		attributes = new HashMap<>();
		attributes.put(OAuth2ParameterNames.REGISTRATION_ID, "google-login");
		authorizationRequest = TestOAuth2AuthorizationRequests.oidcRequest()
				.attributes(attributes).build();
		when(this.authorizationRequestRepository.removeAuthorizationRequest(any(), any())).thenReturn(authorizationRequest);

		accessTokenResponse = oidcAccessTokenResponse().build();
		when(this.accessTokenResponseClient.getTokenResponse(any())).thenReturn(accessTokenResponse);

		Jwt jwt = TestJwts.user();
		when(this.jwtDecoderFactory.createDecoder(any())).thenReturn(token -> jwt);

		when(this.userAuthoritiesMapper.mapAuthorities(any()))
				.thenReturn((Collection) AuthorityUtils.createAuthorityList("ROLE_OIDC_USER"));

		this.mvc.perform(get("/login/oauth2/code/google-login").params(params))
				.andExpect(status().is2xxSuccessful());

		authenticationCaptor = ArgumentCaptor.forClass(Authentication.class);
		verify(authenticationSuccessHandler, times(2)).onAuthenticationSuccess(any(), any(),
				authenticationCaptor.capture());
		authentication = authenticationCaptor.getValue();
		assertThat(authentication.getPrincipal()).isInstanceOf(OidcUser.class);
		assertThat(authentication.getAuthorities()).hasSize(1);
		assertThat(authentication.getAuthorities()).first().isInstanceOf(SimpleGrantedAuthority.class)
				.hasToString("ROLE_OIDC_USER");
	}

	// gh-5488
	@Test
	public void requestWhenCustomLoginProcessingUrlThenProcessAuthentication() throws Exception {
		this.spring.configLocations(this.xml("MultiClientRegistration-WithCustomLoginProcessingUrl")).autowire();

		Map<String, Object> attributes = new HashMap<>();
		attributes.put(OAuth2ParameterNames.REGISTRATION_ID, "github-login");
		OAuth2AuthorizationRequest authorizationRequest = TestOAuth2AuthorizationRequests.request()
				.attributes(attributes).build();
		when(this.authorizationRequestRepository.removeAuthorizationRequest(any(), any())).thenReturn(authorizationRequest);

		OAuth2AccessTokenResponse accessTokenResponse = accessTokenResponse().build();
		when(this.accessTokenResponseClient.getTokenResponse(any())).thenReturn(accessTokenResponse);

		OAuth2User oauth2User = TestOAuth2Users.create();
		when(this.oauth2UserService.loadUser(any())).thenReturn(oauth2User);

		MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
		params.add("code", "code123");
		params.add("state", authorizationRequest.getState());
		this.mvc.perform(get("/login/oauth2/github-login").params(params))
				.andExpect(status().is2xxSuccessful());

		ArgumentCaptor<Authentication> authenticationCaptor = ArgumentCaptor.forClass(Authentication.class);
		verify(authenticationSuccessHandler).onAuthenticationSuccess(any(), any(), authenticationCaptor.capture());
		Authentication authentication = authenticationCaptor.getValue();
		assertThat(authentication.getPrincipal()).isInstanceOf(OAuth2User.class);
	}

	// gh-5521
	@Test
	public void requestWhenCustomAuthorizationRequestResolverThenCalled() throws Exception {
		this.spring.configLocations(this.xml("SingleClientRegistration-WithCustomAuthorizationRequestResolver"))
				.autowire();

		this.mvc.perform(get("/oauth2/authorization/google-login"))
				.andExpect(status().is3xxRedirection());

		verify(authorizationRequestResolver).resolve(any());
	}

	// gh-5347
	@Test
	public void requestWhenMultiClientRegistrationThenRedirectDefaultLoginPage() throws Exception {
		this.spring.configLocations(this.xml("MultiClientRegistration")).autowire();

		this.mvc.perform(get("/"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("http://localhost/login"));
	}

	@Test
	public void requestWhenCustomLoginPageThenRedirectCustomLoginPage() throws Exception {
		this.spring.configLocations(this.xml("SingleClientRegistration-WithCustomLoginPage")).autowire();

		this.mvc.perform(get("/"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("http://localhost/custom-login"));
	}

	// gh-6802
	@Test
	public void requestWhenSingleClientRegistrationAndFormLoginConfiguredThenRedirectDefaultLoginPage()
			throws Exception {
		this.spring.configLocations(this.xml("SingleClientRegistration-WithFormLogin")).autowire();

		this.mvc.perform(get("/"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("http://localhost/login"));
	}

	@Test
	public void requestWhenCustomClientRegistrationRepositoryThenCalled() throws Exception {
		this.spring.configLocations(this.xml("WithCustomClientRegistrationRepository")).autowire();

		ClientRegistration clientRegistration = TestClientRegistrations.clientRegistration().build();
		when(this.clientRegistrationRepository.findByRegistrationId(any())).thenReturn(clientRegistration);

		Map<String, Object> attributes = new HashMap<>();
		attributes.put(OAuth2ParameterNames.REGISTRATION_ID, clientRegistration.getRegistrationId());
		OAuth2AuthorizationRequest authorizationRequest = TestOAuth2AuthorizationRequests.request()
				.attributes(attributes).build();
		when(this.authorizationRequestRepository.removeAuthorizationRequest(any(), any())).thenReturn(authorizationRequest);

		OAuth2AccessTokenResponse accessTokenResponse = accessTokenResponse().build();
		when(this.accessTokenResponseClient.getTokenResponse(any())).thenReturn(accessTokenResponse);

		OAuth2User oauth2User = TestOAuth2Users.create();
		when(this.oauth2UserService.loadUser(any())).thenReturn(oauth2User);

		MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
		params.add("code", "code123");
		params.add("state", authorizationRequest.getState());
		this.mvc.perform(get("/login/oauth2/code/" + clientRegistration.getRegistrationId()).params(params));

		verify(clientRegistrationRepository).findByRegistrationId(clientRegistration.getRegistrationId());
	}

	@Test
	public void requestWhenCustomAuthorizedClientRepositoryThenCalled() throws Exception {
		this.spring.configLocations(this.xml("WithCustomAuthorizedClientRepository")).autowire();

		ClientRegistration clientRegistration = TestClientRegistrations.clientRegistration().build();
		when(this.clientRegistrationRepository.findByRegistrationId(any())).thenReturn(clientRegistration);

		Map<String, Object> attributes = new HashMap<>();
		attributes.put(OAuth2ParameterNames.REGISTRATION_ID, clientRegistration.getRegistrationId());
		OAuth2AuthorizationRequest authorizationRequest = TestOAuth2AuthorizationRequests.request()
				.attributes(attributes).build();
		when(this.authorizationRequestRepository.removeAuthorizationRequest(any(), any())).thenReturn(authorizationRequest);

		OAuth2AccessTokenResponse accessTokenResponse = accessTokenResponse().build();
		when(this.accessTokenResponseClient.getTokenResponse(any())).thenReturn(accessTokenResponse);

		OAuth2User oauth2User = TestOAuth2Users.create();
		when(this.oauth2UserService.loadUser(any())).thenReturn(oauth2User);

		MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
		params.add("code", "code123");
		params.add("state", authorizationRequest.getState());
		this.mvc.perform(get("/login/oauth2/code/" + clientRegistration.getRegistrationId()).params(params));

		verify(authorizedClientRepository).saveAuthorizedClient(any(), any(), any(), any());
	}

	@Test
	public void requestWhenCustomAuthorizedClientServiceThenCalled() throws Exception {
		this.spring.configLocations(this.xml("WithCustomAuthorizedClientService")).autowire();

		ClientRegistration clientRegistration = TestClientRegistrations.clientRegistration().build();
		when(this.clientRegistrationRepository.findByRegistrationId(any())).thenReturn(clientRegistration);

		Map<String, Object> attributes = new HashMap<>();
		attributes.put(OAuth2ParameterNames.REGISTRATION_ID, clientRegistration.getRegistrationId());
		OAuth2AuthorizationRequest authorizationRequest = TestOAuth2AuthorizationRequests.request()
				.attributes(attributes).build();
		when(this.authorizationRequestRepository.removeAuthorizationRequest(any(), any())).thenReturn(authorizationRequest);

		OAuth2AccessTokenResponse accessTokenResponse = accessTokenResponse().build();
		when(this.accessTokenResponseClient.getTokenResponse(any())).thenReturn(accessTokenResponse);

		OAuth2User oauth2User = TestOAuth2Users.create();
		when(this.oauth2UserService.loadUser(any())).thenReturn(oauth2User);

		MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
		params.add("code", "code123");
		params.add("state", authorizationRequest.getState());
		this.mvc.perform(get("/login/oauth2/code/" + clientRegistration.getRegistrationId()).params(params));

		verify(authorizedClientService).saveAuthorizedClient(any(), any());
	}

	private String xml(String configName) {
		return CONFIG_LOCATION_PREFIX + "-" + configName + ".xml";
	}
}
