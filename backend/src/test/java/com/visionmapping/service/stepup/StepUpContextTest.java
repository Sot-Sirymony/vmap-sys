package com.visionmapping.service.stepup;

import static org.assertj.core.api.Assertions.assertThat;

import com.visionmapping.web.StepUpContextInterceptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** BRD 33 D-33-12: the headers reach the service without a controller parameter. */
class StepUpContextTest {

    @AfterEach
    void noRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void outsideARequestThereIsNoCode() {
        assertThat(StepUpContext.current()).isEqualTo(new StepUpContext(null, null));
    }

    @Test
    void theInterceptorKeepsBothHeaders() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(StepUpService.HEADER, "41");
        request.addHeader(StepUpService.CONFIRM_HEADER, "yes");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        boolean goesOn = new StepUpContextInterceptor().preHandle(request, new MockHttpServletResponse(), new Object());
        request.removeHeader(StepUpService.HEADER);

        assertThat(goesOn).isTrue();
        assertThat(StepUpContext.current()).isEqualTo(new StepUpContext("41", "yes"));
    }

    @Test
    void withoutTheInterceptorTheHeadersAreReadFromTheRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(StepUpService.HEADER, "7");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        assertThat(StepUpContext.current()).isEqualTo(new StepUpContext("7", null));
    }
}
