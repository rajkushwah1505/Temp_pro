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

import javax.annotation.CheckForNull;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import static org.kohsuke.github.Previews.*;

/**
 * A pull request.
 * 
 * @author Kohsuke Kawaguchi
 * @see GHRepository#getPullRequest(int)
 */
@SuppressWarnings({"UnusedDeclaration"})
public class GHPullRequest extends GHIssue {

    private String patch_url, diff_url, issue_url;
    private GHCommitPointer base;
    private String merged_at;
    private GHCommitPointer head;

    // details that are only available when obtained from ID
    private GHUser merged_by;
    private int review_comments, additions, commits;
    private boolean merged, maintainer_can_modify;
    private Boolean mergeable;
    private int deletions;
    private String mergeable_state;
    private int changed_files;
    private String merge_commit_sha;

    /**
     * GitHub doesn't return some properties of {@link GHIssue} when requesting the GET on the 'pulls' API
     * route as opposed to 'issues' API route. This flag remembers whether we made the GET call on the 'issues' route
     * on this object to fill in those missing details
     */
    private transient boolean fetchedIssueDetails;


    GHPullRequest wrapUp(GHRepository owner) {
        this.wrap(owner);
        return wrapUp(owner.root);
    }

    GHPullRequest wrapUp(GitHub root) {
        if (owner != null) owner.wrap(root);
        if (base != null) base.wrapUp(root);
        if (head != null) head.wrapUp(root);
        if (merged_by != null) merged_by.wrapUp(root);
        return this;
    }

    @Override
    protected String getApiRoute() {
        return "/repos/"+owner.getOwnerName()+"/"+owner.getName()+"/pulls/"+number;
    }

    /**
     * The URL of the patch file.
     * like https://github.com/jenkinsci/jenkins/pull/100.patch
     */
    public URL getPatchUrl() {
        return GitHub.parseURL(patch_url);
    }
    
    /**
     * The URL of the patch file.
     * like https://github.com/jenkinsci/jenkins/pull/100.patch
     */
    public URL getIssueUrl() {
        return GitHub.parseURL(issue_url);
    }

    /**
     * This points to where the change should be pulled into,
     * but I'm not really sure what exactly it means.
     */
    public GHCommitPointer getBase() {
        return base;
    }

    /**
     * The change that should be pulled. The tip of the commits to merge.
     */
    public GHCommitPointer getHead() {
        return head;
    }
    
    @Deprecated
    public Date getIssueUpdatedAt() throws IOException {
        return super.getUpdatedAt();
    }

    /**
     * The diff file,
     * like https://github.com/jenkinsci/jenkins/pull/100.diff
     */
    public URL getDiffUrl() {
        return GitHub.parseURL(diff_url);
    }

    public Date getMergedAt() {
        return GitHub.parseDate(merged_at);
    }

    @Override
    public Collection<GHLabel> getLabels() throws IOException {
        fetchIssue();
        return super.getLabels();
    }

    @Override
    public GHUser getClosedBy() {
        return null;
    }

    @Override
    public PullRequest getPullRequest() {
        return null;
    }

    //
    // details that are only available via get with ID
    //

    public GHUser getMergedBy() throws IOException {
        populate();
        return merged_by;
    }

    public int getReviewComments() throws IOException {
        populate();
        return review_comments;
    }

    public int getAdditions() throws IOException {
        populate();
        return additions;
    }

    public int getCommits() throws IOException {
        populate();
        return commits;
    }

    public boolean isMerged() throws IOException {
        populate();
        return merged;
    }

    public boolean canMaintainerModify() throws IOException {
        populate();
        return maintainer_can_modify;
    }

    public Boolean getMergeable() throws IOException {
        populate();
        return mergeable;
    }

    public int getDeletions() throws IOException {
        populate();
        return deletions;
    }

    public String getMergeableState() throws IOException {
        populate();
        return mergeable_state;
    }

    public int getChangedFiles() throws IOException {
        populate();
        return changed_files;
    }

    /**
     * See <a href="https://developer.github.com/changes/2013-04-25-deprecating-merge-commit-sha">GitHub blog post</a>
     */
    public String getMergeCommitSha() throws IOException {
        populate();
        return merge_commit_sha;
    }

    /**
     * Fully populate the data by retrieving missing data.
     *
     * Depending on the original API call where this object is created, it may not contain everything.
     */
    private void populate() throws IOException {
        if (mergeable_state!=null)    return; // already populated
        if (root.isOffline()) {
            return; // cannot populate, will have to live with what we have
        }
        root.retrieve().to(url, this).wrapUp(owner);
    }

    /**
     * Retrieves all the files associated to this pull request.
     */
    public PagedIterable<GHPullRequestFileDetail> listFiles() {
        return new PagedIterable<GHPullRequestFileDetail>() {
            public PagedIterator<GHPullRequestFileDetail> _iterator(int pageSize) {
                return new PagedIterator<GHPullRequestFileDetail>(root.retrieve().asIterator(String.format("%s/files", getApiRoute()),
                        GHPullRequestFileDetail[].class, pageSize)) {
                    @Override
                    protected void wrapUp(GHPullRequestFileDetail[] page) {
                    }
                };
            }
        };
    }

