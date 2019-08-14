package org.carlspring.strongbox.controllers;

import org.carlspring.strongbox.config.IntegrationTest;
import org.carlspring.strongbox.providers.io.RepositoryFiles;
import org.carlspring.strongbox.providers.io.RepositoryPath;
import org.carlspring.strongbox.providers.io.RootRepositoryPath;
import org.carlspring.strongbox.rest.common.MavenRestAssuredBaseTest;
import org.carlspring.strongbox.storage.repository.Repository;
import org.carlspring.strongbox.testing.artifact.ArtifactManagementTestExecutionListener;
import org.carlspring.strongbox.testing.artifact.MavenTestArtifact;
import org.carlspring.strongbox.testing.repository.MavenRepository;
import org.carlspring.strongbox.testing.storage.repository.RepositoryAttributes;
import org.carlspring.strongbox.testing.storage.repository.RepositoryManagementTestExecutionListener;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import io.restassured.module.mockmvc.response.ValidatableMockMvcResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import static io.restassured.module.mockmvc.RestAssuredMockMvc.given;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

/**
 * @author Martin Todorov
 * @author Alex Oreshkevich
 * @author Pablo Tirado
 */
@IntegrationTest
@Execution(CONCURRENT)
public class TrashControllerTest
        extends MavenRestAssuredBaseTest
{
    private static final String REPOSITORY_WITH_TRASH_1 = "tct-releases-with-trash-1";

    private static final String REPOSITORY_WITH_TRASH_2 = "tct-releases-with-trash-2";

    private static final String REPOSITORY_WITH_TRASH_3 = "tct-releases-with-trash-3";

    private static final String REPOSITORY_WITH_FORCE_DELETE_1 = "tct-releases-with-force-delete-1";

    private static final String REPOSITORY_WITH_FORCE_DELETE_2 = "tct-releases-with-force-delete-2";

    @Override
    @BeforeEach
    public void init()
            throws Exception
    {
        super.init();

        setContextBaseUrl("/api/trash");
    }

    @ExtendWith({ RepositoryManagementTestExecutionListener.class,
                  ArtifactManagementTestExecutionListener.class })
    @Test
    public void testForceDeleteArtifactNotAllowed(@MavenRepository(repositoryId = REPOSITORY_WITH_TRASH_1)
                                                  @RepositoryAttributes(trashEnabled = true)
                                                  Repository repository,
                                                  @MavenTestArtifact(repositoryId = REPOSITORY_WITH_TRASH_1,
                                                                     id = "org.carlspring.strongbox:test-artifact-to-trash",
                                                                     versions = "1.0")
                                                  Path artifactPath)
            throws IOException
    {
        final String storageId = repository.getStorage().getId();
        final String repositoryId = repository.getId();
        final RootRepositoryPath repositoryPath = repositoryPathResolver.resolve(repository);

        final String artifactPathStr = repositoryPath.relativize(artifactPath).toString();
        final RepositoryPath artifactRepositoryPath = repositoryPathResolver.resolve(repository, artifactPathStr);

        // Delete the artifact (this one should get placed under the .trash)
        client.delete(storageId, repositoryId, artifactPathStr, false);

        final Path artifactFile = RepositoryFiles.trash(artifactRepositoryPath);

        logger.debug("Artifact file: {}", artifactFile.toAbsolutePath());

        assertTrue(Files.exists(artifactFile),
                   "Should have moved the artifact to the trash during a force delete operation, " +
                           "when allowsForceDeletion is not enabled!");

        final Path repositoryIndexDir = repositoryPath.resolve(".index");

        assertTrue(Files.exists(repositoryIndexDir), "Should not have deleted .index directory!");
    }

    @ExtendWith({ RepositoryManagementTestExecutionListener.class,
                  ArtifactManagementTestExecutionListener.class })
    @Test
    public void testForceDeleteArtifactAllowed(@MavenRepository(repositoryId = REPOSITORY_WITH_FORCE_DELETE_1)
                                               Repository repository,
                                               @MavenTestArtifact(repositoryId = REPOSITORY_WITH_FORCE_DELETE_1,
                                                                  id = "org.carlspring.strongbox:test-artifact-to-trash",
                                                                  versions = "1.1")
                                               Path artifactPath)
            throws IOException
    {
        final String storageId = repository.getStorage().getId();
        final String repositoryId = repository.getId();
        final RootRepositoryPath repositoryPath = repositoryPathResolver.resolve(repository);

        final String artifactPathStr = repositoryPath.relativize(artifactPath).toString();
        final RepositoryPath artifactRepositoryPath = repositoryPathResolver.resolve(repository, artifactPathStr);

        // Delete the artifact (this one shouldn't get placed under the .trash)
        client.delete(storageId, repositoryId, artifactPathStr, true);

        final Path artifactFileInTrash = RepositoryFiles.trash(artifactRepositoryPath);

        final Path repositoryDir = repositoryPath.resolve(repositoryId).resolve(".trash");

        assertFalse(Files.exists(artifactFileInTrash),
                    "Failed to delete artifact during a force delete operation!");
        assertFalse(Files.exists(repositoryDir.resolve(artifactPathStr)),
                    "Failed to delete artifact during a force delete operation!");
    }

    @ExtendWith({ RepositoryManagementTestExecutionListener.class,
                  ArtifactManagementTestExecutionListener.class })
    @ParameterizedTest
    @ValueSource(strings = { MediaType.APPLICATION_JSON_VALUE,
                             MediaType.TEXT_PLAIN_VALUE })
    void testDeleteArtifactAndEmptyTrashForRepository(String acceptHeader,
                                                      @MavenRepository(repositoryId = REPOSITORY_WITH_TRASH_2)
                                                      @RepositoryAttributes(trashEnabled = true)
                                                      Repository repository,
                                                      @MavenTestArtifact(repositoryId = REPOSITORY_WITH_TRASH_2,
                                                                         id = "org.carlspring.strongbox:test-artifact-to-trash",
                                                                         versions = "1.0")
                                                      Path artifactPath)
    {
        final String storageId = repository.getStorage().getId();
        final String repositoryId = repository.getId();

        String url = getContextBaseUrl() + "/{storageId}/{repositoryId}";

        ValidatableMockMvcResponse response = given().accept(acceptHeader)
                                                     .when()
                                                     .delete(url, storageId, repositoryId)
                                                     .peek()
                                                     .then()
                                                     .statusCode(HttpStatus.OK.value());

        String message = String.format("The trash for '%s:%s' was removed successfully.", storageId, repositoryId);
        validateResponseBody(response, acceptHeader, message);

        assertFalse(Files.exists(getPathToArtifactInTrash(repository, artifactPath)),
                    "Failed to empty trash for repository '" + repositoryId + "'!");
    }

    @ExtendWith({ RepositoryManagementTestExecutionListener.class,
                  ArtifactManagementTestExecutionListener.class })
    @ParameterizedTest
    @ValueSource(strings = { MediaType.APPLICATION_JSON_VALUE,
                             MediaType.TEXT_PLAIN_VALUE })
    void testDeleteArtifactAndEmptyTrashForAllRepositories(String acceptHeader,
                                                           @MavenRepository(repositoryId = REPOSITORY_WITH_TRASH_3)
                                                           @RepositoryAttributes(trashEnabled = true)
                                                           Repository repository1,
                                                           @MavenTestArtifact(repositoryId = REPOSITORY_WITH_TRASH_3,
                                                                              id = "org.carlspring.strongbox:test-artifact-to-trash",
                                                                              versions = "1.0")
                                                           Path artifactPath1,
                                                           @MavenRepository(repositoryId = REPOSITORY_WITH_FORCE_DELETE_2)
                                                           Repository repository2,
                                                           @MavenTestArtifact(repositoryId = REPOSITORY_WITH_FORCE_DELETE_2,
                                                                              id = "org.carlspring.strongbox:test-artifact-to-trash",
                                                                              versions = "1.1")
                                                           Path artifactPath2)
    {
        String url = getContextBaseUrl();

        ValidatableMockMvcResponse response = given().accept(acceptHeader)
                                                     .when()
                                                     .delete(url)
                                                     .peek()
                                                     .then()
                                                     .statusCode(HttpStatus.OK.value());

        String message = "The trash for all repositories was successfully removed.";
        validateResponseBody(response, acceptHeader, message);

        assertFalse(Files.exists(getPathToArtifactInTrash(repository1, artifactPath1)),
                    "Failed to empty trash for repository '" + repository1.getId() + "'!");
    }

    private Path getPathToArtifactInTrash(Repository repository,
                                          Path artifactPath)
    {
        RootRepositoryPath repositoryPath = repositoryPathResolver.resolve(repository);
        String artifactPathStr = repositoryPath.relativize(artifactPath).toString();
        return repositoryPath.resolve(".trash").resolve(artifactPathStr);
    }

    private void validateResponseBody(ValidatableMockMvcResponse response,
                                      String acceptHeader,
                                      String message)
    {
        if (acceptHeader.equals(MediaType.APPLICATION_JSON_VALUE))
        {
            response.body("message", equalTo(message));
        }
        else if (acceptHeader.equals(MediaType.TEXT_PLAIN_VALUE))
        {
            response.body(equalTo(message));
        }
        else
        {
            throw new IllegalArgumentException("Unsupported content type: " + acceptHeader);
        }
    }

}
