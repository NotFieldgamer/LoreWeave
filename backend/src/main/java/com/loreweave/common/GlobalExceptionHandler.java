package com.loreweave.common;

import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(UnsupportedOperationException.class)
    public ResponseEntity<Map<String, Object>> notImplemented(UnsupportedOperationException e) {
        String msg = e.getMessage() == null ? "Not implemented yet" : e.getMessage();
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
            .body(Map.of("error", msg, "at", Instant.now().toString()));
    }

    /** Honour deliberate status signals (e.g. 404 for a missing/not-owned session) instead of 500-ing them. */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> status(ResponseStatusException e) {
        String msg = e.getReason() == null ? "Request failed" : e.getReason();
        return ResponseEntity.status(e.getStatusCode())
            .body(Map.of("error", msg, "at", Instant.now().toString()));
    }

    /**
     * The client's connection dropped mid-request. On an SSE turn, Tomcat's async machinery
     * re-dispatches the broken-pipe error through the DispatcherServlet — as a plain
     * {@code IOException} (Tomcat's {@code ClientAbortException}, or Spring's
     * {@code AsyncRequestNotUsableException}, both extend {@link IOException}). There is nothing left
     * to deliver, and a JSON body has no converter for {@code text/event-stream}, so we write nothing
     * (a {@code void} handler = "resolved, empty response") rather than let it surface as a bogus 500.
     */
    @ExceptionHandler(IOException.class)
    public void clientDisconnected(IOException e) {
        log.debug("Client connection closed mid-request: {}", e.toString());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> generic(Exception e, HttpServletResponse response) {
        // Once the response is committed (e.g. an SSE stream is already underway) we can't write a
        // JSON error body onto it — text/event-stream has no Map converter, and the bytes are gone.
        // Returning null tells Spring the exception is resolved with nothing more to write.
        if (response.isCommitted()) {
            log.debug("Unhandled exception after the response was committed: {}", e.toString());
            return null;
        }
        log.error("Unhandled exception", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(Map.of("error", "Something went wrong. Try again.", "at", Instant.now().toString()));
    }
}
