/*
 * Copyright 2012-2016 the original author or authors.
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
package org.springframework.security.access.hierarchicalroles;

import org.junit.Test;

import java.util.*;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link RoleHierarchyUtils}.
 *
 * @author Joe Grandja
 */
public class RoleHierarchyUtilsTests {
	private static final String EOL = System.lineSeparator();

	@Test
	public void roleHierarchyFromMapWhenMapValidThenConvertsCorrectly() {
		// @formatter:off
		String expectedRoleHierarchy = "ROLE_A > ROLE_B" + EOL +
				"ROLE_A > ROLE_C" + EOL +
				"ROLE_B > ROLE_D" + EOL +
				"ROLE_C > ROLE_D" + EOL;
		// @formatter:on

		Map<String, List<String>> roleHierarchyMap = new TreeMap<>();
		roleHierarchyMap.put("ROLE_A", asList("ROLE_B", "ROLE_C"));
		roleHierarchyMap.put("ROLE_B", asList("ROLE_D"));
		roleHierarchyMap.put("ROLE_C", asList("ROLE_D"));

		String roleHierarchy = RoleHierarchyUtils.roleHierarchyFromMap(roleHierarchyMap);

		assertThat(roleHierarchy).isEqualTo(expectedRoleHierarchy);
	}

	@Test(expected = IllegalArgumentException.class)
	public void roleHierarchyFromMapWhenMapNullThenThrowsIllegalArgumentException() {
		RoleHierarchyUtils.roleHierarchyFromMap(null);
	}

	@Test(expected = IllegalArgumentException.class)
	public void roleHierarchyFromMapWhenMapEmptyThenThrowsIllegalArgumentException() {
		RoleHierarchyUtils.roleHierarchyFromMap(Collections.<String, List<String>>emptyMap());
	}

	@Test(expected = IllegalArgumentException.class)
	public void roleHierarchyFromMapWhenRoleNullThenThrowsIllegalArgumentException() {
		Map<String, List<String>> roleHierarchyMap = new HashMap<>();
		roleHierarchyMap.put(null, asList("ROLE_B", "ROLE_C"));

		RoleHierarchyUtils.roleHierarchyFromMap(roleHierarchyMap);
	}

	@Test(expected = IllegalArgumentException.class)
	public void roleHierarchyFromMapWhenRoleEmptyThenThrowsIllegalArgumentException() {
		Map<String, List<String>> roleHierarchyMap = new HashMap<>();
		roleHierarchyMap.put("", asList("ROLE_B", "ROLE_C"));

		RoleHierarchyUtils.roleHierarchyFromMap(roleHierarchyMap);
	}

	@Test(expected = IllegalArgumentException.class)
	public void roleHierarchyFromMapWhenImpliedRolesNullThenThrowsIllegalArgumentException() {
		Map<String, List<String>> roleHierarchyMap = new HashMap<>();
		roleHierarchyMap.put("ROLE_A", null);

		RoleHierarchyUtils.roleHierarchyFromMap(roleHierarchyMap);
	}

	@Test(expected = IllegalArgumentException.class)
	public void roleHierarchyFromMapWhenImpliedRolesEmptyThenThrowsIllegalArgumentException() {
		Map<String, List<String>> roleHierarchyMap = new HashMap<>();
		roleHierarchyMap.put("ROLE_A", Collections.<String>emptyList());

		RoleHierarchyUtils.roleHierarchyFromMap(roleHierarchyMap);
	}
}
