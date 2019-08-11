package org.carlspring.strongbox.controllers.configuration;

import org.carlspring.strongbox.config.IntegrationTest;
import org.carlspring.strongbox.providers.layout.Maven2LayoutProvider;
import org.carlspring.strongbox.rest.common.MavenRestAssuredBaseTest;
import org.carlspring.strongbox.storage.repository.MavenRepositoryFactory;
import org.carlspring.strongbox.storage.repository.RepositoryDto;
import org.carlspring.strongbox.storage.repository.RepositoryPolicyEnum;
import org.carlspring.strongbox.storage.validation.deployment.RedeploymentValidator;

import javax.inject.Inject;
import javax.xml.bind.JAXBException;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import static io.restassured.module.mockmvc.RestAssuredMockMvc.given;
import static org.carlspring.strongbox.controllers.configuration.ArtifactCoordinateValidatorsManagementController.*;
import static org.carlspring.strongbox.web.RepositoryMethodArgumentResolver.NOT_FOUND_REPOSITORY_MESSAGE;
import static org.carlspring.strongbox.web.RepositoryMethodArgumentResolver.NOT_FOUND_STORAGE_MESSAGE;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

/**
 * @author Przemyslaw Fusik
 * @author Pablo Tirado
 * @author Aditya Srinivasan
 */
