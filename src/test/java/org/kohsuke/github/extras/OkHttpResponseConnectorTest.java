package org.kohsuke.github.extras;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.squareup.okhttp.CacheControl;
import okhttp3.Cache;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Test;
import org.kohsuke.github.AbstractGitHubWireMockTest;
import org.kohsuke.github.GHRateLimit;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.extras.okhttp3.OkHttpResponseConnector;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

/**
 * Test showing the behavior of OkHttpResponseConnector with and without cache.
 * <p>
 * Key take aways:
 *
 * <ul>
 * <li>These tests are artificial and intended to highlight the differences in behavior between scenarios. However, the
 * differences they indicate are stark.</li>
 * <li>Caching reduces rate limit consumption by at least a factor of two in even the simplest case.</li>
 * <li>The OkHttp cache is pretty smart and will often connect read and write requests made on the same client and
 * invalidate caches.</li>
 * <li>Changes made outside the current client cause the OkHttp cache to return stale data. This is expected and correct
 * behavior.</li>
 * <li>"max-age=0" addresses the problem of external changes by revalidating caches for each request. This produces the
 * same number of requests as OkHttp without caching, but those requests only count towards the GitHub rate limit if
 * data has changes.</li>
 * </ul>
 *
 * @author Liam Newman
 */
public class OkHttpResponseConnectorTest extends AbstractGitHubWireMockTest {

    public OkHttpResponseConnectorTest() {
        useDefaultGitHub = false;
    }

    private static int defaultRateLimitUsed = 17;
    private static int okhttpRateLimitUsed = 17;
    private static int maxAgeZeroRateLimitUsed = 7;
    private static int maxAgeThreeRateLimitUsed = 7;
    private static int maxAgeNoneRateLimitUsed = 4;

    private static int userRequestCount = 0;

    private static int defaultNetworkRequestCount = 16;
    private static int okhttpNetworkRequestCount = 16;
    private static int maxAgeZeroNetworkRequestCount = 16;
    private static int maxAgeThreeNetworkRequestCount = 9;
    private static int maxAgeNoneNetworkRequestCount = 5;

    private static int maxAgeZeroHitCount = 10;
    private static int maxAgeThreeHitCount = 10;
    private static int maxAgeNoneHitCount = 11;

    private GHRateLimit rateLimitBefore;

    @Override
    protected WireMockConfiguration getWireMockOptions() {
        return super.getWireMockOptions().extensions(templating.newResponseTransformer());
    }

    @Before
    public void setupRepo() throws Exception {
        if (mockGitHub.isUseProxy()) {
            GHRepository repo = getRepository(getNonRecordingGitHub());
            repo.setDescription("Resetting");

            // Let things settle a bit between tests when working against the live site
            Thread.sleep(5000);
            userRequestCount = 1;
        }
    }

    private OkHttpResponseConnector responseConnectorMaxAge(OkHttpClient client, int cacheMaxAge) {
        String maxAgeHeaderValue;
        if (cacheMaxAge >= 0 && client.cache() != null) {
            maxAgeHeaderValue = new CacheControl.Builder().maxAge(cacheMaxAge, TimeUnit.SECONDS).build().toString();
        } else {
            maxAgeHeaderValue = null;
        }
        return new OkHttpResponseConnector(client) {
            @Override
            protected Request.Builder newRequestBuilder() {
                Request.Builder builder = new Request.Builder();
                if (maxAgeHeaderValue != null) {
                    builder.header("Cache-Control", maxAgeHeaderValue);
                }
                return builder;
            }
        };
    }

    @Test
    public void DefaultConnector() throws Exception {

        this.gitHub = getGitHubBuilder().withEndpoint(mockGitHub.apiServer().baseUrl()).build();

        doTestActions();

        // Testing behavior after change
        // Uncached connection gets updated correctly but at cost of rate limit
        assertThat(getRepository(gitHub).getDescription(), is("Tricky"));

        checkRequestAndLimit(defaultNetworkRequestCount, defaultRateLimitUsed);
    }

    @Test
    public void OkHttpConnector_NoCache() throws Exception {

        OkHttpClient client = createClient(false);
        OkHttpResponseConnector connector = new OkHttpResponseConnector(client);

        this.gitHub = getGitHubBuilder().withEndpoint(mockGitHub.apiServer().baseUrl())
                .withConnector(connector)
                .build();

        doTestActions();

        // Testing behavior after change
        // Uncached okhttp connection gets updated correctly but at cost of rate limit
        assertThat(getRepository(gitHub).getDescription(), is("Tricky"));

        checkRequestAndLimit(okhttpNetworkRequestCount, okhttpRateLimitUsed);

        Cache cache = client.cache();
        assertThat("Cache", cache, is(nullValue()));
    }

    @Test
    public void OkHttpConnector_Cache_MaxAgeNone() throws Exception {
        // The responses were recorded from github, but the Date headers
        // have been templated to make caching behavior work as expected.
        // This is reasonable as long as the number of network requests matches up.
        snapshotNotAllowed();

        OkHttpClient client = createClient(true);
        OkHttpResponseConnector connector = responseConnectorMaxAge(client, -1);

        this.gitHub = getGitHubBuilder().withEndpoint(mockGitHub.apiServer().baseUrl())
                .withConnector(connector)
                .build();

        doTestActions();

        // Testing behavior after change
        // NOTE: this is wrong! The live data changed!
        // Due to max-age (default 60 from response) the cache returns the old data.
        assertThat(getRepository(gitHub).getDescription(), is(mockGitHub.getMethodName()));

        checkRequestAndLimit(maxAgeNoneNetworkRequestCount, maxAgeNoneRateLimitUsed);

        Cache cache = client.cache();

        // NOTE: this is actually bad.
        // This elevated hit count is the stale requests returning bad data took longer to detect a change.
        assertThat("getHitCount", cache.hitCount(), is(maxAgeNoneHitCount));
    }

