package org.kohsuke.github;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;

import static org.kohsuke.github.Previews.INERTIA;

/**
 * @author Gunnar Skjold
 */
public class GHProjectColumn extends GHObject {
	protected GHProject project;

	private String name;
	private String project_url;

	@Override
	public URL getHtmlUrl() throws IOException {
		return null;
	}

	public GHProjectColumn wrap(GHProject project) {
		this.project = project;
		return this;
	}

	public GitHub getRoot() {
		return super.getRoot();
	}

	public GHProject getProject() throws IOException {
		if(project == null) {
			try {
				project = getRoot().retrieve().to(getProjectUrl().getPath(), GHProject.class);
			} catch (FileNotFoundException e) {
				return null;
			}
		}
		return project;
	}

	public String getName() {
		return name;
	}

	public URL getProjectUrl() {
		return GitHub.parseURL(project_url);
	}

	public void setName(String name) throws IOException {
		edit("name", name);
	}

	private void edit(String key, Object value) throws IOException {
		new Requester(getRoot()).withPreview(INERTIA)._with(key, value).method("PATCH").to(getApiRoute());
	}

	protected String getApiRoute() {
		return String.format("/projects/columns/%d", id);
	}

	public void delete() throws IOException {
		new Requester(getRoot()).withPreview(INERTIA).method("DELETE").to(getApiRoute());
	}

	public PagedIterable<GHProjectCard> listCards() throws IOException {
		final GHProjectColumn column = this;
		return getRoot().retrieve()
			.withPreview(INERTIA)
			.asPagedIterable(
				String.format("/projects/columns/%d/cards", id),
				GHProjectCard[].class,
				item -> item.wrap(column) );
	}

	public GHProjectCard createCard(String note) throws IOException {
		return getRoot().retrieve().method("POST")
				.withPreview(INERTIA)
				.with("note", note)
				.to(String.format("/projects/columns/%d/cards", id), GHProjectCard.class).wrap(this);
	}

	public GHProjectCard createCard(GHIssue issue) throws IOException {
		return getRoot().retrieve().method("POST")
				.withPreview(INERTIA)
				.with("content_type", issue instanceof GHPullRequest ? "PullRequest" : "Issue")
				.with("content_id", issue.getId())
				.to(String.format("/projects/columns/%d/cards", id), GHProjectCard.class).wrap(this);
	}
}
