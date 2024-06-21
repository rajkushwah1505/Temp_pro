package org.kohsuke.github;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import org.junit.Test;

public class GHRepositoryTrafficTopReferralPathTest {
    @Test
    public void test() {
        GHRepositoryTrafficTopReferralPath testee = new GHRepositoryTrafficTopReferralPath(1, 2, "path", "title");
        assertThat(testee.getCount(), is(equalTo(1)));
        assertThat(testee.getUniques(), is(equalTo(2)));
        assertThat(testee.getPath(), is(equalTo("path")));
        assertThat(testee.getTitle(), is(equalTo("title")));
    }
}
