package com.visionmapping.service.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.visionmapping.service.ai.ClaudeStubServer;
import com.visionmapping.service.ai.ClaudeStubServer.Answer;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** BRD 12 COV-FR-28: the admin's "Test key" check against a local stub of the Models API. */
class ClaudeKeyCheckerTest {

    private ClaudeStubServer stub;
    private ClaudeKeyChecker checker;

    @BeforeEach
    void startStub() throws Exception {
        stub = new ClaudeStubServer();
        checker = new ClaudeKeyChecker(stub.baseUrl(), Duration.ofSeconds(2), 0);
    }

    @AfterEach
    void stopStub() {
        stub.close();
    }

    @Test
    void confirmsAWorkingKeyByLookingUpTheChosenModel() {
        stub.answer(Answer.json(200, """
                {"type":"model","id":"claude-opus-5-5","display_name":"Claude Opus 5.5","created_at":"2026-09-01T00:00:00Z"}
                """));

        ClaudeKeyChecker.Result result = checker.check("key-1", "claude-opus-5-5");

        assertThat(result.ok()).isTrue();
        assertThat(result.message()).isEqualTo("Connected. Claude Opus 5.5 is available.");
        assertThat(stub.requests().get(0).method()).isEqualTo("GET");
        assertThat(stub.requests().get(0).path()).isEqualTo("/v1/models/claude-opus-5-5");
        assertThat(stub.requests().get(0).apiKey()).isEqualTo("key-1");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "401|authentication_error|The API key was not accepted. Check it and save it again.",
        "403|permission_error|This API key is not allowed to use the Claude API.",
        "404|not_found_error|The key works, but the model claude-opus-5-5 is not available to it.",
        "429|rate_limit_error|The Claude API is busy right now. Try again in a minute.",
        "500|api_error|The Claude API answered with an error (500).",
    })
    void explainsEachKindOfFailureInPlainWords(int status, String type, String message) {
        stub.answer(Answer.json(status, ClaudeStubServer.error(type, "details")));

        ClaudeKeyChecker.Result result = checker.check("key-1", "claude-opus-5-5");

        assertThat(result.ok()).isFalse();
        assertThat(result.message()).isEqualTo(message);
    }

    @Test
    void saysWhenTheApiCannotBeReached() {
        String address = stub.baseUrl();
        stub.close();

        ClaudeKeyChecker.Result result = new ClaudeKeyChecker(address, Duration.ofSeconds(1), 0).check("key-1", "claude-opus-5-5");

        assertThat(result.ok()).isFalse();
        assertThat(result.message()).isEqualTo("Could not reach the Claude API. Check the server's internet connection.");
    }
}
