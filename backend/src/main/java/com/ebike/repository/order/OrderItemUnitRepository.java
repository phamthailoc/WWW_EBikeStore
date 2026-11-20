package com.ebike.repository.order;

import com.ebike.model.entity.order.OrderItemUnit;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderItemUnitRepository extends JpaRepository<OrderItemUnit, Long> {

    List<OrderItemUnit> findByFrameNumberIn(Collection<String> frameNumbers);
}
