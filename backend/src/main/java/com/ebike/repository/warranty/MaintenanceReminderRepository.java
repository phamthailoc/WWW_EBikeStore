package com.ebike.repository.warranty;

import com.ebike.model.entity.warranty.MaintenanceReminder;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MaintenanceReminderRepository extends JpaRepository<MaintenanceReminder, Long> {

    List<MaintenanceReminder> findByUnitIdIn(Collection<Long> unitIds);
}
