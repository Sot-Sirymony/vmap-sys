package com.visionmapping.web;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.http.server.PathContainer;
import org.springframework.lang.Nullable;
import org.springframework.web.util.pattern.PathPattern;

/**
 * V33 (D-33-2, BR-33-7): keeps the old {@code /api/...} paths working while the
 * controllers move to {@code /api/v1/...}.
 *
 * <p>A request on {@code /api/<x>} is passed on as {@code /api/v1/<x>} when a
 * version 1 endpoint matches that path, with the headers
 * {@code Deprecation: true} and, when a date is set, {@code Sunset}. A path
 * with no version 1 endpoint yet is passed on unchanged, so a controller that
 * has not moved keeps answering on its old path. {@code /api/health} and paths
 * already under {@code /api/v1} are never touched.
 *
 * <p>The filter runs before Spring Security (see {@code ApiVersionConfig}), so
 * the security rules, the two-factor setup check and the rate limits all see
 * the version 1 path, exactly as for a request sent there directly.
 */
public final class LegacyApiPathFilter implements Filter {

    static final String DEPRECATION_HEADER = "Deprecation";
    static final String SUNSET_HEADER = "Sunset";

    private final Supplier<List<PathPattern>> versionOnePatterns;
    private final String sunset;

    /**
     * @param versionOnePatterns the path patterns of every handler under {@code /api/v1},
     *                           read when first needed (the handler mappings are ready by then)
     * @param sunset             the HTTP date sent in {@code Sunset}; blank sends no {@code Sunset}
     */
    public LegacyApiPathFilter(Supplier<List<PathPattern>> versionOnePatterns, String sunset) {
        this.versionOnePatterns = versionOnePatterns;
        this.sunset = sunset == null ? "" : sunset.trim();
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (request instanceof HttpServletRequest http && response instanceof HttpServletResponse httpResponse) {
            String versionOnePath = versionOnePathFor(http);
            if (versionOnePath != null) {
                httpResponse.setHeader(DEPRECATION_HEADER, "true");
                if (!sunset.isEmpty()) {
                    httpResponse.setHeader(SUNSET_HEADER, sunset);
                }
                chain.doFilter(new VersionOneRequest(http, versionOnePath), response);
                return;
            }
        }
        chain.doFilter(request, response);
    }

    /** The version 1 path for an old path that has a version 1 endpoint; otherwise none. */
    private String versionOnePathFor(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (!isLegacy(path)) {
            return null;
        }
        String candidate = ApiPaths.V1 + path.substring(ApiPaths.LEGACY.length());
        PathContainer container = PathContainer.parsePath(candidate);
        return versionOnePatterns.get().stream().anyMatch(pattern -> pattern.matches(container)) ? candidate : null;
    }

    static boolean isLegacy(String path) {
        return path.startsWith(ApiPaths.LEGACY + "/")
                && !path.equals(ApiPaths.V1) && !path.startsWith(ApiPaths.V1 + "/")
                && !path.equals(ApiPaths.HEALTH);
    }

    /** The same request with its path under {@code /api/v1}. */
    private static final class VersionOneRequest extends HttpServletRequestWrapper {

        private final String path;

        VersionOneRequest(HttpServletRequest request, String path) {
            super(request);
            this.path = path;
        }

        @Override
        public String getRequestURI() {
            return getContextPath() + path;
        }

        @Override
        public String getServletPath() {
            return path;
        }

        @Override
        @Nullable
        public String getPathInfo() {
            return null;
        }

        @Override
        public StringBuffer getRequestURL() {
            StringBuffer original = super.getRequestURL();
            String uri = super.getRequestURI();
            int start = original.lastIndexOf(uri);
            StringBuffer url = new StringBuffer(start < 0 ? "" : original.substring(0, start));
            return url.append(getRequestURI());
        }
    }
}
