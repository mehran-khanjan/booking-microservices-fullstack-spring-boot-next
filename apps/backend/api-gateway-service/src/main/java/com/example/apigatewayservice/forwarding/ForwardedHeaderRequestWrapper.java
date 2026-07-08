package com.example.apigatewayservice.forwarding;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Synthesizes missing standard forwarded headers ({@code X-Forwarded-For}, {@code
 * X-Forwarded-Proto}, {@code X-Forwarded-Host}, and {@code Forwarded}) based on the actual request
 * details (remote address, scheme, host).
 *
 * <p>Existing values are never overwritten; this wrapper only adds headers that are absent from the
 * original request.
 */
public final class ForwardedHeaderRequestWrapper extends HttpServletRequestWrapper {

  private static final Logger log = LoggerFactory.getLogger(ForwardedHeaderRequestWrapper.class);

  private final Map<String, String> synthetic;

  private ForwardedHeaderRequestWrapper(HttpServletRequest request, Map<String, String> synthetic) {
    super(request);
    this.synthetic = synthetic;
    if (log.isDebugEnabled()) {
      log.debug("ForwardedHeaderRequestWrapper created with synthetic headers: {}", synthetic);
    }
  }

  /**
   * Returns a wrapper around {@code request} exposing synthesized forwarded headers, or the
   * original request unchanged if nothing needs synthesizing.
   *
   * @param request the original HTTP request
   * @return a wrapped request with additional headers, or the original request
   */
  public static HttpServletRequest wrap(HttpServletRequest request) {
    String clientIp = request.getRemoteAddr();
    String scheme = request.getScheme();
    String host = request.getServerName();

    Map<String, String> synthetic = new LinkedHashMap<>();

    // --- Check and synthesise each header ---
    boolean addedFor = putIfAbsentWithLogging(synthetic, request, "X-Forwarded-For", clientIp);
    boolean addedProto = putIfAbsentWithLogging(synthetic, request, "X-Forwarded-Proto", scheme);
    boolean addedHost = putIfAbsentWithLogging(synthetic, request, "X-Forwarded-Host", host);
    boolean addedForwarded =
        putIfAbsentWithLogging(
            synthetic,
            request,
            "Forwarded",
            String.format("for=%s;proto=%s;host=%s", clientIp, scheme, host));

    // --- Summarise what happened ---
    if (synthetic.isEmpty()) {
      log.info(
          "All forwarded headers already present – no synthesis needed. "
              + "X-Forwarded-For: {}, X-Forwarded-Proto: {}, X-Forwarded-Host: {}, Forwarded: {}",
          request.getHeader("X-Forwarded-For"),
          request.getHeader("X-Forwarded-Proto"),
          request.getHeader("X-Forwarded-Host"),
          request.getHeader("Forwarded"));
      return request;
    } else {
      log.info(
          "Synthesised missing forwarded headers: {} (added {})",
          synthetic,
          String.join(
                  ", ",
                  addedFor ? "X-Forwarded-For" : "",
                  addedProto ? "X-Forwarded-Proto" : "",
                  addedHost ? "X-Forwarded-Host" : "",
                  addedForwarded ? "Forwarded" : "")
              .replaceAll("^, |, $", ""));
      return new ForwardedHeaderRequestWrapper(request, synthetic);
    }
  }

  /**
   * Attempts to put the header only if absent. Logs whether the header was already present or newly
   * synthesised. Returns true if the header was added (i.e., synthesised).
   */
  private static boolean putIfAbsentWithLogging(
      Map<String, String> target, HttpServletRequest original, String name, String value) {
    String existing = original.getHeader(name);
    if (existing != null && !existing.isEmpty()) {
      log.info("Header '{}' already present with value: '{}' – using existing", name, existing);
      return false;
    } else {
      if (value != null) {
        target.put(name, value);
        log.info("Header '{}' not present – synthesising with value: '{}'", name, value);
        return true;
      } else {
        log.warn("Header '{}' not present and no value available to synthesise – skipping", name);
        return false;
      }
    }
  }

  @Override
  public String getHeader(String name) {
    String existing = super.getHeader(name);
    if (existing != null && !existing.isEmpty()) {
      return existing;
    }
    return synthetic.entrySet().stream()
        .filter(e -> e.getKey().equalsIgnoreCase(name))
        .map(Map.Entry::getValue)
        .findFirst()
        .orElse(null);
  }

  @Override
  public Enumeration<String> getHeaders(String name) {
    String value = getHeader(name);
    return value == null ? Collections.emptyEnumeration() : Collections.enumeration(List.of(value));
  }

  @Override
  public Enumeration<String> getHeaderNames() {
    // Must include synthesized names too, or code that enumerates headers
    // (rather than calling getHeader(name) directly) silently misses them.
    Set<String> names = new LinkedHashSet<>(Collections.list(super.getHeaderNames()));
    names.addAll(synthetic.keySet());
    return Collections.enumeration(names);
  }
}
