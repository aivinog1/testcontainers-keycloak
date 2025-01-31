package dasniko.testcontainers.keycloak;

import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.info.ServerInfoRepresentation;
import org.testcontainers.containers.ContainerLaunchException;

import java.time.Duration;
import java.time.Instant;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Niko Köbler, https://www.n-k.de, @dasniko
 */
public class KeycloakContainerTest {

    public static final String TEST_REALM_JSON = "/test-realm.json";

    @Test
    public void shouldStartKeycloak() {
        try (KeycloakContainer keycloak = new KeycloakContainer()) {
            keycloak.start();
        }
    }

    @Test
    public void shouldConsiderConfiguredStartupTimeout() {
        final int MAX_TIMEOUT = 5;
        Instant start = Instant.now();
        try {
            Duration duration = Duration.ofSeconds(MAX_TIMEOUT);
            try (KeycloakContainer keycloak = new KeycloakContainer().withStartupTimeout(duration)) {
                keycloak.start();
            }
        } catch(ContainerLaunchException ex) {
            Duration observedDuration = Duration.between(start, Instant.now());
            assertTrue(observedDuration.toMillis()/1000 >= MAX_TIMEOUT && observedDuration.toMillis()/1000 < 15,
                "Startup time should consider configured limit of " + MAX_TIMEOUT + " seconds.");
        }
    }

    @Test
    public void shouldImportRealm() {
        try (KeycloakContainer keycloak = new KeycloakContainer().withRealmImportFile(TEST_REALM_JSON)) {
            keycloak.start();

            String accountService = given().when().get(keycloak.getAuthServerUrl() + "realms/test")
                .then().statusCode(200).body("realm", equalTo("test"))
                .extract().path("account-service");

            given().when().get(accountService).then().statusCode(200);
        }
    }

    @Test
    public void shouldImportMultipleRealms() {
        try (KeycloakContainer keycloak = new KeycloakContainer().
            withRealmImportFiles(TEST_REALM_JSON, "/another-realm.json")) {
            keycloak.start();

            String accountService = given().when().get(keycloak.getAuthServerUrl() + "realms/test")
                .then().statusCode(200).body("realm", equalTo("test"))
                .extract().path("account-service");

            given().when().get(accountService).then().statusCode(200);

            accountService = given().when().get(keycloak.getAuthServerUrl() + "realms/another")
                .then().statusCode(200).body("realm", equalTo("another"))
                .extract().path("account-service");

            given().when().get(accountService).then().statusCode(200);
        }
    }

    @Test
    public void shouldReturnServerInfo() {
        try (KeycloakContainer keycloak = new KeycloakContainer()) {
            keycloak.start();

            checkKeycloakContainerInternals(keycloak);
        }
    }

    @Test
    public void shouldUseDifferentAdminCredentials() {
        try (KeycloakContainer keycloak = new KeycloakContainer()
            .withAdminUsername("foo")
            .withAdminPassword("bar")) {
            keycloak.start();

            checkKeycloakContainerInternals(keycloak);
        }
    }

    @Test
    public void shouldRunOnDifferentContextPath() {
        String contextPath = "/auth/";
        try (KeycloakContainer keycloak = new KeycloakContainer().withContextPath(contextPath)) {
            keycloak.start();

            String authServerUrl = keycloak.getAuthServerUrl();
            assertThat(authServerUrl, endsWith(contextPath));

            given().when().get(authServerUrl + "realms/master/.well-known/openid-configuration")
                .then().statusCode(200);

            checkKeycloakContainerInternals(keycloak);
        }
    }

    private void checkKeycloakContainerInternals(KeycloakContainer keycloak) {
        Keycloak keycloakAdminClient = keycloak.getKeycloakAdminClient();
        ServerInfoRepresentation serverInfo = keycloakAdminClient.serverInfo().getInfo();
        assertThat(serverInfo, notNullValue());
        assertThat(serverInfo.getSystemInfo().getVersion(), equalTo(keycloak.getKeycloakVersion()));
    }

}
