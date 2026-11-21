package com.visionmapping.service.oauth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * V14 (GA-FR-11, GA-SEC-01, GA-SEC-11): exchanges Google's one-time code for
 * the ID token, at Google's token endpoint, with the client secret and the PKCE
 * verifier. Calls give up after the configured timeout (5 seconds) and fail
 * closed. Neither the code, the secret nor the answer is ever logged.
 */
@Component
public class GoogleTokenClient {

    private final GoogleOAuthSettings settings;
    private final RestClient restClient;

    public GoogleTokenClient(GoogleOAuthSettings settings) {
        this.settings = settings;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(settings.timeout());
        factory.setReadTimeout(settings.timeout());
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    /** The ID token from Google's answer; GOOGLE when Google cannot be reached or refuses the code. */
    String exchange(String code, String codeVerifier) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("client_id", settings.clientId());
        form.add("client_secret", settings.clientSecret());
        form.add("redirect_uri", settings.redirectUri());
        form.add("code_verifier", codeVerifier);
        TokenAnswer answer;
        try {
            answer = restClient.post()
                    .uri(settings.tokenUri())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(form)
                    .retrieve()
                    .body(TokenAnswer.class);
        } catch (RestClientException exception) {
            throw new GoogleSignInException(GoogleSignInFailure.GOOGLE,
                    "Google's token endpoint failed: " + exception.getClass().getSimpleName(), exception);
        }
        if (answer == null || answer.idToken() == null || answer.idToken().isBlank()) {
            throw new GoogleSignInException(GoogleSignInFailure.VERIFICATION, "Google's token answer had no ID token");
        }
        return answer.idToken();
    }

    /** Only the field this flow needs; the access token is ignored and never stored. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record TokenAnswer(@Nullable @JsonProperty("id_token") String idToken) {
    }
}
