package com.ebike.repository.stock;

import com.ebike.model.entity.stock.StockReceipt;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockReceiptRepository extends JpaRepository<StockReceipt, Long> {

    /** Newest first; {@code pattern} is a lower-case LIKE pattern on the supplier name ("%" for all), {@code beforeId} the last id of the previous page. */
    @Query("select r from StockReceipt r left join fetch r.createdBy "
        + "where r.id < :beforeId and lower(r.supplierName) like :pattern order by r.id desc")
    List<StockReceipt> search(@Param("beforeId") Long beforeId, @Param("pattern") String pattern, Pageable pageable);

    @Query("select r from StockReceipt r left join fetch r.createdBy where r.id = :id")
    Optional<StockReceipt> findDetailById(@Param("id") Long id);
}
