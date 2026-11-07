package com.visionmapping.config;

import com.visionmapping.service.ai.PlanSessionContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** V11 (PB-FR-36): forgets the plan session key before and after every request (see PlanSessionContext). */
@Component
public class PlanSessionContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        PlanSessionContext.clear();
        try {
            chain.doFilter(request, response);
        } finally {
            PlanSessionContext.clear();
        }
    }
}
