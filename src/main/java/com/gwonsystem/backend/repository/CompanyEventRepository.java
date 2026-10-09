package com.gwonsystem.backend.repository;
import com.gwonsystem.backend.entity.CompanyEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface CompanyEventRepository extends JpaRepository<CompanyEvent,Long> {
 List<CompanyEvent> findAllByOrderByStartDateDescIdDesc();
}
