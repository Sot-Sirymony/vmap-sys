package com.visionmapping.service.support;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.NotFoundException;
import com.anthropic.errors.PermissionDeniedException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.errors.UnauthorizedException;
import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * V9: checks a Claude API key by looking up the chosen model through the
 * Models API. That call is free, and it proves both that the key works and
 * that the model is available to it.
 */
@Component
public class ClaudeKeyChecker {

    public record Result(boolean ok, String message) {
    }

    public Result check(String apiKey, String model) {
        AnthropicClient client = AnthropicOkHttpClient.builder()
                .apiKey(apiKey)
                .timeout(Duration.ofSeconds(15))
                .maxRetries(1)
                .build();
        try {
            String name = client.models().retrieve(model).displayName();
            return new Result(true, "Connected. " + name + " is available.");
        } catch (UnauthorizedException exception) {
            return new Result(false, "The API key was not accepted. Check it and save it again.");
        } catch (PermissionDeniedException exception) {
            return new Result(false, "This API key is not allowed to use the Claude API.");
        } catch (NotFoundException exception) {
            return new Result(false, "The key works, but the model " + model + " is not available to it.");
        } catch (RateLimitException exception) {
            return new Result(false, "The Claude API is busy right now. Try again in a minute.");
        } catch (AnthropicServiceException exception) {
            return new Result(false, "The Claude API answered with an error (" + exception.statusCode() + ").");
        } catch (AnthropicIoException exception) {
            return new Result(false, "Could not reach the Claude API. Check the server's internet connection.");
        } finally {
            client.close();
        }
    }
}
