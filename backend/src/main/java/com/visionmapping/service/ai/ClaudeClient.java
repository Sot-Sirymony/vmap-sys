package com.visionmapping.service.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.helpers.BetaMessageAccumulator;
import com.anthropic.models.beta.messages.BetaMessage;
import com.anthropic.models.beta.messages.BetaOutputConfig;
import com.anthropic.models.beta.messages.BetaRawMessageStreamEvent;
import com.anthropic.models.beta.messages.BetaStopReason;
import com.anthropic.models.beta.messages.BetaTextBlock;
import com.anthropic.models.beta.messages.MessageCreateParams;
import com.visionmapping.exception.AiUnavailableException;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * V9 (BRD integration overview): the only place the app talks to Claude. The
 * key comes from the AI settings on each call, so a new key takes effect at
 * once. On Opus and Sonnet a safeguard refusal is retried on a fallback model
 * by the API itself ({@code fallbacks: "default"}).
 */
@Slf4j
@Component
public class ClaudeClient {

    /** Short chat replies; MAX_TOKENS is reported as cut short rather than failing. */
    static final long MAX_TOKENS = 4096;
    private static final String FALLBACK_BETA = "server-side-fallback-2026-07-01";
    private static final Set<String> MODELS_WITH_FALLBACK = Set.of("claude-opus-5-5", "claude-sonnet-5-5");

    public record Turn(boolean fromParticipant, String text) {
    }

    public record Reply(String text, String model, long inputTokens, long outputTokens, boolean refused, boolean cutShort) {
    }

    private String cachedKey;
    private AnthropicClient cachedClient;

    public Reply send(String apiKey, String model, String system, List<Turn> turns) {
        try {
            return toReply(client(apiKey).beta().messages().create(params(model, system, turns)));
        } catch (AnthropicServiceException exception) {
            log.warn("Claude API answered {} for model {}", exception.statusCode(), model);
            throw new AiUnavailableException();
        } catch (AnthropicIoException exception) {
            log.warn("Could not reach the Claude API: {}", exception.getMessage());
            throw new AiUnavailableException();
        }
    }

    /**
     * AI-NFR-01: streams the reply so the first words show within seconds.
     * onText gets each piece of text as it arrives; the returned Reply, built
     * from the whole stream, is the authoritative text to save.
     */
    public Reply stream(String apiKey, String model, String system, List<Turn> turns, Consumer<String> onText) {
        BetaMessageAccumulator accumulator = BetaMessageAccumulator.create();
        try (StreamResponse<BetaRawMessageStreamEvent> response = client(apiKey).beta().messages()
                .createStreaming(params(model, system, turns))) {
            response.stream().forEach(event -> {
                accumulator.accumulate(event);
                event.contentBlockDelta()
                        .flatMap(delta -> delta.delta().text())
                        .ifPresent(text -> onText.accept(text.text()));
            });
            return toReply(accumulator.message());
        } catch (AnthropicServiceException exception) {
            log.warn("Claude API answered {} for model {} while streaming", exception.statusCode(), model);
            throw new AiUnavailableException();
        } catch (AnthropicIoException exception) {
            log.warn("Lost the Claude API stream: {}", exception.getMessage());
            throw new AiUnavailableException();
        }
    }

    private MessageCreateParams params(String model, String system, List<Turn> turns) {
        MessageCreateParams.Builder params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(MAX_TOKENS)
                .system(system)
                // Coaching chat does not repay deep thinking; low effort keeps
                // replies quick and inside the $20 monthly budget (AI-NFR-15).
                .outputConfig(BetaOutputConfig.builder().effort(BetaOutputConfig.Effort.LOW).build());
        for (Turn turn : turns) {
            if (turn.fromParticipant()) {
                params.addUserMessage(turn.text());
            } else {
                params.addAssistantMessage(turn.text());
            }
        }
        if (MODELS_WITH_FALLBACK.contains(model)) {
            params.addBeta(FALLBACK_BETA).putAdditionalBodyProperty("fallbacks", JsonValue.from("default"));
        }
        return params.build();
    }

    private static Reply toReply(BetaMessage message) {
        String text = message.content().stream()
                .flatMap(block -> block.text().stream())
                .map(BetaTextBlock::text)
                .collect(Collectors.joining("\n\n"))
                .trim();
        BetaStopReason stop = message.stopReason().orElse(null);
        return new Reply(text, message.model().asString(), message.usage().inputTokens(), message.usage().outputTokens(),
                BetaStopReason.REFUSAL.equals(stop), BetaStopReason.MAX_TOKENS.equals(stop));
    }

    private synchronized AnthropicClient client(String apiKey) {
        if (!apiKey.equals(cachedKey)) {
            if (cachedClient != null) {
                cachedClient.close();
            }
            cachedClient = AnthropicOkHttpClient.builder()
                    .apiKey(apiKey)
                    .timeout(Duration.ofSeconds(60))
                    .maxRetries(2)
                    .build();
            cachedKey = apiKey;
        }
        return cachedClient;
    }
}
