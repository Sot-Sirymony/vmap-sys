package com.visionmapping.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.ProblemDetailJacksonMixin;
import org.springframework.stereotype.Component;

/**
 * V33 (D-33-13): writes a {@link ProblemDetail} straight to the response, for
 * the security filters that answer before any controller or
 * {@code @RestControllerAdvice} runs. The output is the same JSON as an error
 * from the global handler: the extensions sit at the top level, not under
 * {@code properties}.
 */
@Component
public class ProblemWriter {

    private final ObjectMapper objectMapper;

    public ProblemWriter(ObjectMapper objectMapper) {
        // The application's mapper already has the mix-in; adding it to a copy
        // makes the output the same even when a test passes a plain mapper.
        this.objectMapper = objectMapper.copy().addMixIn(ProblemDetail.class, ProblemDetailJacksonMixin.class);
    }

    public void write(HttpServletResponse response, ProblemDetail problem) throws IOException {
        response.setStatus(problem.getStatus());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
