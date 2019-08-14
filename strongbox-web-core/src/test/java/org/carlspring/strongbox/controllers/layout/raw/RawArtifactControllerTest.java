package org.carlspring.strongbox.controllers.layout.raw;

import org.carlspring.strongbox.config.IntegrationTest;
import org.carlspring.strongbox.providers.layout.RawLayoutProvider;
import org.carlspring.strongbox.rest.common.RawRestAssuredBaseTest;
import org.carlspring.strongbox.storage.repository.RawRepositoryFactory;
import org.carlspring.strongbox.storage.repository.RepositoryDto;
import org.carlspring.strongbox.storage.repository.RepositoryPolicyEnum;

import javax.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import static io.restassured.module.mockmvc.RestAssuredMockMvc.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Martin Todorov
 */
@IntegrationTest
public class RawArtifactControllerTest
        extends RawRestAssuredBaseTest
{

    private static final String TEST_RESOURCES = "target/test-resources";

    private static final String REPOSITORY_RELEASES = "ract-raw-releases";

    @Inject
    RawRepositoryFactory rawRepositoryFactory;


    @BeforeAll
    public static void cleanUp()
            throws Exception
    {
        cleanUp(getRepositoriesToClean());
    }

    public static Set<RepositoryDto> getRepositoriesToClean()
    {
        Set<RepositoryDto> repositories = new LinkedHashSet<>();
        repositories.add(createRepositoryMock(STORAGE0, REPOSITORY_RELEASES, RawLayoutProvider.ALIAS));

        return repositories;
    }

    @Override
    @BeforeEach
    public void init()
            throws Exception
    {
        // Notes:
        // This tests resources currently don't need per test method isolation.
        // In case the requirements change, this test will need to be refactored.
        super.init();

        RepositoryDto repository = rawRepositoryFactory.createRepository(REPOSITORY_RELEASES);
        repository.setPolicy(RepositoryPolicyEnum.RELEASE.getPolicy());

        createRepository(STORAGE0, repository);

        // createRepositoryWithFile(repository, STORAGE0, "org/foo/bar/blah.zip");

        //noinspection ResultOfMethodCallIgnored
        Files.createDirectories(Paths.get(TEST_RESOURCES));
    }

    @Test
    public void testDeploy()
            throws IOException
    {
        String path = "org/foo/bar/blah.txt";
        byte[] content = "This is a test file\n".getBytes();

        // Push
        String artfactUrl = getContextBaseUrl() + "/storages/" + STORAGE0 + "/" + REPOSITORY_RELEASES + "/" + path;

        given().header(HttpHeaders.USER_AGENT, "Raw/*")
               .contentType(MediaType.MULTIPART_FORM_DATA_VALUE)
               .body(content)
               .when()
               .put(artfactUrl)
               .peek()
               .then()
               .statusCode(HttpStatus.OK.value());

        assertPathExists(artfactUrl);

        InputStream is = client.getResource(artfactUrl);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        int len;
        final int size = 64;
        byte[] bytes = new byte[size];
        while ((len = is.read(bytes, 0, size)) != -1)
        {
            baos.write(bytes, 0, len);
        }

        baos.flush();

        assertEquals(new String(content), new String(baos.toByteArray()), "Deployed content mismatch!");

        System.out.println("Read '" + new String(baos.toByteArray()) + "'.");
    }

    @Test
    public void testResolveViaHostedRepository()
            throws Exception
    {
        createFile(STORAGE0, REPOSITORY_RELEASES, "org/foo/bar/blah.zip");

        String artifactPath = "/storages/" + STORAGE0 + "/" + REPOSITORY_RELEASES + "/org/foo/bar/blah.zip";

        resolveArtifact(artifactPath);
    }

}
