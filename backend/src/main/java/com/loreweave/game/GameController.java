package com.loreweave.game;

import com.loreweave.game.dto.Dtos.*;
import com.loreweave.llm.GameMasterService;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.validation.Valid;
import java.util.List;

/**
 * The signed-in user's id is jwt.getSubject(). Sessions are scoped to it — the ownerId is
 * always derived from the token, never from the request body.
 */
@RestController
@RequestMapping("/api/sessions")
public class GameController {

    private final GameMasterService gameMaster;
    private final SessionService sessions;

    public GameController(GameMasterService gameMaster, SessionService sessions) {
        this.gameMaster = gameMaster;
        this.sessions = sessions;
    }

    @PostMapping
    public SessionDto create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateSessionRequest req) {
        return sessions.create(jwt.getSubject(), req.genre(), req.premise());
    }

    @GetMapping
    public List<SessionDto> list(@AuthenticationPrincipal Jwt jwt) {
        return sessions.listForOwner(jwt.getSubject());
    }

    /** Dashboard aggregate. Declared before {@code /{id}} — a literal path outranks the variable anyway. */
    @GetMapping("/stats")
    public DashboardDto stats(@AuthenticationPrincipal Jwt jwt) {
        return sessions.dashboard(jwt.getSubject());
    }

    @GetMapping("/{id}")
    public SessionDetailDto detail(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
        return sessions.detail(jwt.getSubject(), id);
    }

    @GetMapping("/{id}/world")
    public WorldStateDto world(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
        return sessions.worldState(jwt.getSubject(), id);
    }

    /** Take a turn — streams narration token-by-token via SSE, emits a graph delta at the end. */
    @PostMapping(value = "/{id}/turns", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter takeTurn(@AuthenticationPrincipal Jwt jwt, @PathVariable String id,
                               @Valid @RequestBody TurnRequest req) {
        sessions.owned(id, jwt.getSubject());          // 404 if it isn't the caller's adventure
        SseEmitter emitter = new SseEmitter(120_000L);
        gameMaster.streamTurn(id, req.action(), emitter);
        return emitter;
    }
}
