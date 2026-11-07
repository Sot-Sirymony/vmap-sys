package com.visionmapping.service.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.visionmapping.exception.AiUnavailableException;
import com.visionmapping.service.ai.ClaudeStubServer.Answer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** BRD 12 COV-FR-28: ClaudeClient against a local stub of the Claude API; no real call is made. */
class ClaudeClientTest {

    private static final String OPUS = "claude-opus-5-5";
    private static final String HAIKU = "claude-haiku-5-5";
    private static final List<ClaudeClient.Turn> TURNS = List.of(
            new ClaudeClient.Turn(true, "I want to open a bakery."),
            new ClaudeClient.Turn(false, "What would success look like?"),
            new ClaudeClient.Turn(true, "Selling 200 loaves a day."));

    private ClaudeStubServer stub;
    private ClaudeClient client;

    record SuggestedGoals(List<String> goals) {
    }

    @BeforeEach
    void startStub() throws Exception {
        stub = new ClaudeStubServer();
        client = new ClaudeClient(stub.baseUrl(), Duration.ofSeconds(2), 0);
    }

    @AfterEach
    void stopStub() {
        stub.close();
    }

    @Test
    void sendsTheConversationAndReturnsTheReplyWithItsTokenCounts() {
        stub.answer(Answer.json(200, ClaudeStubServer.message(OPUS, "Start with a market stall.", "end_turn")));

        ClaudeClient.Reply reply = client.send("key-1", OPUS, "You are a coach.", TURNS);

        assertThat(reply.text()).isEqualTo("Start with a market stall.");
        assertThat(reply.model()).isEqualTo(OPUS);
        assertThat(reply.inputTokens()).isEqualTo(120);
        assertThat(reply.outputTokens()).isEqualTo(45);
        assertThat(reply.refused()).isFalse();
        assertThat(reply.cutShort()).isFalse();

        ClaudeStubServer.Request request = stub.requests().get(0);
        assertThat(request.path()).isEqualTo("/v1/messages");
        assertThat(request.apiKey()).isEqualTo("key-1");
        assertThat(request.body())
                .contains("\"system\":\"You are a coach.\"")
                .contains("\"role\":\"user\"").contains("\"role\":\"assistant\"")
                .contains("Selling 200 loaves a day.")
                .contains("\"effort\":\"low\"")
                // Opus and Sonnet ask the API to retry a safeguard refusal on a fallback model.
                .contains("\"fallbacks\":\"default\"");
    }

    @Test
    void asksForNoFallbackOnModelsThatHaveNone() {
        stub.answer(Answer.json(200, ClaudeStubServer.message(HAIKU, "Hi", "end_turn")));

        client.send("key-1", HAIKU, "System", TURNS);

        assertThat(stub.requests().get(0).body()).doesNotContain("fallbacks");
    }

    @Test
    void marksARefusalAndACutShortReply() {
        stub.answer(Answer.json(200, ClaudeStubServer.message(OPUS, "", "refusal")));
        assertThat(client.send("key-1", OPUS, "System", TURNS).refused()).isTrue();

        stub.answer(Answer.json(200, ClaudeStubServer.message(OPUS, "Step one is to", "max_tokens")));
        ClaudeClient.Reply cut = client.send("key-1", OPUS, "System", TURNS);
        assertThat(cut.cutShort()).isTrue();
        assertThat(cut.refused()).isFalse();
        assertThat(cut.text()).isEqualTo("Step one is to");
    }

    @Test
    void reportsAnErrorStatusAsAiUnavailable() {
        stub.answer(Answer.json(529, ClaudeStubServer.error("overloaded_error", "Overloaded")));
        assertThatThrownBy(() -> client.send("key-1", OPUS, "System", TURNS)).isInstanceOf(AiUnavailableException.class);

        stub.answer(Answer.json(401, ClaudeStubServer.error("authentication_error", "invalid x-api-key")));
        assertThatThrownBy(() -> client.send("key-1", OPUS, "System", TURNS)).isInstanceOf(AiUnavailableException.class);
    }

    @Test
    void reportsATimeoutAsAiUnavailable() {
        ClaudeClient impatient = new ClaudeClient(stub.baseUrl(), Duration.ofMillis(300), 0);
        stub.answer(Answer.slow(2000));
        assertThatThrownBy(() -> impatient.send("key-1", OPUS, "System", TURNS)).isInstanceOf(AiUnavailableException.class);
    }

