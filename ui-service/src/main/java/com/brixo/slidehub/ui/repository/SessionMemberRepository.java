package com.brixo.slidehub.ui.repository;

import com.brixo.slidehub.ui.model.SessionMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SessionMemberRepository extends JpaRepository<SessionMember, String> {

    Optional<SessionMember> findBySessionIdAndParticipantId(String sessionId, String participantId);

    Optional<SessionMember> findByParticipantTokenAndActiveTrue(String participantToken);

    List<SessionMember> findBySessionIdAndActiveTrue(String sessionId);
}
