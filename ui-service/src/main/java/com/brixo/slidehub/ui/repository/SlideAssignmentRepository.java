package com.brixo.slidehub.ui.repository;

import com.brixo.slidehub.ui.model.SlideAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SlideAssignmentRepository extends JpaRepository<SlideAssignment, String> {

    List<SlideAssignment> findByPresentationIdOrderBySlideNumberAsc(String presentationId);

    Optional<SlideAssignment> findByPresentationIdAndSlideNumber(String presentationId, int slideNumber);
}
