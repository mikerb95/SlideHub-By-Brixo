package com.brixo.slidehub.ui.service;

import com.brixo.slidehub.ui.repository.PresentationParticipantRepository;
import com.brixo.slidehub.ui.repository.PresentationRepository;
import com.brixo.slidehub.ui.repository.PresentationSessionRepository;
import com.brixo.slidehub.ui.repository.SessionMemberRepository;
import com.brixo.slidehub.ui.repository.SlideAssignmentRepository;
import com.brixo.slidehub.ui.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AccountDeletionServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PresentationRepository presentationRepository;

    @Mock
    private SessionMemberRepository sessionMemberRepository;

    @Mock
    private SlideAssignmentRepository slideAssignmentRepository;

    @Mock
    private PresentationParticipantRepository presentationParticipantRepository;

    @Mock
    private PresentationSessionRepository presentationSessionRepository;

    @InjectMocks
    private AccountDeletionService accountDeletionService;

    @Test
    void deleteAccount_withPresentations_deletesInSafeOrder() {
        String userId = "user-1";
        List<String> presentationIds = List.of("p1", "p2");

        given(userRepository.existsById(userId)).willReturn(true);
        given(presentationRepository.findIdsByUserId(userId)).willReturn(presentationIds);

        accountDeletionService.deleteAccount(userId);

        InOrder inOrder = inOrder(
                sessionMemberRepository,
                slideAssignmentRepository,
                presentationParticipantRepository,
                presentationSessionRepository,
                presentationRepository,
                userRepository);

        inOrder.verify(sessionMemberRepository).deleteBySessionPresentationIdIn(presentationIds);
        inOrder.verify(slideAssignmentRepository).deleteByPresentationIdIn(presentationIds);
        inOrder.verify(presentationParticipantRepository).deleteByPresentationIdIn(presentationIds);
        inOrder.verify(presentationSessionRepository).deleteByPresentationIdIn(presentationIds);
        inOrder.verify(presentationRepository).deleteByUserId(userId);
        inOrder.verify(userRepository).deleteById(userId);
    }

    @Test
    void deleteAccount_withoutPresentations_deletesOnlyUser() {
        String userId = "user-2";

        given(userRepository.existsById(userId)).willReturn(true);
        given(presentationRepository.findIdsByUserId(userId)).willReturn(List.of());

        accountDeletionService.deleteAccount(userId);

        verify(sessionMemberRepository, never()).deleteBySessionPresentationIdIn(anyList());
        verify(slideAssignmentRepository, never()).deleteByPresentationIdIn(anyList());
        verify(presentationParticipantRepository, never()).deleteByPresentationIdIn(anyList());
        verify(presentationSessionRepository, never()).deleteByPresentationIdIn(anyList());
        verify(presentationRepository, never()).deleteByUserId(userId);
        verify(userRepository).deleteById(userId);
    }
}
