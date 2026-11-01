package io.github.ratchet4j.core.model;

/**
 * Content-based identity of a violation. None of the values depend on line numbers.
 *
 * @param strong rule + file + anchor line + context + occurrence
 * @param weak rule + file + anchor line + occurrence
 * @param move rule + anchor line + context (survives file moves); empty when there is no anchor
 * @param scope enclosing {@code Class#method}, may be empty
 */
public record Fingerprint(String strong, String weak, String move, String scope) {}
