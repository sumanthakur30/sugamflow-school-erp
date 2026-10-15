package com.sugamflow.school.student.persistence.repo;

import com.sugamflow.school.student.persistence.entity.StudentDeskNoteEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentDeskNoteRepository extends JpaRepository<StudentDeskNoteEntity, UUID> {

  List<StudentDeskNoteEntity> findByOrganizationIdAndStudentIdAndKindOrderByCreatedAtDesc(
      String organizationId, UUID studentId, String kind);
}