@IntegrationTest
@Execution(CONCURRENT)
public class ArtifactCoordinateValidatorsManagementControllerTest
        extends MavenRestAssuredBaseTest
{
    private final static String REPOSITORY_RELEASES_SINGLE_VALIDATOR = "releases-with-single-validator";
    private final static String REPOSITORY_RELEASES_DEFAULT_VALIDATORS = "releases-with-default-validators";
    private final static String REPOSITORY_ANOTHER_RELEASES_SINGLE_VALIDATOR = "another-releases-with-default-validators";
    private final static String REPOSITORY_ANOTHER_RELEASES_SINGLE_VALIDATOR_2 = "yet-another-releases-with-default-validators";
    private final static String REPOSITORY_SINGLE_VALIDATOR_ONLY = "single-validator-only";

    @Inject
    private MavenRepositoryFactory mavenRepositoryFactory;

    @Inject
    private RedeploymentValidator redeploymentValidator;

    public static Set<RepositoryDto> getRepositoriesToClean()
    {
        Set<RepositoryDto> repositories = new LinkedHashSet<>();
        repositories.add(createRepositoryMock(STORAGE0, REPOSITORY_RELEASES_SINGLE_VALIDATOR, Maven2LayoutProvider.ALIAS));
        repositories.add(createRepositoryMock(STORAGE0, REPOSITORY_RELEASES_DEFAULT_VALIDATORS, Maven2LayoutProvider.ALIAS));
        repositories.add(createRepositoryMock(STORAGE0, REPOSITORY_ANOTHER_RELEASES_SINGLE_VALIDATOR, Maven2LayoutProvider.ALIAS));
        repositories.add(createRepositoryMock(STORAGE0, REPOSITORY_ANOTHER_RELEASES_SINGLE_VALIDATOR_2, Maven2LayoutProvider.ALIAS));
        repositories.add(createRepositoryMock(STORAGE0, REPOSITORY_SINGLE_VALIDATOR_ONLY, Maven2LayoutProvider.ALIAS));

        return repositories;
    }

    @Override
    @BeforeEach
    public void init()
            throws Exception
    {
        super.init();

        RepositoryDto repository1 = mavenRepositoryFactory.createRepository(REPOSITORY_RELEASES_SINGLE_VALIDATOR);
        repository1.setPolicy(RepositoryPolicyEnum.RELEASE.getPolicy());
        repository1.setArtifactCoordinateValidators(
                new LinkedHashSet<>(Collections.singletonList(redeploymentValidator.getAlias())));

        createRepository(STORAGE0, repository1);

        RepositoryDto repository2 = mavenRepositoryFactory.createRepository(REPOSITORY_RELEASES_DEFAULT_VALIDATORS);
        repository2.setPolicy(RepositoryPolicyEnum.RELEASE.getPolicy());

        createRepository(STORAGE0, repository2);

        RepositoryDto repository3 = mavenRepositoryFactory.createRepository(
                REPOSITORY_ANOTHER_RELEASES_SINGLE_VALIDATOR);
        repository3.setPolicy(RepositoryPolicyEnum.RELEASE.getPolicy());

        createRepository(STORAGE0, repository3);

        RepositoryDto repository4 = mavenRepositoryFactory.createRepository(REPOSITORY_ANOTHER_RELEASES_SINGLE_VALIDATOR_2);
        repository4.setPolicy(RepositoryPolicyEnum.RELEASE.getPolicy());

        createRepository(STORAGE0, repository4);

        RepositoryDto repository5 = mavenRepositoryFactory.createRepository("single-validator-only");
        repository5.setPolicy(RepositoryPolicyEnum.RELEASE.getPolicy());
        repository5.setArtifactCoordinateValidators(
                new LinkedHashSet<>(Collections.singletonList(redeploymentValidator.getAlias())));

        createRepository(STORAGE0, repository5);

        setContextBaseUrl(getContextBaseUrl() + "/api/configuration/artifact-coordinate-validators");
    }

    @AfterEach
    public void removeRepositories()
            throws IOException, JAXBException
    {
        removeRepositories(getRepositoriesToClean());
    }

    @Test
    public void expectOneValidator()
    {
        String url = getContextBaseUrl() + "/{storageId}/{repositoryId}";
        String repositoryId = REPOSITORY_RELEASES_SINGLE_VALIDATOR;

        given().accept(MediaType.APPLICATION_JSON_VALUE)
               .when()
               .get(url, STORAGE0, repositoryId)
               .peek()
               .then()
               .statusCode(HttpStatus.OK.value())
               .body("versionValidators", containsInAnyOrder("redeployment-validator"));
    }

    @Test
    public void expectedThreeDefaultValidatorsForRepositoryWithDefaultValidators()
    {
        String url = getContextBaseUrl() + "/{storageId}/{repositoryId}";
        String repositoryId = REPOSITORY_ANOTHER_RELEASES_SINGLE_VALIDATOR;

        given().accept(MediaType.APPLICATION_JSON_VALUE)
               .when()
               .get(url, STORAGE0, repositoryId)
               .peek()
               .then()
               .statusCode(HttpStatus.OK.value())
               .body("versionValidators",
                     containsInAnyOrder("maven-release-version-validator",
                                        "maven-snapshot-version-validator",
                                        "redeployment-validator"));
    }

    @Test
    public void shouldNotGetValidatorWithNoStorageFound()
    {
        String url = getContextBaseUrl() + "/{storageId}/{repositoryId}";
        String storageId = "storage-not-found";
        String repositoryId = REPOSITORY_RELEASES_SINGLE_VALIDATOR;
        String message = String.format(NOT_FOUND_STORAGE_MESSAGE, storageId);

        given().accept(MediaType.APPLICATION_JSON_VALUE)
               .when()
               .get(url, storageId, repositoryId)
               .peek()
               .then()
               .statusCode(HttpStatus.NOT_FOUND.value())
               .body("message", equalTo(message));
    }

    @Test
    public void shouldNotGetValidatorWithNoRepositoryFound()
    {
        String url = getContextBaseUrl() + "/{storageId}/{repositoryId}";
        String storageId = STORAGE0;
        String repositoryId = "releases-not-found";
        String message = String.format(NOT_FOUND_REPOSITORY_MESSAGE, storageId, repositoryId);

        given().accept(MediaType.APPLICATION_JSON_VALUE)
               .when()
               .get(url, storageId, repositoryId)
               .peek()
               .then()
               .statusCode(HttpStatus.NOT_FOUND.value())
               .body("message", equalTo(message));
    }

    @ParameterizedTest
    @ValueSource(strings = { MediaType.APPLICATION_JSON_VALUE,
                             MediaType.TEXT_PLAIN_VALUE })
    void validatorsForReleaseRepositoryShouldBeRemovableAndFailSafe(String acceptHeader)
    {
        String url = getContextBaseUrl() + "/{storageId}/{repositoryId}/{alias}";
        String repositoryId = REPOSITORY_RELEASES_DEFAULT_VALIDATORS;
        String alias = "maven-snapshot-version-validator";

        given().accept(acceptHeader)
               .when()
               .put(url, STORAGE0, repositoryId, alias)
               .peek()
               .then()
               .statusCode(HttpStatus.OK.value())
               .body(containsString(SUCCESSFUL_ADD));

        given().accept(acceptHeader)
               .when()
               .delete(url, STORAGE0, repositoryId, alias)
               .peek()
               .then()
               .statusCode(HttpStatus.OK.value())
               .body(containsString(SUCCESSFUL_DELETE));

        url = getContextBaseUrl() + "/{storageId}/{repositoryId}";

        given().accept(MediaType.APPLICATION_JSON_VALUE)
               .when()
               .get(url, STORAGE0, repositoryId)
               .peek()
               .then()
               .statusCode(HttpStatus.OK.value())
               .body("versionValidators",
                     containsInAnyOrder("redeployment-validator", "maven-release-version-validator"));
    }

    @ParameterizedTest
    @ValueSource(strings = { MediaType.APPLICATION_JSON_VALUE,
                             MediaType.TEXT_PLAIN_VALUE })
    void shouldNotRemoveAliasNotFound(String acceptHeader)
    {
        String url = getContextBaseUrl() + "/{storageId}/{repositoryId}/{alias}";
        String repositoryId = REPOSITORY_ANOTHER_RELEASES_SINGLE_VALIDATOR_2;
        String alias = "alias-not-found";

        given().accept(acceptHeader)
               .when()
               .delete(url, STORAGE0, repositoryId, alias)
               .peek()
               .then()
               .statusCode(HttpStatus.NOT_FOUND.value())
               .body(containsString(NOT_FOUND_ALIAS_MESSAGE));
    }


    @Test
    public void validatorsForReleaseRepositoryShouldBeAddableAndFailSafeWithResponseInJson()
    {
        validatorsForReleaseRepositoryShouldBeAddableAndFailSafe(MediaType.APPLICATION_JSON_VALUE);
    }

    @Test
    public void validatorsForReleaseRepositoryShouldBeAddableAndFailSafeWithResponseInText()
    {
        validatorsForReleaseRepositoryShouldBeAddableAndFailSafe(MediaType.TEXT_PLAIN_VALUE);
    }

    @Test
    public void getCollectionOfArtifactCoordinateValidators()
    {
        String url = getContextBaseUrl() + "/validators";

        given().accept(MediaType.APPLICATION_JSON_VALUE)
               .when()
               .get(url)
               .peek()
               .then()
               .statusCode(HttpStatus.OK.value())
               .body(containsString("versionValidators"), containsString("Maven 2"));
    }

    @Test
    public void getArtifactCoordinateValidatorsForLayoutProvider()
    {

        String url = getContextBaseUrl() + "/validators";
        String layoutProvider = "Maven 2";

        given().accept(MediaType.APPLICATION_JSON_VALUE)
               .when()
               .get(url, layoutProvider)
               .peek()
               .then()
               .statusCode(HttpStatus.OK.value())
               .body(containsString("supportedLayoutProviders"), containsString(layoutProvider));
    }

    @ParameterizedTest
    @ValueSource(strings = { MediaType.APPLICATION_JSON_VALUE,
                             MediaType.TEXT_PLAIN_VALUE })
    void validatorsForReleaseRepositoryShouldBeAddableAndFailSafe(String acceptHeader)
    {
        String urlList = getContextBaseUrl() + "/{storageId}/{repositoryId}";
        String urlAdd = getContextBaseUrl() + "/{storageId}/{repositoryId}/{alias}";
        String repositoryId = REPOSITORY_RELEASES_SINGLE_VALIDATOR;
        String alias = "/maven-snapshot-version-validator";

        given().accept(MediaType.APPLICATION_JSON_VALUE)
               .when()
               .get(urlList, STORAGE0, repositoryId)
               .peek()
               .then()
               .statusCode(HttpStatus.OK.value())
               .body("versionValidators", containsInAnyOrder("redeployment-validator"));

        given().accept(acceptHeader)
               .when()
               .put(urlAdd, STORAGE0, repositoryId, alias)
               .peek()
               .then()
               .statusCode(HttpStatus.OK.value())
               .body(containsString(SUCCESSFUL_ADD));

        given().accept(MediaType.APPLICATION_JSON_VALUE)
               .when()
               .get(urlList, STORAGE0, repositoryId)
               .peek()
               .then()
               .statusCode(HttpStatus.OK.value())
               .body("versionValidators",
                     containsInAnyOrder("redeployment-validator", "maven-snapshot-version-validator"));

        given().accept(acceptHeader)
               .when()
               .put(urlAdd, STORAGE0, repositoryId, alias)
               .peek()
               .then()
               .statusCode(HttpStatus.OK.value())
               .body(containsString(SUCCESSFUL_ADD));

        given().accept(MediaType.APPLICATION_JSON_VALUE)
               .when()
               .get(urlList, STORAGE0, repositoryId)
               .peek()
               .then()
               .statusCode(HttpStatus.OK.value())
               .body("versionValidators",
                     containsInAnyOrder("redeployment-validator", "maven-snapshot-version-validator"));
    }


}
