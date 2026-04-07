package com.brixo.slidehub.ui.service;

import com.brixo.slidehub.ui.repository.PresentationParticipantRepository;
import com.brixo.slidehub.ui.repository.PresentationRepository;
import com.brixo.slidehub.ui.repository.PresentationSessionRepository;
import com.brixo.slidehub.ui.repository.SessionMemberRepository;
import com.brixo.slidehub.ui.repository.SlideAssignmentRepository;
import com.brixo.slidehub.ui.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AccountDeletionService {

    private static final Logger log = LoggerFactory.getLogger(AccountDeletionService.class);

    private final UserRepository userRepository;
    private final PresentationRepository presentationRepository;
    private final SessionMemberRepository sessionMemberRepository;
    private final SlideAssignmentRepository slideAssignmentRepository;
    private final PresentationParticipantRepository presentationParticipantRepository;
    private final PresentationSessionRepository presentationSessionRepository;

    public AccountDeletionService(UserRepository userRepository,
            PresentationRepository presentationRepository,
            SessionMemberRepository sessionMemberRepository,
            SlideAssignmentRepository slideAssignmentRepository,
            PresentationParticipantRepository presentationParticipantRepository,
            PresentationSessionRepository presentationSessionRepository) {
        this.userRepository = userRepository;
        this.presentationRepository = presentationRepository;
        this.sessionMemberRepository = sessionMemberRepository;
        this.slideAssignmentRepository = slideAssignmentRepository;
        this.presentationParticipantRepository = presentationParticipantRepository;
        this.presentationSessionRepository = presentationSessionRepository;
    }

    @Transactional
    public void deleteAccount(String userId) {
        if (!userRepository.existsById(userId)) {
            log.warn("Solicitud de borrado de cuenta inexistente: {}", userId);
            return;
        }

        List<String> presentationIds = presentationRepository.findIdsByUserId(userId);

        if (!presentationIds.isEmpty()) {
            sessionMemberRepository.deleteBySessionPresentationIdIn(presentationIds);
            slideAssignmentRepository.deleteByPresentationIdIn(presentationIds);
            presentationParticipantRepository.deleteByPresentationIdIn(presentationIds);
            presentationSessionRepository.deleteByPresentationIdIn(presentationIds);
            presentationRepository.deleteByUserId(userId);
        }

        userRepository.deleteById(userId);
        log.info("Cuenta eliminada correctamente para userId={}", userId);
    }
}
