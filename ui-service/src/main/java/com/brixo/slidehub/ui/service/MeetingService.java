package com.brixo.slidehub.ui.service;

import com.brixo.slidehub.ui.model.Presentation;
import com.brixo.slidehub.ui.model.PresentationParticipant;
import com.brixo.slidehub.ui.model.PresentationSession;
import com.brixo.slidehub.ui.model.SessionMember;
import com.brixo.slidehub.ui.model.SlideAssignment;
import com.brixo.slidehub.ui.repository.PresentationParticipantRepository;
import com.brixo.slidehub.ui.repository.PresentationRepository;
import com.brixo.slidehub.ui.repository.PresentationSessionRepository;
import com.brixo.slidehub.ui.repository.SessionMemberRepository;
import com.brixo.slidehub.ui.repository.SlideAssignmentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class MeetingService {

    private final PresentationRepository presentationRepository;
    private final PresentationParticipantRepository participantRepository;
    private final SlideAssignmentRepository assignmentRepository;
    private final PresentationSessionRepository sessionRepository;
    private final SessionMemberRepository memberRepository;
    private final HapticBridgeService hapticBridgeService;
    private final AssistBridgeService assistBridgeService;

    @Value("${slidehub.base-url:http://localhost:8080}")
    private String baseUrl;

    public MeetingService(PresentationRepository presentationRepository,
            PresentationParticipantRepository participantRepository,
            SlideAssignmentRepository assignmentRepository,
            PresentationSessionRepository sessionRepository,
            SessionMemberRepository memberRepository,
            HapticBridgeService hapticBridgeService,
            AssistBridgeService assistBridgeService) {
        this.presentationRepository = presentationRepository;
        this.participantRepository = participantRepository;
        this.assignmentRepository = assignmentRepository;
        this.sessionRepository = sessionRepository;
        this.memberRepository = memberRepository;
        this.hapticBridgeService = hapticBridgeService;
        this.assistBridgeService = assistBridgeService;
    }

    public record ParticipantItem(String id, String displayName, boolean presenter) {
    }

    public record AssignmentItem(int slideNumber, String participantId, String participantName) {
    }

    public record SessionInfo(String sessionId, String joinToken, String joinUrl) {
    }

    public record JoinResult(String participantToken, String displayName) {
    }

    public record AssignmentCheck(boolean vibrate, String pattern, String message) {
    }

    public Optional<SessionInfo> getActiveSession(String userId, String presentationId) {
        requireOwnedPresentation(userId, presentationId);
        return sessionRepository.findByPresentationIdAndActiveTrue(presentationId)
                .map(session -> new SessionInfo(
                        session.getId(),
                        session.getJoinToken(),
                        buildJoinUrl(presentationId, session.getJoinToken())));
    }

    @Transactional
    public ParticipantItem addParticipant(String userId, String presentationId, String displayName, boolean presenter) {
        Presentation presentation = requireOwnedPresentation(userId, presentationId);
        String normalized = normalizeName(displayName);
        if (participantRepository.existsByPresentationIdAndDisplayNameIgnoreCase(presentationId, normalized)) {
            throw new IllegalArgumentException("Ya existe un participante con ese nombre.");
        }

        PresentationParticipant participant = new PresentationParticipant();
        participant.setId(UUID.randomUUID().toString());
        participant.setPresentation(presentation);
        participant.setDisplayName(normalized);
        participant.setPresenter(presenter);
        participant.setCreatedAt(LocalDateTime.now());

        PresentationParticipant saved = participantRepository.save(participant);
        return new ParticipantItem(saved.getId(), saved.getDisplayName(), saved.isPresenter());
    }

    public List<ParticipantItem> listParticipants(String presentationId) {
        return participantRepository.findByPresentationIdOrderByDisplayNameAsc(presentationId).stream()
                .map(participant -> new ParticipantItem(
                        participant.getId(),
                        participant.getDisplayName(),
                        participant.isPresenter()))
                .toList();
    }

    @Transactional
    public AssignmentItem assignSlide(String userId, String presentationId, int slideNumber, String participantId) {
        requireOwnedPresentation(userId, presentationId);
        if (slideNumber < 1) {
            throw new IllegalArgumentException("slideNumber debe ser >= 1");
        }

        PresentationParticipant participant = participantRepository
                .findByIdAndPresentationId(participantId, presentationId)
                .orElseThrow(() -> new IllegalArgumentException("Participante no encontrado para esa presentación."));

        SlideAssignment assignment = assignmentRepository
                .findByPresentationIdAndSlideNumber(presentationId, slideNumber)
                .orElseGet(() -> {
                    SlideAssignment newAssignment = new SlideAssignment();
                    newAssignment.setId(UUID.randomUUID().toString());
                    newAssignment.setPresentation(participant.getPresentation());
                    newAssignment.setSlideNumber(slideNumber);
                    newAssignment.setCreatedAt(LocalDateTime.now());
                    return newAssignment;
                });

        assignment.setParticipant(participant);
        SlideAssignment saved = assignmentRepository.save(assignment);

        return new AssignmentItem(saved.getSlideNumber(),
                saved.getParticipant().getId(),
                saved.getParticipant().getDisplayName());
    }

    public List<AssignmentItem> listAssignments(String presentationId) {
        return assignmentRepository.findByPresentationIdOrderBySlideNumberAsc(presentationId).stream()
                .map(assignment -> new AssignmentItem(
                        assignment.getSlideNumber(),
                        assignment.getParticipant().getId(),
                        assignment.getParticipant().getDisplayName()))
                .toList();
    }

    @Transactional
    public SessionInfo startSession(String userId, String presentationId) {
        requireOwnedPresentation(userId, presentationId);

        sessionRepository.findByPresentationIdAndActiveTrue(presentationId).ifPresent(existing -> {
            existing.setActive(false);
            existing.setUpdatedAt(LocalDateTime.now());
            sessionRepository.save(existing);
        });

        String joinToken = UUID.randomUUID().toString().replace("-", "");
        Presentation presentation = presentationRepository.findById(presentationId)
                .orElseThrow(() -> new IllegalArgumentException("Presentación no encontrada."));

        PresentationSession session = new PresentationSession();
        session.setId(UUID.randomUUID().toString());
        session.setPresentation(presentation);
        session.setJoinToken(joinToken);
        session.setActive(true);
        session.setCreatedAt(LocalDateTime.now());
        session.setUpdatedAt(LocalDateTime.now());

        PresentationSession saved = sessionRepository.save(session);
        return new SessionInfo(saved.getId(), joinToken, buildJoinUrl(presentationId, joinToken));
    }

    public Map<String, Object> getJoinOptions(String presentationId, String joinToken) {
        PresentationSession session = requireActiveSession(presentationId, joinToken);
        List<ParticipantItem> participants = listParticipants(presentationId);
        return Map.of(
                "sessionId", session.getId(),
                "presentationId", presentationId,
                "participants", participants);
    }

    @Transactional
    public JoinResult joinSession(String presentationId, String joinToken, String participantId, String customName) {
        PresentationSession session = requireActiveSession(presentationId, joinToken);

        PresentationParticipant participant = resolveJoinParticipant(session, participantId, customName);

        SessionMember member = memberRepository
                .findBySessionIdAndParticipantId(session.getId(), participant.getId())
                .orElseGet(() -> {
                    SessionMember m = new SessionMember();
                    m.setId(UUID.randomUUID().toString());
                    m.setSession(session);
                    m.setParticipant(participant);
                    m.setParticipantToken(UUID.randomUUID().toString().replace("-", ""));
                    m.setJoinedAt(LocalDateTime.now());
                    return m;
                });

        member.setActive(true);
        member.setLastSeenAt(LocalDateTime.now());

        SessionMember saved = memberRepository.save(member);
        return new JoinResult(saved.getParticipantToken(), saved.getParticipant().getDisplayName());
    }

    public AssignmentCheck checkSlideAssignment(String presentationId,
            String joinToken,
            String participantToken,
            int slideNumber) {
        PresentationSession session = requireActiveSession(presentationId, joinToken);
        SessionMember member = requireActiveMember(session.getId(), participantToken);

        Optional<SlideAssignment> assignment = assignmentRepository
                .findByPresentationIdAndSlideNumber(presentationId, slideNumber);

        if (assignment.isEmpty()) {
            return new AssignmentCheck(false, "single", "");
        }

        boolean isResponsible = assignment.get().getParticipant().getId().equals(member.getParticipant().getId());
        if (!isResponsible) {
            return new AssignmentCheck(false, "single", "");
        }

        return new AssignmentCheck(true, "single", "Tu slide asignada está activa");
    }

    @Transactional
    public int requestHelp(String presentationId, String joinToken, String participantToken) {
        PresentationSession session = requireActiveSession(presentationId, joinToken);
        SessionMember sender = requireActiveMember(session.getId(), participantToken);

        List<SessionMember> recipients = memberRepository.findBySessionIdAndActiveTrue(session.getId()).stream()
                .filter(member -> !member.getParticipantToken().equals(participantToken))
                .sorted(Comparator.comparing(SessionMember::getJoinedAt))
                .toList();

        String message = "Solicitud de ayuda de %s".formatted(sender.getParticipant().getDisplayName());
        recipients.forEach(member -> hapticBridgeService.publishTriple(member.getParticipantToken(), message));
        return recipients.size();
    }

    @Transactional
    public Map<String, Object> assistFromAudio(String presentationId,
            String joinToken,
            String participantToken,
            int slideNumber,
            String slideContext,
            byte[] audioBytes,
            String filename,
            String contentType) {
        PresentationSession session = requireActiveSession(presentationId, joinToken);
        SessionMember sender = requireActiveMember(session.getId(), participantToken);

        Map<String, Object> response = assistBridgeService.processAudio(
                audioBytes,
                filename,
                contentType,
                session.getPresentation().getRepoUrl(),
                slideNumber,
                slideContext);

        Object success = response.get("success");
        boolean ok = success instanceof Boolean b && b;
        if (ok) {
            String answer = String.valueOf(response.getOrDefault("answer", ""));
            String senderName = sender.getParticipant().getDisplayName();
            String broadcastMessage = "IA para " + senderName + ": " + trimMessage(answer, 220);
            memberRepository.findBySessionIdAndActiveTrue(session.getId())
                    .forEach(member -> hapticBridgeService.publishSingle(member.getParticipantToken(),
                            broadcastMessage));
        }

        return response;
    }

    private Presentation requireOwnedPresentation(String userId, String presentationId) {
        return presentationRepository.findByIdAndUserId(presentationId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Presentación no encontrada o sin permisos."));
    }

    private PresentationSession requireActiveSession(String presentationId, String joinToken) {
        return sessionRepository.findByPresentationIdAndJoinTokenAndActiveTrue(presentationId, joinToken)
                .orElseThrow(() -> new IllegalArgumentException("Sesión inválida o expirada."));
    }

    private SessionMember requireActiveMember(String sessionId, String participantToken) {
        SessionMember member = memberRepository.findByParticipantTokenAndActiveTrue(participantToken)
                .orElseThrow(() -> new IllegalArgumentException("participantToken inválido."));
        if (!member.getSession().getId().equals(sessionId)) {
            throw new IllegalArgumentException("participantToken no pertenece a la sesión.");
        }
        member.setLastSeenAt(LocalDateTime.now());
        return memberRepository.save(member);
    }

    private PresentationParticipant resolveJoinParticipant(PresentationSession session,
            String participantId,
            String customName) {
        if (participantId != null && !participantId.isBlank()) {
            return participantRepository.findByIdAndPresentationId(participantId, session.getPresentation().getId())
                    .orElseThrow(() -> new IllegalArgumentException("Participante inválido para esta presentación."));
        }

        String name = normalizeName(customName);
        if (participantRepository.existsByPresentationIdAndDisplayNameIgnoreCase(
                session.getPresentation().getId(), name)) {
            return participantRepository.findByPresentationIdOrderByDisplayNameAsc(session.getPresentation().getId())
                    .stream()
                    .filter(p -> p.getDisplayName().equalsIgnoreCase(name))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Participante no encontrado."));
        }

        PresentationParticipant participant = new PresentationParticipant();
        participant.setId(UUID.randomUUID().toString());
        participant.setPresentation(session.getPresentation());
        participant.setDisplayName(name);
        participant.setPresenter(true);
        participant.setCreatedAt(LocalDateTime.now());
        return participantRepository.save(participant);
    }

    private String normalizeName(String rawName) {
        if (rawName == null) {
            throw new IllegalArgumentException("displayName es obligatorio.");
        }
        String normalized = rawName.trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("displayName es obligatorio.");
        }
        if (normalized.length() > 120) {
            throw new IllegalArgumentException("displayName excede 120 caracteres.");
        }
        return normalized;
    }

    private String buildJoinUrl(String presentationId, String joinToken) {
        return "%s/remote?presentationId=%s&joinToken=%s".formatted(baseUrl, presentationId, joinToken);
    }

    private String trimMessage(String raw, int maxLength) {
        if (raw == null) {
            return "";
        }
        if (raw.length() <= maxLength) {
            return raw;
        }
        return raw.substring(0, maxLength - 1) + "…";
    }
}
