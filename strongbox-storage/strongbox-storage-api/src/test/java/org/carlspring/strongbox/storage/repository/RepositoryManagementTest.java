package org.carlspring.strongbox.storage.repository;

import org.carlspring.strongbox.StorageApiTestConfig;
import org.carlspring.strongbox.artifact.generator.NullArtifactGenerator;
import org.carlspring.strongbox.booters.PropertiesBooter;
import org.carlspring.strongbox.configuration.ConfigurationManager;
import org.carlspring.strongbox.data.CacheManagerTestExecutionListener;
import org.carlspring.strongbox.providers.io.RepositoryPathResolver;
import org.carlspring.strongbox.providers.io.RootRepositoryPath;
import org.carlspring.strongbox.testing.artifact.ArtifactManagementTestExecutionListener;
import org.carlspring.strongbox.testing.artifact.TestArtifact;
import org.carlspring.strongbox.testing.repository.NullRepository;
import org.carlspring.strongbox.testing.storage.repository.RepositoryManagementTestExecutionListener;
import org.carlspring.strongbox.testing.storage.repository.TestRepository;
import org.carlspring.strongbox.testing.storage.repository.TestRepositoryManagementApplicationContext;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.codec.digest.MessageDigestAlgorithms;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestExecutionListeners;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles(profiles = "test")
@ContextConfiguration(classes = { StorageApiTestConfig.class })
@TestExecutionListeners(listeners = { CacheManagerTestExecutionListener.class },
                        mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
public class RepositoryManagementTest
{

    private static Set<Repository> resolvedRepositoryInstances = ConcurrentHashMap.newKeySet();
    private static Set<byte[]> resolvedArtifactChecksums = ConcurrentHashMap.newKeySet();

    @Inject
    private RepositoryPathResolver repositoryPathResolver;

    @Inject
    protected ConfigurationManager configurationManager;

    @Inject
    protected PropertiesBooter propertiesBooter;

    @AfterEach
    public void checkRepositoryContextCleanup()
    {
        assertNull(TestRepositoryManagementApplicationContext.getInstance());
    }

    @ExtendWith({ RepositoryManagementTestExecutionListener.class, ArtifactManagementTestExecutionListener.class })
    @Test
    public void testParametersShouldBeInjected(@NullRepository(repositoryId = "rmt1") Repository r1,
                                               @NullRepository(repositoryId = "rmt2") Repository r2,
                                               @TestArtifact(resource = "artifact1.ext", generator = NullArtifactGenerator.class) Path standaloneArtifact,
                                               @TestArtifact(repositoryId = "rmt2", resource = "org/carlspring/test/artifact2.ext", generator = NullArtifactGenerator.class) Path repositoryArtifact,
                                               TestInfo testInfo)
    {
        assertNotNull(testInfo);
        parametersShouldBeCorrectlyResolvedAndUnique(r1, r2);
    }

    @ExtendWith({ RepositoryManagementTestExecutionListener.class, ArtifactManagementTestExecutionListener.class })
    @Test
    public void testGroupRepository(@NullRepository(repositoryId = "rmt1") Repository r1,
                                    @NullRepository(repositoryId = "rmt2") Repository r2,
                                    @TestRepository.Group(repositories = { "rmt1",
                                                                           "rmt2" })
                                    @NullRepository(repositoryId = "rmtg")
                                    Repository group)
    {
        parametersShouldBeCorrectlyResolvedAndUnique(r1, r2, group);
    }
    
    @ExtendWith({ RepositoryManagementTestExecutionListener.class, ArtifactManagementTestExecutionListener.class })
    @RepeatedTest(10)
    public void testConcurrentRepositoryDirect(@NullRepository(repositoryId = "rmt1") Repository r1,
                                               @NullRepository(repositoryId = "rmt2") Repository r2,
                                               @TestArtifact(resource = "artifact1.ext", generator = NullArtifactGenerator.class) Path standaloneArtifact,
                                               @TestArtifact(repositoryId = "rmt2", resource = "org/carlspring/test/artifact2.ext", generator = NullArtifactGenerator.class) Path repositoryArtifact)
        throws IOException
    {
        artifactShouldBeCorrectlyResolvedAndUnique(standaloneArtifact);
        artifactShouldBeCorrectlyResolvedAndUnique(repositoryArtifact);
        parametersShouldBeCorrectlyResolvedAndUnique(r1, r2);
    }

    @ExtendWith(RepositoryManagementTestExecutionListener.class)
    @RepeatedTest(10)
    public void testConcurrentRepositoryReverse(@NullRepository(repositoryId = "rmt2") Repository r2,
                                                @NullRepository(repositoryId = "rmt1") Repository r1)
    {
        parametersShouldBeCorrectlyResolvedAndUnique(r1, r2);
    }

    private void artifactShouldBeCorrectlyResolvedAndUnique(Path artifact)
        throws IOException
    {
        assertTrue(Files.exists(artifact));
        assertEquals(1024L, Files.size(artifact));

        String fileName = artifact.getFileName().toString();
        String checksumFileName = fileName + "." + MessageDigestAlgorithms.MD5.toLowerCase();
        Path artifactChecksum = artifact.resolveSibling(checksumFileName);

        assertTrue(Files.exists(artifactChecksum));
        assertTrue(resolvedArtifactChecksums.add(Files.readAllBytes(artifactChecksum)));
    }

    private void parametersShouldBeCorrectlyResolvedAndUnique(Repository...repositories)
    {
        for (Repository repository : repositories)
        {
            // Check that @TestRepository resolved
            assertNotNull(repository);
            // Check that repositories correctly resolved
            assertNotNull(configurationManager.getRepository(repository.getStorage().getId(), repository.getId()));

            // Check that paths created
            RootRepositoryPath path = repositoryPathResolver.resolve(repository);
            assertTrue(Files.exists(path));

            assertTrue(resolvedRepositoryInstances.add(repository));
        }
    }

}
