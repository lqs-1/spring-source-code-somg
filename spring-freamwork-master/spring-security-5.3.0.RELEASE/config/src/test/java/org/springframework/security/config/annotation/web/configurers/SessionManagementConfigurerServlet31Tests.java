/*
 * Copyright 2002-2013 the original author or authors.
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
package org.springframework.security.config.annotation.web.configurers;

import java.lang.reflect.Method;
import javax.servlet.Filter;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.modules.junit4.PowerMockRunner;

import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.core.userdetails.PasswordEncodedUser;
import org.springframework.security.web.context.HttpRequestResponseHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

/**
 *
 * @author Rob Winch
 */
@RunWith(PowerMockRunner.class)
@PowerMockIgnore({ "org.w3c.dom.*", "org.xml.sax.*", "org.apache.xerces.*", "javax.xml.parsers.*" })
public class SessionManagementConfigurerServlet31Tests {
	@Mock
	Method method;

	MockHttpServletRequest request;
	MockHttpServletResponse response;
	MockFilterChain chain;

	ConfigurableApplicationContext context;

	Filter springSecurityFilterChain;

	@Before
	public void setup() {
		request = new MockHttpServletRequest("GET", "");
		response = new MockHttpServletResponse();
		chain = new MockFilterChain();
	}

	@After
	public void teardown() {
		if (context != null) {
			context.close();
		}
	}

	@Test
	public void changeSessionIdThenPreserveParameters() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "");
		String id =  request.getSession().getId();
		request.getSession();
		request.setServletPath("/login");
		request.setMethod("POST");
		request.setParameter("username", "user");
		request.setParameter("password", "password");
		HttpSessionCsrfTokenRepository repository = new HttpSessionCsrfTokenRepository();
		CsrfToken token = repository.generateToken(request);
		repository.saveToken(token, request, response);
		request.setParameter(token.getParameterName(), token.getToken());
		request.getSession().setAttribute("attribute1", "value1");

		loadConfig(SessionManagementDefaultSessionFixationServlet31Config.class);

		springSecurityFilterChain.doFilter(request, response, chain);

		assertThat(request.getSession().getId()).isNotEqualTo(id);
		assertThat(request.getSession().getAttribute("attribute1")).isEqualTo("value1");
	}

	@EnableWebSecurity
	static class SessionManagementDefaultSessionFixationServlet31Config extends
			WebSecurityConfigurerAdapter {
		// @formatter:off
		@Override
		protected void configure(HttpSecurity http) throws Exception {
			http
				.formLogin()
					.and()
				.sessionManagement();
		}
		// @formatter:on

		// @formatter:off
		@Override
		protected void configure(AuthenticationManagerBuilder auth) throws Exception {
			auth
				.inMemoryAuthentication()
					.withUser(PasswordEncodedUser.user());
		}
		// @formatter:on
	}

	private void loadConfig(Class<?>... classes) {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
		context.register(classes);
		context.refresh();
		this.context = context;
		this.springSecurityFilterChain = this.context.getBean(
				"springSecurityFilterChain", Filter.class);
	}

	private void login(Authentication auth) {
		HttpSessionSecurityContextRepository repo = new HttpSessionSecurityContextRepository();
		HttpRequestResponseHolder requestResponseHolder = new HttpRequestResponseHolder(
				request, response);
		repo.loadContext(requestResponseHolder);

		SecurityContextImpl securityContextImpl = new SecurityContextImpl();
		securityContextImpl.setAuthentication(auth);
		repo.saveContext(securityContextImpl, requestResponseHolder.getRequest(),
				requestResponseHolder.getResponse());
	}
}
