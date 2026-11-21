package com.visionmapping.service.oauth;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * V14 (GA-NFR-02): a local stand-in for Google. It serves the token endpoint
 * ({@code /token}) and the key set ({@code /certs}), and signs test ID tokens
 * with its own RSA key, so no test ever calls Google.
 */
public final class GoogleStubServer implements AutoCloseable {

    public static final String CLIENT_ID = "test-client.apps.googleusercontent.com";

    private final HttpServer server;
    private final List<Map<String, String>> tokenRequests = new CopyOnWriteArrayList<>();
    private final AtomicInteger keySetFetches = new AtomicInteger();
    private volatile RSAKey signingKey = newKey();
    private volatile String idToken = "";
    private volatile int tokenStatus = 200;
    private volatile long tokenDelayMillis;

    public GoogleStubServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/token", exchange -> {
            tokenRequests.add(form(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
            sleep(tokenDelayMillis);
            String body = tokenStatus == 200
                    ? "{\"access_token\":\"ignored\",\"token_type\":\"Bearer\",\"expires_in\":3599,\"id_token\":\"" + idToken + "\"}"
                    : "{\"error\":\"invalid_grant\"}";
            answer(exchange, tokenStatus, body);
        });
        server.createContext("/certs", exchange -> {
            keySetFetches.incrementAndGet();
            answer(exchange, 200, new JWKSet(signingKey.toPublicJWK()).toString());
        });
        // Parallel, so a slow answer left over from a timeout test never blocks the next request.
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /** Resets the stub between tests. */
    public void reset() {
        tokenRequests.clear();
        idToken = "";
        tokenStatus = 200;
        tokenDelayMillis = 0;
    }

    /** The ID token the next code exchange returns. */
    public void answerWith(String token) {
        this.idToken = token;
    }

    public void failTokenEndpoint(int status) {
        this.tokenStatus = status;
    }

    public void delayTokenEndpoint(long millis) {
        this.tokenDelayMillis = millis;
    }

    /** Google rotates its keys: a new key ID, and only the new key is published. */
    public void rotateKey() {
        signingKey = newKey();
    }

    public List<Map<String, String>> tokenRequests() {
        return List.copyOf(tokenRequests);
    }

    public int keySetFetches() {
        return keySetFetches.get();
    }

    /** Claims as Google sends them for a verified Gmail account, valid for an hour. */
    public static JWTClaimsSet.Builder claims(String nonce) {
        Instant now = Instant.now();
        return new JWTClaimsSet.Builder()
                .issuer("https://accounts.google.com")
                .audience(CLIENT_ID)
                .subject("1" + Long.toUnsignedString(UUID.randomUUID().getMostSignificantBits()))
                .claim("email", "person@gmail.com")
                .claim("email_verified", true)
                .claim("name", "Test Person")
                .claim("nonce", nonce)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(Duration.ofHours(1))));
    }

    /** Signed with the published key. */
    public String sign(JWTClaimsSet claims) {
        return sign(claims, signingKey);
    }

    /** Signed with a key Google never published: the signature check must fail. */
    public static String signWithUnknownKey(JWTClaimsSet claims, String keyId) {
        return sign(claims, newKey(keyId));
    }

    public String currentKeyId() {
        return signingKey.getKeyID();
    }

    private static String sign(JWTClaimsSet claims, RSAKey key) {
        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims);
            jwt.sign(new RSASSASigner(key));
            return jwt.serialize();
        } catch (JOSEException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static RSAKey newKey() {
        return newKey(UUID.randomUUID().toString());
    }

    private static RSAKey newKey(String keyId) {
        try {
            return new RSAKeyGenerator(2048).keyID(keyId).generate();
        } catch (JOSEException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static Map<String, String> form(String body) {
        Map<String, String> values = new HashMap<>();
        for (String pair : body.split("&")) {
            int equals = pair.indexOf('=');
            if (equals > 0) {
                values.put(URLDecoder.decode(pair.substring(0, equals), StandardCharsets.UTF_8),
                        URLDecoder.decode(pair.substring(equals + 1), StandardCharsets.UTF_8));
            }
        }
        return values;
    }

    private static void sleep(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static void answer(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        try {
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        } catch (IOException closedByClient) {
            // The client gave up (a timeout test); nothing to answer.
        } finally {
            exchange.close();
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
