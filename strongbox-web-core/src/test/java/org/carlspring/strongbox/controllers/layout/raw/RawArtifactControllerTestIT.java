package org.carlspring.strongbox.controllers.layout.raw;

import org.carlspring.strongbox.config.IntegrationTest;
import org.carlspring.strongbox.providers.layout.RawLayoutProvider;
import org.carlspring.strongbox.rest.common.RawRestAssuredBaseTest;
import org.carlspring.strongbox.storage.repository.Repository;
import org.carlspring.strongbox.testing.storage.repository.RepositoryManagementTestExecutionListener;
import org.carlspring.strongbox.testing.storage.repository.TestRepository;
import org.carlspring.strongbox.testing.storage.repository.TestRepository.Group;
import org.carlspring.strongbox.testing.storage.repository.TestRepository.Remote;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

/**
 * @author Martin Todorov
 * @author Pablo Tirado
 */
@IntegrationTest
@Execution(CONCURRENT)
public class RawArtifactControllerTestIT
        extends RawRestAssuredBaseTest
{

    private static final String REPOSITORY_RELEASES = "ractit-raw-releases";

    private static final String REPOSITORY_PROXY = "ractit-raw-proxy";

    private static final String REPOSITORY_GROUP = "ractit-raw-group";

    private static final String REMOTE_URL = "http://slackbuilds.org/slackbuilds/14.2/";

    @Override
    @BeforeEach
    public void init()
            throws Exception
    {
        super.init();
    }

    /**
     * Note: This test requires an internet connection.
     *
     * @throws Exception
     */
    @ExtendWith(RepositoryManagementTestExecutionListener.class)
    @Test
    public void testResolveArtifactViaProxy(@TestRepository(layout = RawLayoutProvider.ALIAS,
                                                            repositoryId = REPOSITORY_RELEASES)
                                            Repository releasesRepository,
                                            @Remote (url = REMOTE_URL
    )                                       @TestRepository(layout = RawLayoutProvider.ALIAS,
                                                            repositoryId = REPOSITORY_PROXY)
                                            Repository proxyRepository)
            throws Exception
    {
        final String storageId = proxyRepository.getStorage().getId();
        final String repositoryId = proxyRepository.getId();
        final String path = "system/alien.tar.gz";

        String artifactPath = "/storages/" + storageId + "/" + repositoryId + "/" + path;

        resolveArtifact(artifactPath);
    }

    /**
     * Note: This test requires an internet connection.
     *
     * @throws Exception
     */
    @ExtendWith(RepositoryManagementTestExecutionListener.class)
    @Test
    public void testResolveArtifactViaGroupWithProxy(@TestRepository(layout = RawLayoutProvider.ALIAS,
                                                                     repositoryId = REPOSITORY_RELEASES)
                                                     Repository releasesRepository,
                                                     @Remote (url = REMOTE_URL
    )                                                @TestRepository(layout = RawLayoutProvider.ALIAS,
                                                                     repositoryId = REPOSITORY_PROXY)
                                                     Repository proxyRepository,
                                                     @Group(repositories = REPOSITORY_PROXY)
                                                     @TestRepository(layout = RawLayoutProvider.ALIAS,
                                                                     repositoryId = REPOSITORY_GROUP)
                                                     Repository groupRepository)
            throws Exception
    {
        final String storageId = groupRepository.getStorage().getId();
        final String repositoryGroupId = groupRepository.getId();
        final String path = "system/alien.tar.gz";

        String artifactPath = "/storages/" + storageId + "/" + repositoryGroupId + "/" + path;

        resolveArtifact(artifactPath);
    }

}
