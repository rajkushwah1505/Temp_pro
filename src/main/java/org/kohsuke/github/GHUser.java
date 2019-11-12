/*
 * The MIT License
 *
 * Copyright (c) 2010, Kohsuke Kawaguchi
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.kohsuke.github;

import com.infradna.tool.bridge_method_injector.WithBridgeMethods;

import java.io.IOException;
import java.util.*;

/**
 * Represents an user of GitHub.
 *
 * @author Kohsuke Kawaguchi
 */
public class GHUser extends GHPerson {

    public List<GHKey> getKeys() throws IOException {
        return Collections.unmodifiableList(Arrays.asList(getRoot().createRequester().method("GET").to(getApiTailUrl("keys"), GHKey[].class)));
    }

    /**
     * Follow this user.
     */
    public void follow() throws IOException {
        getRoot().createRequester().method("PUT").to("/user/following/" + login);
    }

    /**
     * Unfollow this user.
     */
    public void unfollow() throws IOException {
        getRoot().createRequester().method("DELETE").to("/user/following/" + login);
    }

    /**
     * Lists the users that this user is following
     */
    @WithBridgeMethods(Set.class)
    public GHPersonSet<GHUser> getFollows() throws IOException {
        return new GHPersonSet<GHUser>(listFollows().asList());
    }

    /**
     * Lists the users that this user is following
     */
    public PagedIterable<GHUser> listFollows() {
        return listUser("following");
    }

    /**
     * Lists the users who are following this user.
     */
    @WithBridgeMethods(Set.class)
    public GHPersonSet<GHUser> getFollowers() throws IOException {
        return new GHPersonSet<GHUser>(listFollowers().asList());
    }

    /**
     * Lists the users who are following this user.
     */
    public PagedIterable<GHUser> listFollowers() {
        return listUser("followers");
    }

    private PagedIterable<GHUser> listUser(final String suffix) {
        return getRoot().createRequester().method("GET")
            .asPagedIterable(
                getApiTailUrl(suffix),
                GHUser[].class,
                item -> item.wrapUp(getRoot()) );
    }

    /**
     * Lists all the subscribed (aka watched) repositories.
     *
     * https://developer.github.com/v3/activity/watching/
     */
    public PagedIterable<GHRepository> listSubscriptions() {
        return listRepositories("subscriptions");
    }

    /**
     * Lists all the repositories that this user has starred.
     */
    public PagedIterable<GHRepository> listStarredRepositories() {
        return listRepositories("starred");
    }

    private PagedIterable<GHRepository> listRepositories(final String suffix) {
        return getRoot().createRequester().method("GET")
            .asPagedIterable(
                getApiTailUrl(suffix),
                GHRepository[].class,
                item -> item.wrap(getRoot()) );
    }

    /**
     * Returns true if this user belongs to the specified organization.
     */
    public boolean isMemberOf(GHOrganization org) {
        return org.hasMember(this);
    }

    /**
     * Returns true if this user belongs to the specified team.
     */
    public boolean isMemberOf(GHTeam team) {
        return team.hasMember(this);
    }

    /**
     * Returns true if this user belongs to the specified organization as a public member.
     */
    public boolean isPublicMemberOf(GHOrganization org) {
        return org.hasPublicMember(this);
    }

    /*package*/ static GHUser[] wrap(GHUser[] users, GitHub root) {
        for (GHUser f : users)
            f.setRoot(root);
        return users;
    }

    /**
     * Gets the organization that this user belongs to publicly.
     */
    @WithBridgeMethods(Set.class)
    public GHPersonSet<GHOrganization> getOrganizations() throws IOException {
        GHPersonSet<GHOrganization> orgs = new GHPersonSet<GHOrganization>();
        Set<String> names = new HashSet<String>();
        for (GHOrganization o : getRoot().createRequester().method("GET").to("/users/" + login + "/orgs", GHOrganization[].class)) {
            if (names.add(o.getLogin()))    // I've seen some duplicates in the data
                orgs.add(getRoot().getOrganization(o.getLogin()));
        }
        return orgs;
    }

    /**
     * Lists events performed by a user (this includes private events if the caller is authenticated.
     */
    public PagedIterable<GHEventInfo> listEvents() throws IOException {
        return getRoot().createRequester().method("GET")
            .asPagedIterable(
                String.format("/users/%s/events", login),
                GHEventInfo[].class,
                item -> item.wrapUp(getRoot()) );
    }

    /**
     * Lists Gists created by this user.
     */
    public PagedIterable<GHGist> listGists() throws IOException {
        return getRoot().createRequester().method("GET")
            .asPagedIterable(
                String.format("/users/%s/gists", login),
                GHGist[].class,
                item -> item.wrapUp(GHUser.this) );
    }

    @Override
    public int hashCode() {
        return login.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof GHUser) {
            GHUser that = (GHUser) obj;
            return this.login.equals(that.login);
        }
        return false;
    }

    String getApiTailUrl(String tail) {
        if (tail.length()>0 && !tail.startsWith("/"))    tail='/'+tail;
        return "/users/" + login + tail;
    }

    /*package*/ GHUser wrapUp(GitHub root) {
        super.wrapUp(root);
        return this;
    }
}
