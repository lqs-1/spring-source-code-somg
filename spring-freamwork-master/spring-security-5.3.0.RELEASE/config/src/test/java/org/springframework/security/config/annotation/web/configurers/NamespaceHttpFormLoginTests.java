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
package org.springframework.security.config.annotation.web.configurers;

import javax.servlet.http.HttpServletRequest;

import org.junit.Rule;
import org.junit.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.test.SpringTestRule;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

/**
 * Tests to verify that all the functionality of <form-login> attributes is present
 *
 * @author Rob Winch
 * @author Josh Cummings
 *
 */
public class NamespaceHttpFormLoginTests {

	@Rule
	public final SpringTestRule spring = new SpringTestRule();

	@Autowired
	MockMvc mvc;


	@Test
	public void formLoginWhenDefaultConfigurationThenMatchesNamespace() throws Exception {
		this.spring.register(FormLoginConfig.class, UserDetailsServiceConfig.class).autowire();

		this.mvc.perform(get("/"))
				.andExpect(redirectedUrl("http://localhost/login"));

		this.mvc.perform(post("/login")
				.with(csrf()))
				.andExpect(redirectedUrl("/login?error"));

		this.mvc.perform(post("/login")
				.param("username", "user")
				.param("password", "password")
				.with(csrf()))
				.andExpect(redirectedUrl("/"));
	}

	@EnableWebSecurity
	static class FormLoginConfig extends WebSecurityConfigurerAdapter {

		@Override
		public void configure(WebSecurity web) {
			web
				.ignoring()
					.antMatchers("/resources/**");
		}

		@Override
		protected void configure(HttpSecurity http) throws Exception {
			http
				.authorizeRequests()
					.anyRequest().hasRole("USER")
					.and()
				.formLogin();
		}
	}

	@Test
	public void formLoginWithCustomEndpointsThenBehaviorMatchesNamespace() throws Exception {
		this.spring.register(FormLoginCustomConfig.class, UserDetailsServiceConfig.class).autowire();

		this.mvc.perform(get("/"))
				.andExpect(redirectedUrl("http://localhost/authentication/login"));

		this.mvc.perform(post("/authentication/login/process")
				.with(csrf()))
				.andExpect(redirectedUrl("/authentication/login?failed"));

		this.mvc.perform(post("/authentication/login/process")
				.param("username", "user")
				.param("password", "password")
				.with(csrf()))
				.andExpect(redirectedUrl("/default"));
	}

	@EnableWebSecurity
	static class FormLoginCustomConfig extends WebSecurityConfigurerAdapter {
		protected void configure(HttpSecurity http) throws Exception {
			boolean alwaysUseDefaultSuccess = true;
			http
				.authorizeRequests()
					.anyRequest().hasRole("USER")
					.and()
				.formLogin()
					.usernameParameter("username") // form-login@username-parameter
					.passwordParameter("password") // form-login@password-parameter
					.loginPage("/authentication/login") // form-login@login-page
					.failureUrl("/authentication/login?failed") // form-login@authentication-failure-url
					.loginProcessingUrl("/authentication/login/process") // form-login@login-processing-url
					.defaultSuccessUrl("/default", alwaysUseDefaultSuccess); // form-login@default-target-url / form-login@always-use-default-target
		}
	}

	@Test
	public void formLoginWithCustomHandlersThenBehaviorMatchesNamespace() throws Exception {
		this.spring.register(FormLoginCustomRefsConfig.class, UserDetailsServiceConfig.class).autowire();

		this.mvc.perform(get("/"))
				.andExpect(redirectedUrl("http://localhost/login"));

		this.mvc.perform(post("/login")
				.with(csrf()))
				.andExpect(redirectedUrl("/custom/failure"));
		verifyBean(WebAuthenticationDetailsSource.class).buildDetails(any(HttpServletRequest.class));

		this.mvc.perform(post("/login")
				.param("username", "user")
				.param("password", "password")
				.with(csrf()))
				.andExpect(redirectedUrl("/custom/targetUrl"));
	}

	@EnableWebSecurity
	static class FormLoginCustomRefsConfig extends WebSecurityConfigurerAdapter {
		protected void configure(HttpSecurity http) throws Exception {
			SavedRequestAwareAuthenticationSuccessHandler successHandler =
					new SavedRequestAwareAuthenticationSuccessHandler();
			successHandler.setDefaultTargetUrl("/custom/targetUrl");

			http
				.authorizeRequests()
					.anyRequest().hasRole("USER")
					.and()
				.formLogin()
					.loginPage("/login")
					.failureHandler(new SimpleUrlAuthenticationFailureHandler("/custom/failure")) // form-login@authentication-failure-handler-ref
					.successHandler(successHandler) // form-login@authentication-success-handler-ref
					.authenticationDetailsSource(authenticationDetailsSource()) // form-login@authentication-details-source-ref
					.and();
		}

		@Bean
		WebAuthenticationDetailsSource authenticationDetailsSource() {
			return spy(WebAuthenticationDetailsSource.class);
		}
	}

	@Configuration
	static class UserDetailsServiceConfig {
		@Bean
		public UserDetailsService userDetailsService() {
			return new InMemoryUserDetailsManager(
					User.withDefaultPasswordEncoder()
							.username("user")
							.password("password")
							.roles("USER")
							.build());
		}
	}

	private <T> T verifyBean(Class<T> beanClass) {
		return verify(this.spring.getContext().getBean(beanClass));
	}
}
