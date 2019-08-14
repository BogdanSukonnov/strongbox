package org.carlspring.strongbox.controllers;

import org.carlspring.strongbox.config.IntegrationTest;
import org.carlspring.strongbox.rest.common.RestAssuredBaseTest;
import org.carlspring.strongbox.users.security.SecurityTokenProvider;

import javax.inject.Inject;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import org.jose4j.jwt.NumericDate;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.TestSecurityContextHolder;
import static io.restassured.module.mockmvc.RestAssuredMockMvc.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

/**
 * @author adavid9
 * @author Pablo Tirado
 */
@IntegrationTest
@Execution(CONCURRENT)
public class JwtAuthenticationTest
        extends RestAssuredBaseTest
{

    private static final String UNAUTHORIZED_MESSAGE = "Full authentication is required to access this resource";

    @Inject
    private SecurityTokenProvider securityTokenProvider;


    @Override
    @BeforeEach
    public void init()
            throws Exception
    {
        super.init();

        setContextBaseUrl("/api/users");
        TestSecurityContextHolder.clearContext();
        SecurityContextHolder.clearContext();
    }

    @Test
    public void testJWTAuthShouldPassWithToken()
            throws Exception
    {
        String url = getContextBaseUrl() + "/login";

        String basicAuth = "Basic YWRtaW46cGFzc3dvcmQ=";

        String body = given().header(HttpHeaders.AUTHORIZATION, basicAuth)
                             .accept(MediaType.APPLICATION_JSON_VALUE)
                             .when()
                             .get(url)
                             .then()
                             .statusCode(HttpStatus.OK.value())
                             .extract()
                             .asString();
        TestSecurityContextHolder.clearContext();
        SecurityContextHolder.clearContext();

        url = getContextBaseUrl();
        given().accept(MediaType.APPLICATION_JSON_VALUE)
               .when()
               .get(url)
               .then()
               .statusCode(HttpStatus.UNAUTHORIZED.value())
               .body(notNullValue());
        TestSecurityContextHolder.clearContext();
        SecurityContextHolder.clearContext();

        // this token will expire after 1 hour
        String tokenValue = getTokenValue(body);
        given().header(HttpHeaders.AUTHORIZATION, getAuthorizationHeader(tokenValue))
               .accept(MediaType.APPLICATION_JSON_VALUE)
               .when()
               .get(url)
               .then()
               .statusCode(HttpStatus.OK.value())
               .body(notNullValue());
    }

    @Test
    public void testJWTAuthShouldFailWithoutToken()
    {
        String url = getContextBaseUrl();

        given().accept(MediaType.APPLICATION_JSON_VALUE)
               .when()
               .get(url)
               .then()
               .statusCode(HttpStatus.UNAUTHORIZED.value())
               .body("error", equalTo(UNAUTHORIZED_MESSAGE));
    }

    @Test
    public void testJWTInvalidToken()
    {
        String url = getContextBaseUrl();

        String invalidToken = "ABCD";

        given().header(HttpHeaders.AUTHORIZATION, getAuthorizationHeader(invalidToken))
               .accept(MediaType.APPLICATION_JSON_VALUE)
               .when()
               .get(url)
               .then()
               .statusCode(HttpStatus.UNAUTHORIZED.value())
               .body("error", equalTo("invalid.token"));
    }

    @Test
    public void testJWTExpirationToken()
            throws Exception
    {
        String url = getContextBaseUrl();

        // create token that will expire after 1 second
        String expiredToken = securityTokenProvider.getToken("admin", Collections.emptyMap(), 3, null);

        given().header(HttpHeaders.AUTHORIZATION, getAuthorizationHeader(expiredToken))
               .accept(MediaType.APPLICATION_JSON_VALUE)
               .when()
               .get(url)
               .then()
               .statusCode(HttpStatus.OK.value())
               .body(notNullValue());

        TimeUnit.SECONDS.sleep(3);

        given().header(HttpHeaders.AUTHORIZATION, getAuthorizationHeader(expiredToken))
               .accept(MediaType.APPLICATION_JSON_VALUE)
               .when()
               .get(url)
               .then()
               .statusCode(HttpStatus.UNAUTHORIZED.value())
               .body("error", equalTo("expired"));
    }

    @Test
    public void testJWTIssuedAtFuture()
            throws Exception
    {
        String url = getContextBaseUrl();

        NumericDate futureNumericDate = NumericDate.now();
        // add five minutes to the current time to create a JWT issued in the future
        futureNumericDate.addSeconds(300);

        String token = securityTokenProvider.getToken("admin", Collections.emptyMap(), 10, futureNumericDate);

        given().header(HttpHeaders.AUTHORIZATION, getAuthorizationHeader(token))
               .accept(MediaType.APPLICATION_JSON_VALUE)
               .when()
               .get(url)
               .then()
               .statusCode(HttpStatus.UNAUTHORIZED.value())
               .body("error", equalTo("invalid.token"));
    }

    private String getTokenValue(String body)
            throws JSONException
    {
        JSONObject extractToken = new JSONObject(body);
        return extractToken.getString("token");
    }

    private String getAuthorizationHeader(String tokenValue)
    {
        return String.format("Bearer %s", tokenValue);
    }

}
