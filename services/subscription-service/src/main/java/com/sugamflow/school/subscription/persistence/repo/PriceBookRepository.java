package com.sugamflow.school.subscription.persistence.repo;

import com.sugamflow.school.subscription.persistence.entity.PriceBookEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PriceBookRepository extends JpaRepository<PriceBookEntity, String> {
  List<PriceBookEntity> findAllByOrderByCodeAsc();

  Optional<PriceBookEntity> findByCodeIgnoreCase(String code);

  Optional<PriceBookEntity> findFirstByDefaultBookTrueAndActiveTrue();
}
