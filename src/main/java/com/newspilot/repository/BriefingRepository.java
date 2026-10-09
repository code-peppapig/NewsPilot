package com.newspilot.repository;

import com.newspilot.entity.Briefing;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

/**
 * 历史简报数据访问层。
 */
public interface BriefingRepository extends JpaRepository<Briefing, Long> {

    List<Briefing> findByUserIdOrderByBriefingDateDesc(Long userId);

    boolean existsByUserIdAndBriefingDate(Long userId, LocalDate briefingDate);
}
