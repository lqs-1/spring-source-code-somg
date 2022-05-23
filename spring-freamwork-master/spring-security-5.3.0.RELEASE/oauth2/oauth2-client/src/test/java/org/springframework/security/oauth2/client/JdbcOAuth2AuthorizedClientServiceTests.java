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
package org.springframework.security.oauth2.client;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.ArgumentPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.TestClientRegistrations;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.TestOAuth2AccessTokens;
import org.springframework.security.oauth2.core.TestOAuth2RefreshTokens;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link JdbcOAuth2AuthorizedClientService}.
 *
 * @author Joe Grandja
 */
public class JdbcOAuth2AuthorizedClientServiceTests {
	private static final String OAUTH2_CLIENT_SCHEMA_SQL_RESOURCE = "org/springframework/security/oauth2/client/oauth2-client-schema.sql";
	private static int principalId = 1000;
	private ClientRegistration clientRegistration;
	private ClientRegistrationRepository clientRegistrationRepository;
	private EmbeddedDatabase db;
	private JdbcOperations jdbcOperations;
	private JdbcOAuth2AuthorizedClientService authorizedClientService;

	@Before
	public void setUp() {
		this.clientRegistration = TestClientRegistrations.clientRegistration().build();
		this.clientRegistrationRepository = mock(ClientRegistrationRepository.class);
		when(this.clientRegistrationRepository.findByRegistrationId(any())).thenReturn(this.clientRegistration);
		this.db = createDb();
		this.jdbcOperations = new JdbcTemplate(this.db);
		this.authorizedClientService = new JdbcOAuth2AuthorizedClientService(
				this.jdbcOperations, this.clientRegistrationRepository);
	}

	@After
	public void tearDown() {
		this.db.shutdown();
	}

	@Test
	public void constructorWhenJdbcOperationsIsNullThenThrowIllegalArgumentException() {
		assertThatThrownBy(() -> new JdbcOAuth2AuthorizedClientService(null, this.clientRegistrationRepository))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("jdbcOperations cannot be null");
	}