    @Test
    void reportsAnUnreachableApiAsAiUnavailable() {
        String address = stub.baseUrl();
        stub.close();
        ClaudeClient offline = new ClaudeClient(address, Duration.ofSeconds(1), 0);
        assertThatThrownBy(() -> offline.send("key-1", OPUS, "System", TURNS)).isInstanceOf(AiUnavailableException.class);
    }

    @Test
    void streamsEachPieceAsItArrivesAndReturnsTheWholeReply() {
        stub.answer(Answer.sse(ClaudeStubServer.stream(OPUS, "end_turn", "Start ", "with a ", "market stall.")));
        List<String> pieces = new ArrayList<>();

        ClaudeClient.Reply reply = client.stream("key-1", OPUS, "System", TURNS, pieces::add);

        assertThat(pieces).containsExactly("Start ", "with a ", "market stall.");
        assertThat(reply.text()).isEqualTo("Start with a market stall.");
        assertThat(reply.outputTokens()).isEqualTo(45);
        assertThat(stub.requests().get(0).body()).contains("\"stream\":true");
    }

    @Test
    void marksAStreamedReplyThatWasCutShortOrRefused() {
        stub.answer(Answer.sse(ClaudeStubServer.stream(OPUS, "max_tokens", "Step one is to")));
        assertThat(client.stream("key-1", OPUS, "System", TURNS, piece -> { }).cutShort()).isTrue();

        stub.answer(Answer.sse(ClaudeStubServer.stream(OPUS, "refusal")));
        assertThat(client.stream("key-1", OPUS, "System", TURNS, piece -> { }).refused()).isTrue();
    }

    @Test
    void reportsAStreamThatFailsAsAiUnavailable() {
        stub.answer(Answer.json(500, ClaudeStubServer.error("api_error", "Internal error")));
        assertThatThrownBy(() -> client.stream("key-1", OPUS, "System", TURNS, piece -> { }))
                .isInstanceOf(AiUnavailableException.class);
    }

    @Test
    void readsAStructuredAnswerIntoTheRequestedType() {
        stub.answer(Answer.json(200, ClaudeStubServer.message(OPUS, "{\"goals\":[\"Find a location\",\"Test recipes\"]}", "end_turn")));

        ClaudeClient.Structured<SuggestedGoals> answer = client.structured("key-1", OPUS, "System", "Suggest goals", SuggestedGoals.class);

        assertThat(answer.refused()).isFalse();
        assertThat(answer.value().goals()).containsExactly("Find a location", "Test recipes");
        assertThat(answer.inputTokens()).isEqualTo(120);
        assertThat(stub.requests().get(0).body())
                .contains("\"effort\":\"medium\"")
                .contains("\"max_tokens\":" + ClaudeClient.STRUCTURED_MAX_TOKENS)
                .contains("\"fallbacks\":\"default\"");
    }

    @Test
    void returnsNoValueWhenAStructuredRequestIsRefused() {
        stub.answer(Answer.json(200, ClaudeStubServer.message(HAIKU, "", "refusal")));

        ClaudeClient.Structured<SuggestedGoals> answer = client.structured("key-1", HAIKU, "System", "Suggest goals", SuggestedGoals.class);

        assertThat(answer.refused()).isTrue();
        assertThat(answer.value()).isNull();
        assertThat(stub.requests().get(0).body()).doesNotContain("fallbacks");
    }

    @Test
    void reportsAStructuredAnswerThatIsNotValidJsonOrAnErrorAsAiUnavailable() {
        stub.answer(Answer.json(200, ClaudeStubServer.message(OPUS, "not json at all", "end_turn")));
        assertThatThrownBy(() -> client.structured("key-1", OPUS, "System", "Suggest goals", SuggestedGoals.class))
                .isInstanceOf(AiUnavailableException.class);

        stub.answer(Answer.json(429, ClaudeStubServer.error("rate_limit_error", "Slow down")));
        assertThatThrownBy(() -> client.structured("key-1", OPUS, "System", "Suggest goals", SuggestedGoals.class))
                .isInstanceOf(AiUnavailableException.class);
    }

    @Test
    void usesANewKeyAtOnce() {
        stub.answer(Answer.json(200, ClaudeStubServer.message(OPUS, "Hi", "end_turn")));

        client.send("old-key", OPUS, "System", TURNS);
        client.send("old-key", OPUS, "System", TURNS);
        client.send("new-key", OPUS, "System", TURNS);

        assertThat(stub.requests()).extracting(ClaudeStubServer.Request::apiKey).containsExactly("old-key", "old-key", "new-key");
    }
}
