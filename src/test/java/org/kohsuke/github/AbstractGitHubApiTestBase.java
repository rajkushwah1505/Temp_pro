/*
 * GitHub API for Java
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.kohsuke.github;

import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.kohsuke.randname.RandomNameGenerator;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

import static java.util.Collections.unmodifiableMap;

/**
 * @author Kohsuke Kawaguchi
 */
public abstract class AbstractGitHubApiTestBase extends Assert {

    static final class Property {
        private final String key;
        private final String defaultValue;

        Property(String key, String defaultValue) {
            this.key = checkNotNull(key);
            this.defaultValue = checkNotNull(defaultValue);
        }

        private static <T> T checkNotNull(@Nullable T key) {
            if (key == null) {
                throw new NullPointerException();
            }
            return key;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Property property = (Property) o;
            return key.equals(property.key);
        }

        @Override
        public int hashCode() {
            return key.hashCode();
        }
    }

    protected GitHub gitHub;

    private final Set<Property> properties;
    private Map<Property, String> propertyValues = Collections.emptyMap();

    public AbstractGitHubApiTestBase(Property... properties) {
        this.properties = Collections.unmodifiableSet(new HashSet<Property>(Arrays.asList(properties)));
    }

    @Before
    public void setUp() throws Exception {
        File f = new File(System.getProperty("user.home"), ".github.kohsuke2");
        if (f.exists()) {
            Properties props = new Properties();
            FileInputStream in = null;
            try {
                in = new FileInputStream(f);
                props.load(in);
            } finally {
                IOUtils.closeQuietly(in);
            }
            // use the non-standard credential preferentially, so that developers of this library do not have
            // to clutter their event stream.
            this.gitHub = GitHubBuilder.fromProperties(props).withRateLimitHandler(RateLimitHandler.FAIL).build();
        } else {
            this.gitHub = GitHubBuilder.fromCredentials().withRateLimitHandler(RateLimitHandler.FAIL).build();
        }
        this.propertyValues = unmodifiableMap(readPropertyValues());
    }

    private Map<Property, String> readPropertyValues() {
        Map<Property, String> res = new HashMap<Property, String>();
        for (Property property : properties) {
            res.put(property, System.getProperty(property.key, property.defaultValue));
        }
        return res;
    }

    /**
     * @throws IllegalArgumentException if {@code property} hasn't been registered at startup
     */
    String getValue(Property property) {
        String res = propertyValues.get(property);
        if (res == null) {
            return property.defaultValue;
        }
        return res;
    }

    protected GHUser getUser() {
        try {
            return gitHub.getMyself();
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    protected void kohsuke() {
        String login = getUser().getLogin();
        Assume.assumeTrue(login.equals("kohsuke") || login.equals("kohsuke2"));
    }

    protected static final RandomNameGenerator rnd = new RandomNameGenerator();
}