    @Test
    public void OkHttpConnector_Cache_MaxAge_Three() throws Exception {

        // NOTE: This test is very timing sensitive.
        // It can be run locally to verify behavior but snapshot data is to touchy
        assumeFalse("Test only valid when not taking a snapshot", mockGitHub.isTakeSnapshot());
        assumeTrue("Test only valid when proxying (-Dtest.github.useProxy to enable)", mockGitHub.isUseProxy());

        OkHttpClient client = createClient(true);
        OkHttpResponseConnector connector = responseConnectorMaxAge(client, 3);

        this.gitHub = getGitHubBuilder().withEndpoint(mockGitHub.apiServer().baseUrl())
                .withConnector(connector)
                .build();

        doTestActions();

        // Due to max-age=3 this eventually checks the site and gets updated information. Yay?
        assertThat(getRepository(gitHub).getDescription(), is("Tricky"));

        checkRequestAndLimit(maxAgeThreeNetworkRequestCount, maxAgeThreeRateLimitUsed);

        Cache cache = client.cache();
        assertThat("getHitCount", cache.hitCount(), is(maxAgeThreeHitCount));
    }

    @Test
    public void OkHttpConnector_Cache_MaxAgeDefault_Zero() throws Exception {
        // The responses were recorded from github, but the Date headers
        // have been templated to make caching behavior work as expected.
        // This is reasonable as long as the number of network requests matches up.
        snapshotNotAllowed();

        OkHttpClient client = createClient(true);
        OkHttpResponseConnector connector = responseConnectorMaxAge(client, 0);

        this.gitHub = getGitHubBuilder().withEndpoint(mockGitHub.apiServer().baseUrl())
                .withConnector(connector)
                .build();

        doTestActions();

        // Testing behavior after change
        // NOTE: max-age=0 produces the same result at uncached without added rate-limit use.
        assertThat(getRepository(gitHub).getDescription(), is("Tricky"));

        checkRequestAndLimit(maxAgeZeroNetworkRequestCount, maxAgeZeroRateLimitUsed);

        Cache cache = client.cache();
        assertThat("getHitCount", cache.hitCount(), is(maxAgeZeroHitCount));
    }

    private void checkRequestAndLimit(int networkRequestCount, int rateLimitUsed) throws IOException {
        GHRateLimit rateLimitAfter = gitHub.rateLimit();
        assertThat("Request Count", mockGitHub.getRequestCount(), is(networkRequestCount + userRequestCount));

        // Rate limit must be under this value, but if it wiggles we don't care
        assertThat("Rate Limit Change",
                rateLimitBefore.remaining - rateLimitAfter.remaining,
                is(lessThanOrEqualTo(rateLimitUsed + userRequestCount)));

    }

    private OkHttpClient createClient(boolean useCache) throws IOException {
        OkHttpClient.Builder client = new OkHttpClient.Builder();

        if (useCache) {
            File cacheDir = new File("target/cache/" + baseFilesClassPath + "/" + mockGitHub.getMethodName());
            cacheDir.mkdirs();
            FileUtils.cleanDirectory(cacheDir);
            Cache cache = new Cache(cacheDir, 100 * 1024L * 1024L);

            client.cache(cache);
        }

        return client.build();
    }

    /**
     * This is a standard set of actions to be performed with each connector
     *
     * @throws Exception
     */
    private void doTestActions() throws Exception {
        rateLimitBefore = gitHub.getRateLimit();

        String name = mockGitHub.getMethodName();

        GHRepository repo = getRepository(gitHub);

        // Testing behavior when nothing has changed.
        pollForChange("Resetting");
        assertThat(getRepository(gitHub).getDescription(), is("Resetting"));

        repo.setDescription(name);

        pollForChange(name);

        // Test behavior after change
        assertThat(getRepository(gitHub).getDescription(), is(name));

        // Get Tricky - make a change via a different client
        if (mockGitHub.isUseProxy()) {
            GHRepository altRepo = getRepository(getNonRecordingGitHub());
            altRepo.setDescription("Tricky");
        }

        // Testing behavior after change
        pollForChange("Tricky");
    }

    private void pollForChange(String name) throws IOException, InterruptedException {
        getRepository(gitHub).getDescription();
        Thread.sleep(500);
        getRepository(gitHub).getDescription();
        // This is only interesting when running the max-age=3 test which currently only runs with proxy
        // Disabled to speed up the tests
        if (mockGitHub.isUseProxy()) {
            Thread.sleep(1000);
        }
        getRepository(gitHub).getDescription();
        // This is only interesting when running the max-age=3 test which currently only runs with proxy
        // Disabled to speed up the tests
        if (mockGitHub.isUseProxy()) {
            Thread.sleep(4000);
        }
    }

    private static GHRepository getRepository(GitHub gitHub) throws IOException {
        return gitHub.getOrganization("hub4j-test-org").getRepository("github-api");
    }

}
