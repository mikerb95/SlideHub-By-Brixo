package com.brixo.slidehub.ui.controller;

import com.brixo.slidehub.ui.model.User;
import com.brixo.slidehub.ui.repository.UserRepository;
import com.brixo.slidehub.ui.service.MeetingService;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/presentations/{presentationId}/meeting")
public class MeetingController {

    private final MeetingService meetingService;
    private final UserRepository userRepository;

    public MeetingController(MeetingService meetingService, UserRepository userRepository) {
        this.meetingService = meetingService;
        this.userRepository = userRepository;
    }

    record AddParticipantRequest(String displayName, Boolean presenter) {
    }

    record AssignSlideRequest(int slideNumber, String participantId) {
    }

    record JoinRequest(String joinToken, String participantId, String customName) {
    }

    record HelpRequest(String joinToken, String participantToken) {
    }

    @GetMapping("/participants")
    public ResponseEntity<?> listParticipants(@PathVariable String presentationId) {
        return ResponseEntity.ok(meetingService.listParticipants(presentationId));
    }

    @PostMapping("/participants")
    public ResponseEntity<?> addParticipant(@PathVariable String presentationId,
            @RequestBody AddParticipantRequest request,
            Authentication authentication) {
        try {
            String userId = resolveUser(authentication).getId();
            boolean presenter = request.presenter() == null || request.presenter();
            return ResponseEntity
                    .ok(meetingService.addParticipant(userId, presentationId, request.displayName(), presenter));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @GetMapping("/assignments")
    public ResponseEntity<?> listAssignments(@PathVariable String presentationId) {
        return ResponseEntity.ok(meetingService.listAssignments(presentationId));
    }

    @PostMapping("/assignments")
    public ResponseEntity<?> assignSlide(@PathVariable String presentationId,
            @RequestBody AssignSlideRequest request,
            Authentication authentication) {
        try {
            String userId = resolveUser(authentication).getId();
            return ResponseEntity.ok(
                    meetingService.assignSlide(userId, presentationId, request.slideNumber(), request.participantId()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping("/session/start")
    public ResponseEntity<?> startSession(@PathVariable String presentationId,
            Authentication authentication) {
        try {
            String userId = resolveUser(authentication).getId();
            return ResponseEntity.ok(meetingService.startSession(userId, presentationId));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @GetMapping("/session/active")
    public ResponseEntity<?> activeSession(@PathVariable String presentationId,
            Authentication authentication) {
        try {
            String userId = resolveUser(authentication).getId();
            return meetingService.getActiveSession(userId, presentationId)
                    .<ResponseEntity<?>>map(ResponseEntity::ok)
                    .orElse(ResponseEntity.noContent().build());
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @GetMapping("/join-options")
    public ResponseEntity<?> joinOptions(@PathVariable String presentationId,
            @RequestParam("joinToken") String joinToken) {
        try {
            return ResponseEntity.ok(meetingService.getJoinOptions(presentationId, joinToken));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping("/join")
    public ResponseEntity<?> join(@PathVariable String presentationId,
            @RequestBody JoinRequest request) {
        try {
            return ResponseEntity.ok(meetingService.joinSession(
                    presentationId,
                    request.joinToken(),
                    request.participantId(),
                    request.customName()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @GetMapping("/assignment-check")
    public ResponseEntity<?> checkAssignment(@PathVariable String presentationId,
            @RequestParam("joinToken") String joinToken,
            @RequestParam("participantToken") String participantToken,
            @RequestParam("slideNumber") int slideNumber) {
        try {
            return ResponseEntity.ok(meetingService.checkSlideAssignment(
                    presentationId,
                    joinToken,
                    participantToken,
                    slideNumber));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping("/help")
    public ResponseEntity<?> requestHelp(@PathVariable String presentationId,
            @RequestBody HelpRequest request) {
        try {
            int recipients = meetingService.requestHelp(
                    presentationId,
                    request.joinToken(),
                    request.participantToken());
            return ResponseEntity.ok(Map.of("success", true, "recipients", recipients));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping(value = "/assist/audio", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> assistFromAudio(
            @PathVariable String presentationId,
            @RequestParam("joinToken") String joinToken,
            @RequestParam("participantToken") String participantToken,
            @RequestParam(value = "slideNumber", defaultValue = "1") int slideNumber,
            @RequestParam(value = "slideContext", required = false) String slideContext,
            @RequestPart("audio") MultipartFile audio) {
        try {
            if (audio == null || audio.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Audio requerido"));
            }
            return ResponseEntity.ok(meetingService.assistFromAudio(
                    presentationId,
                    joinToken,
                    participantToken,
                    slideNumber,
                    slideContext,
                    audio.getBytes(),
                    audio.getOriginalFilename(),
                    audio.getContentType()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(Map.of("error", ex.getMessage()));
        }
    }

    private User resolveUser(Authentication authentication) {
        if (authentication == null) {
            throw new IllegalArgumentException("Autenticación requerida.");
        }
        String identifier = authentication.getName();
        Optional<User> user = userRepository.findByEmail(identifier);
        return user.orElseThrow(() -> new IllegalArgumentException("Usuario autenticado no encontrado."));
    }
}