    /**
     * Retrieves all the reviews associated to this pull request.
     */
    public PagedIterable<GHPullRequestReview> listReviews() {
        return new PagedIterable<GHPullRequestReview>() {
            public PagedIterator<GHPullRequestReview> _iterator(int pageSize) {
                return new PagedIterator<GHPullRequestReview>(root.retrieve()
                        .withPreview(BLACK_CAT)
                        .asIterator(String.format("%s/reviews", getApiRoute()),
                        GHPullRequestReview[].class, pageSize)) {
                    @Override
                    protected void wrapUp(GHPullRequestReview[] page) {
                        for (GHPullRequestReview r: page) {
                            r.wrapUp(GHPullRequest.this);
                        }
                    }
                };
            }
        };
    }

    /**
     * Obtains all the review comments associated with this pull request.
     */
    public PagedIterable<GHPullRequestReviewComment> listReviewComments() throws IOException {
        return new PagedIterable<GHPullRequestReviewComment>() {
            public PagedIterator<GHPullRequestReviewComment> _iterator(int pageSize) {
                return new PagedIterator<GHPullRequestReviewComment>(root.retrieve().asIterator(getApiRoute() + "/comments",
                        GHPullRequestReviewComment[].class, pageSize)) {
                    protected void wrapUp(GHPullRequestReviewComment[] page) {
                        for (GHPullRequestReviewComment c : page)
                            c.wrapUp(GHPullRequest.this);
                    }
                };
            }
        };
    }

    /**
     * Retrieves all the commits associated to this pull request.
     */
    public PagedIterable<GHPullRequestCommitDetail> listCommits() {
        return new PagedIterable<GHPullRequestCommitDetail>() {
            public PagedIterator<GHPullRequestCommitDetail> _iterator(int pageSize) {
                return new PagedIterator<GHPullRequestCommitDetail>(root.retrieve().asIterator(
                        String.format("%s/commits", getApiRoute()),
                        GHPullRequestCommitDetail[].class, pageSize)) {
                    @Override
                    protected void wrapUp(GHPullRequestCommitDetail[] page) {
                        for (GHPullRequestCommitDetail c : page)
                            c.wrapUp(GHPullRequest.this);
                    }
                };
            }
        };
    }

    @Preview
    @Deprecated
    public GHPullRequestReview createReview(String body, @CheckForNull GHPullRequestReviewState event,
                                            GHPullRequestReviewComment... comments)
            throws IOException {
        return createReview(body, event, Arrays.asList(comments));
    }

    @Preview
    @Deprecated
    public GHPullRequestReview createReview(String body, @CheckForNull GHPullRequestReviewState event,
                                            List<GHPullRequestReviewComment> comments)
            throws IOException {
//        if (event == null) {
//            event = GHPullRequestReviewState.PENDING;
//        }
        List<DraftReviewComment> draftComments = new ArrayList<DraftReviewComment>(comments.size());
        for (GHPullRequestReviewComment c : comments) {
            draftComments.add(new DraftReviewComment(c.getBody(), c.getPath(), c.getPosition()));
        }
        return new Requester(root).method("POST")
                .with("body", body)
                //.with("event", event.name())
                ._with("comments", draftComments)
                .withPreview(BLACK_CAT)
                .to(getApiRoute() + "/reviews", GHPullRequestReview.class).wrapUp(this);
    }

    public GHPullRequestReviewComment createReviewComment(String body, String sha, String path, int position) throws IOException {
        return new Requester(root).method("POST")
                .with("body", body)
                .with("commit_id", sha)
                .with("path", path)
                .with("position", position)
                .to(getApiRoute() + "/comments", GHPullRequestReviewComment.class).wrapUp(this);
    }

    /**
     * Merge this pull request.
     *
     * The equivalent of the big green "Merge pull request" button.
     *
     * @param msg
     *      Commit message. If null, the default one will be used.
     */
    public void merge(String msg) throws IOException {
        merge(msg,null);
    }

    /**
     * Merge this pull request.
     *
     * The equivalent of the big green "Merge pull request" button.
     *
     * @param msg
     *      Commit message. If null, the default one will be used.
     * @param sha
     *      SHA that pull request head must match to allow merge.
     */
    public void merge(String msg, String sha) throws IOException {
        merge(msg, sha, null);
    }

    /**
     * Merge this pull request, using the specified merge method.
     *
     * The equivalent of the big green "Merge pull request" button.
     *
     * @param msg
     *      Commit message. If null, the default one will be used.
     * @param method
     *      SHA that pull request head must match to allow merge.
     */
    public void merge(String msg, String sha, MergeMethod method) throws IOException {
        new Requester(root).method("PUT")
                .with("commit_message",msg)
                .with("sha",sha)
                .with("merge_method",method)
                .to(getApiRoute()+"/merge");
    }

    public enum MergeMethod{ MERGE, SQUASH, REBASE }

    private void fetchIssue() throws IOException {
        if (!fetchedIssueDetails) {
            new Requester(root).to(getIssuesApiRoute(), this);
            fetchedIssueDetails = true;
        }
    }

    private static class DraftReviewComment {
        private String body;
        private String path;
        private int position;

        public DraftReviewComment(String body, String path, int position) {
            this.body = body;
            this.path = path;
            this.position = position;
        }

        public String getBody() {
            return body;
        }

        public String getPath() {
            return path;
        }

        public int getPosition() {
            return position;
        }
    }
}