	@Test
	public void constructorWhenClientRegistrationRepositoryIsNullThenThrowIllegalArgumentException() {
		assertThatThrownBy(() -> new JdbcOAuth2AuthorizedClientService(this.jdbcOperations, null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("clientRegistrationRepository cannot be null");
	}

	@Test
	public void setAuthorizedClientRowMapperWhenNullThenThrowIllegalArgumentException() {
		assertThatThrownBy(() -> this.authorizedClientService.setAuthorizedClientRowMapper(null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("authorizedClientRowMapper cannot be null");
	}

	@Test
	public void setAuthorizedClientParametersMapperWhenNullThenThrowIllegalArgumentException() {
		assertThatThrownBy(() -> this.authorizedClientService.setAuthorizedClientParametersMapper(null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("authorizedClientParametersMapper cannot be null");
	}

	@Test
	public void loadAuthorizedClientWhenClientRegistrationIdIsNullThenThrowIllegalArgumentException() {
		assertThatThrownBy(() -> this.authorizedClientService.loadAuthorizedClient(null, "principalName"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("clientRegistrationId cannot be empty");
	}

	@Test
	public void loadAuthorizedClientWhenPrincipalNameIsNullThenThrowIllegalArgumentException() {
		assertThatThrownBy(() -> this.authorizedClientService.loadAuthorizedClient(this.clientRegistration.getRegistrationId(), null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("principalName cannot be empty");
	}

	@Test
	public void loadAuthorizedClientWhenDoesNotExistThenReturnNull() {
		OAuth2AuthorizedClient authorizedClient = this.authorizedClientService.loadAuthorizedClient(
				"registration-not-found", "principalName");
		assertThat(authorizedClient).isNull();
	}

	@Test
	public void loadAuthorizedClientWhenExistsThenReturnAuthorizedClient() {
		Authentication principal = createPrincipal();
		OAuth2AuthorizedClient expected = createAuthorizedClient(principal, this.clientRegistration);

		this.authorizedClientService.saveAuthorizedClient(expected, principal);

		OAuth2AuthorizedClient authorizedClient = this.authorizedClientService.loadAuthorizedClient(
				this.clientRegistration.getRegistrationId(), principal.getName());

		assertThat(authorizedClient).isNotNull();
		assertThat(authorizedClient.getClientRegistration()).isEqualTo(expected.getClientRegistration());
		assertThat(authorizedClient.getPrincipalName()).isEqualTo(expected.getPrincipalName());
		assertThat(authorizedClient.getAccessToken().getTokenType()).isEqualTo(expected.getAccessToken().getTokenType());
		assertThat(authorizedClient.getAccessToken().getTokenValue()).isEqualTo(expected.getAccessToken().getTokenValue());
		assertThat(authorizedClient.getAccessToken().getIssuedAt()).isEqualTo(expected.getAccessToken().getIssuedAt());
		assertThat(authorizedClient.getAccessToken().getExpiresAt()).isEqualTo(expected.getAccessToken().getExpiresAt());
		assertThat(authorizedClient.getAccessToken().getScopes()).isEqualTo(expected.getAccessToken().getScopes());
		assertThat(authorizedClient.getRefreshToken().getTokenValue()).isEqualTo(expected.getRefreshToken().getTokenValue());
		assertThat(authorizedClient.getRefreshToken().getIssuedAt()).isEqualTo(expected.getRefreshToken().getIssuedAt());
	}

	@Test
	public void loadAuthorizedClientWhenExistsButNotFoundInClientRegistrationRepositoryThenThrowDataRetrievalFailureException() {
		when(this.clientRegistrationRepository.findByRegistrationId(any())).thenReturn(null);
		Authentication principal = createPrincipal();
		OAuth2AuthorizedClient expected = createAuthorizedClient(principal, this.clientRegistration);

		this.authorizedClientService.saveAuthorizedClient(expected, principal);

		assertThatThrownBy(() -> this.authorizedClientService.loadAuthorizedClient(this.clientRegistration.getRegistrationId(), principal.getName()))
				.isInstanceOf(DataRetrievalFailureException.class)
				.hasMessage("The ClientRegistration with id '" + this.clientRegistration.getRegistrationId() +
						"' exists in the data source, however, it was not found in the ClientRegistrationRepository.");
	}

	@Test
	public void saveAuthorizedClientWhenAuthorizedClientIsNullThenThrowIllegalArgumentException() {
		Authentication principal = createPrincipal();

		assertThatThrownBy(() -> this.authorizedClientService.saveAuthorizedClient(null, principal))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("authorizedClient cannot be null");
	}

	@Test
	public void saveAuthorizedClientWhenPrincipalIsNullThenThrowIllegalArgumentException() {
		Authentication principal = createPrincipal();
		OAuth2AuthorizedClient authorizedClient = createAuthorizedClient(principal, this.clientRegistration);

		assertThatThrownBy(() -> this.authorizedClientService.saveAuthorizedClient(authorizedClient, null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("principal cannot be null");
	}

	@Test
	public void saveAuthorizedClientWhenSaveThenLoadReturnsSaved() {
		Authentication principal = createPrincipal();
		OAuth2AuthorizedClient expected = createAuthorizedClient(principal, this.clientRegistration);

		this.authorizedClientService.saveAuthorizedClient(expected, principal);

		OAuth2AuthorizedClient authorizedClient = this.authorizedClientService.loadAuthorizedClient(
				this.clientRegistration.getRegistrationId(), principal.getName());

		assertThat(authorizedClient).isNotNull();
		assertThat(authorizedClient.getClientRegistration()).isEqualTo(expected.getClientRegistration());
		assertThat(authorizedClient.getPrincipalName()).isEqualTo(expected.getPrincipalName());
		assertThat(authorizedClient.getAccessToken().getTokenType()).isEqualTo(expected.getAccessToken().getTokenType());
		assertThat(authorizedClient.getAccessToken().getTokenValue()).isEqualTo(expected.getAccessToken().getTokenValue());
		assertThat(authorizedClient.getAccessToken().getIssuedAt()).isEqualTo(expected.getAccessToken().getIssuedAt());
		assertThat(authorizedClient.getAccessToken().getExpiresAt()).isEqualTo(expected.getAccessToken().getExpiresAt());
		assertThat(authorizedClient.getAccessToken().getScopes()).isEqualTo(expected.getAccessToken().getScopes());
		assertThat(authorizedClient.getRefreshToken().getTokenValue()).isEqualTo(expected.getRefreshToken().getTokenValue());
		assertThat(authorizedClient.getRefreshToken().getIssuedAt()).isEqualTo(expected.getRefreshToken().getIssuedAt());

		// Test save/load of NOT NULL attributes only
		principal = createPrincipal();
		expected = createAuthorizedClient(principal, this.clientRegistration, true);

		this.authorizedClientService.saveAuthorizedClient(expected, principal);

		authorizedClient = this.authorizedClientService.loadAuthorizedClient(
				this.clientRegistration.getRegistrationId(), principal.getName());

		assertThat(authorizedClient).isNotNull();
		assertThat(authorizedClient.getClientRegistration()).isEqualTo(expected.getClientRegistration());
		assertThat(authorizedClient.getPrincipalName()).isEqualTo(expected.getPrincipalName());
		assertThat(authorizedClient.getAccessToken().getTokenType()).isEqualTo(expected.getAccessToken().getTokenType());
		assertThat(authorizedClient.getAccessToken().getTokenValue()).isEqualTo(expected.getAccessToken().getTokenValue());
		assertThat(authorizedClient.getAccessToken().getIssuedAt()).isEqualTo(expected.getAccessToken().getIssuedAt());
		assertThat(authorizedClient.getAccessToken().getExpiresAt()).isEqualTo(expected.getAccessToken().getExpiresAt());
		assertThat(authorizedClient.getAccessToken().getScopes()).isEmpty();
		assertThat(authorizedClient.getRefreshToken()).isNull();
	}

	@Test
	public void saveAuthorizedClientWhenSaveDuplicateThenThrowDuplicateKeyException() {
		Authentication principal = createPrincipal();
		OAuth2AuthorizedClient authorizedClient = createAuthorizedClient(principal, this.clientRegistration);

		this.authorizedClientService.saveAuthorizedClient(authorizedClient, principal);

		assertThatThrownBy(() -> this.authorizedClientService.saveAuthorizedClient(authorizedClient, principal))
				.isInstanceOf(DuplicateKeyException.class);
	}

	@Test
	public void saveLoadAuthorizedClientWhenCustomStrategiesSetThenCalled() throws Exception {
		JdbcOAuth2AuthorizedClientService.OAuth2AuthorizedClientRowMapper authorizedClientRowMapper =
				spy(new JdbcOAuth2AuthorizedClientService.OAuth2AuthorizedClientRowMapper(this.clientRegistrationRepository));
		this.authorizedClientService.setAuthorizedClientRowMapper(authorizedClientRowMapper);
		JdbcOAuth2AuthorizedClientService.OAuth2AuthorizedClientParametersMapper authorizedClientParametersMapper =
				spy(new JdbcOAuth2AuthorizedClientService.OAuth2AuthorizedClientParametersMapper());
		this.authorizedClientService.setAuthorizedClientParametersMapper(authorizedClientParametersMapper);

		Authentication principal = createPrincipal();
		OAuth2AuthorizedClient authorizedClient = createAuthorizedClient(principal, this.clientRegistration);

		this.authorizedClientService.saveAuthorizedClient(authorizedClient, principal);
		this.authorizedClientService.loadAuthorizedClient(
				this.clientRegistration.getRegistrationId(), principal.getName());

		verify(authorizedClientRowMapper).mapRow(any(), anyInt());
		verify(authorizedClientParametersMapper).apply(any());
	}

	@Test
	public void removeAuthorizedClientWhenClientRegistrationIdIsNullThenThrowIllegalArgumentException() {
		assertThatThrownBy(() -> this.authorizedClientService.removeAuthorizedClient(null, "principalName"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("clientRegistrationId cannot be empty");
	}

	@Test
	public void removeAuthorizedClientWhenPrincipalNameIsNullThenThrowIllegalArgumentException() {
		assertThatThrownBy(() -> this.authorizedClientService.removeAuthorizedClient(this.clientRegistration.getRegistrationId(), null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("principalName cannot be empty");
	}

	@Test
	public void removeAuthorizedClientWhenExistsThenRemoved() {
		Authentication principal = createPrincipal();
		OAuth2AuthorizedClient authorizedClient = createAuthorizedClient(principal, this.clientRegistration);

		this.authorizedClientService.saveAuthorizedClient(authorizedClient, principal);

		authorizedClient = this.authorizedClientService.loadAuthorizedClient(
				this.clientRegistration.getRegistrationId(), principal.getName());
		assertThat(authorizedClient).isNotNull();

		this.authorizedClientService.removeAuthorizedClient(
				this.clientRegistration.getRegistrationId(), principal.getName());

		authorizedClient = this.authorizedClientService.loadAuthorizedClient(
				this.clientRegistration.getRegistrationId(), principal.getName());
		assertThat(authorizedClient).isNull();
	}

	@Test
	public void tableDefinitionWhenCustomThenAbleToOverride() {
		CustomTableDefinitionJdbcOAuth2AuthorizedClientService customAuthorizedClientService =
				new CustomTableDefinitionJdbcOAuth2AuthorizedClientService(
						new JdbcTemplate(createDb("custom-oauth2-client-schema.sql")),
						this.clientRegistrationRepository);

		Authentication principal = createPrincipal();
		OAuth2AuthorizedClient authorizedClient = createAuthorizedClient(principal, this.clientRegistration);

		customAuthorizedClientService.saveAuthorizedClient(authorizedClient, principal);

		authorizedClient = customAuthorizedClientService.loadAuthorizedClient(
				this.clientRegistration.getRegistrationId(), principal.getName());
		assertThat(authorizedClient).isNotNull();

		customAuthorizedClientService.removeAuthorizedClient(
				this.clientRegistration.getRegistrationId(), principal.getName());

		authorizedClient = customAuthorizedClientService.loadAuthorizedClient(
				this.clientRegistration.getRegistrationId(), principal.getName());
		assertThat(authorizedClient).isNull();
	}

	private static EmbeddedDatabase createDb() {
		return createDb(OAUTH2_CLIENT_SCHEMA_SQL_RESOURCE);
	}

	private static EmbeddedDatabase createDb(String schema) {
		return new EmbeddedDatabaseBuilder()
				.generateUniqueName(true)
				.setType(EmbeddedDatabaseType.HSQL)
				.setScriptEncoding("UTF-8")
				.addScript(schema)
				.build();
	}

	private static Authentication createPrincipal() {
		return new TestingAuthenticationToken("principal-" + principalId++, "password");
	}

	private static OAuth2AuthorizedClient createAuthorizedClient(Authentication principal, ClientRegistration clientRegistration) {
		return createAuthorizedClient(principal, clientRegistration, false);
	}

	private static OAuth2AuthorizedClient createAuthorizedClient(Authentication principal,
			ClientRegistration clientRegistration, boolean requiredAttributesOnly) {
		OAuth2AccessToken accessToken;
		if (!requiredAttributesOnly) {
			accessToken = TestOAuth2AccessTokens.scopes("read", "write");
		} else {
			accessToken = TestOAuth2AccessTokens.noScopes();
		}
		OAuth2RefreshToken refreshToken = null;
		if (!requiredAttributesOnly) {
			refreshToken = TestOAuth2RefreshTokens.refreshToken();
		}
		return new OAuth2AuthorizedClient(
				clientRegistration, principal.getName(), accessToken, refreshToken);
	}

	private static class CustomTableDefinitionJdbcOAuth2AuthorizedClientService extends JdbcOAuth2AuthorizedClientService {
		private static final String COLUMN_NAMES =
				"clientRegistrationId, " +
				"principalName, " +
				"accessTokenType, " +
				"accessTokenValue, " +
				"accessTokenIssuedAt, " +
				"accessTokenExpiresAt, " +
				"accessTokenScopes, " +
				"refreshTokenValue, " +
				"refreshTokenIssuedAt";
		private static final String TABLE_NAME = "oauth2AuthorizedClient";
		private static final String PK_FILTER = "clientRegistrationId = ? AND principalName = ?";
		private static final String LOAD_AUTHORIZED_CLIENT_SQL = "SELECT " + COLUMN_NAMES +
				" FROM " + TABLE_NAME + " WHERE " + PK_FILTER;
		private static final String SAVE_AUTHORIZED_CLIENT_SQL = "INSERT INTO " + TABLE_NAME +
				" (" + COLUMN_NAMES + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
		private static final String REMOVE_AUTHORIZED_CLIENT_SQL = "DELETE FROM " + TABLE_NAME +
				" WHERE " + PK_FILTER;

		private CustomTableDefinitionJdbcOAuth2AuthorizedClientService(
				JdbcOperations jdbcOperations, ClientRegistrationRepository clientRegistrationRepository) {
			super(jdbcOperations, clientRegistrationRepository);
			setAuthorizedClientRowMapper(new OAuth2AuthorizedClientRowMapper(clientRegistrationRepository));
		}

		@Override
		@SuppressWarnings("unchecked")
		public <T extends OAuth2AuthorizedClient> T loadAuthorizedClient(String clientRegistrationId, String principalName) {
			SqlParameterValue[] parameters = new SqlParameterValue[] {
					new SqlParameterValue(Types.VARCHAR, clientRegistrationId),
					new SqlParameterValue(Types.VARCHAR, principalName)
			};
			PreparedStatementSetter pss = new ArgumentPreparedStatementSetter(parameters);
			List<OAuth2AuthorizedClient> result = this.jdbcOperations.query(
					LOAD_AUTHORIZED_CLIENT_SQL, pss, this.authorizedClientRowMapper);
			return !result.isEmpty() ? (T) result.get(0) : null;
		}

		@Override
		public void saveAuthorizedClient(OAuth2AuthorizedClient authorizedClient, Authentication principal) {
			List<SqlParameterValue> parameters = this.authorizedClientParametersMapper.apply(
					new OAuth2AuthorizedClientHolder(authorizedClient, principal));
			PreparedStatementSetter pss = new ArgumentPreparedStatementSetter(parameters.toArray());
			this.jdbcOperations.update(SAVE_AUTHORIZED_CLIENT_SQL, pss);
		}

		@Override
		public void removeAuthorizedClient(String clientRegistrationId, String principalName) {
			SqlParameterValue[] parameters = new SqlParameterValue[] {
					new SqlParameterValue(Types.VARCHAR, clientRegistrationId),
					new SqlParameterValue(Types.VARCHAR, principalName)
			};
			PreparedStatementSetter pss = new ArgumentPreparedStatementSetter(parameters);
			this.jdbcOperations.update(REMOVE_AUTHORIZED_CLIENT_SQL, pss);
		}

		private static class OAuth2AuthorizedClientRowMapper implements RowMapper<OAuth2AuthorizedClient> {
			private final ClientRegistrationRepository clientRegistrationRepository;

			private OAuth2AuthorizedClientRowMapper(ClientRegistrationRepository clientRegistrationRepository) {
				Assert.notNull(clientRegistrationRepository, "clientRegistrationRepository cannot be null");
				this.clientRegistrationRepository = clientRegistrationRepository;
			}

			@Override
			public OAuth2AuthorizedClient mapRow(ResultSet rs, int rowNum) throws SQLException {
				String clientRegistrationId = rs.getString("clientRegistrationId");
				ClientRegistration clientRegistration = this.clientRegistrationRepository.findByRegistrationId(
						clientRegistrationId);
				if (clientRegistration == null) {
					throw new DataRetrievalFailureException("The ClientRegistration with id '" +
							clientRegistrationId + "' exists in the data source, " +
							"however, it was not found in the ClientRegistrationRepository.");
				}

				OAuth2AccessToken.TokenType tokenType = null;
				if (OAuth2AccessToken.TokenType.BEARER.getValue().equalsIgnoreCase(
						rs.getString("accessTokenType"))) {
					tokenType = OAuth2AccessToken.TokenType.BEARER;
				}
				String tokenValue = new String(rs.getBytes("accessTokenValue"), StandardCharsets.UTF_8);
				Instant issuedAt = rs.getTimestamp("accessTokenIssuedAt").toInstant();
				Instant expiresAt = rs.getTimestamp("accessTokenExpiresAt").toInstant();
				Set<String> scopes = Collections.emptySet();
				String accessTokenScopes = rs.getString("accessTokenScopes");
				if (accessTokenScopes != null) {
					scopes = StringUtils.commaDelimitedListToSet(accessTokenScopes);
				}
				OAuth2AccessToken accessToken = new OAuth2AccessToken(
						tokenType, tokenValue, issuedAt, expiresAt, scopes);

				OAuth2RefreshToken refreshToken = null;
				byte[] refreshTokenValue = rs.getBytes("refreshTokenValue");
				if (refreshTokenValue != null) {
					tokenValue = new String(refreshTokenValue, StandardCharsets.UTF_8);
					issuedAt = null;
					Timestamp refreshTokenIssuedAt = rs.getTimestamp("refreshTokenIssuedAt");
					if (refreshTokenIssuedAt != null) {
						issuedAt = refreshTokenIssuedAt.toInstant();
					}
					refreshToken = new OAuth2RefreshToken(tokenValue, issuedAt);
				}

				String principalName = rs.getString("principalName");

				return new OAuth2AuthorizedClient(
						clientRegistration, principalName, accessToken, refreshToken);
			}
		}
	}
}
