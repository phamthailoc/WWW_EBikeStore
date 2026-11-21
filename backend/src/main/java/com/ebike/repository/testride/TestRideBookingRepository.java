package com.ebike.repository.testride;

import com.ebike.model.entity.testride.TestRideBooking;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TestRideBookingRepository extends JpaRepository<TestRideBooking, Long> {

    /** Row lock for whoever changes a booking: the customer cancelling and the staff moving it wait for each other instead of both passing the step check. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from TestRideBooking b where b.id = :id")
    Optional<TestRideBooking> findLockedById(@Param("id") Long id);

    /** The same booking with everything a response reads, in one query. */
    @EntityGraph(attributePaths = {"user", "product", "showroom"})
    @Query("select b from TestRideBooking b where b.id = :id")
    Optional<TestRideBooking> findDetailedById(@Param("id") Long id);

    /**
     * The customer's bookings, the graph holds what the response reads. The inverse one-to-one sides (the bike's specification, the customer's
     * preference) are loaded eagerly and cannot be proxied, so without them in the graph each distinct bike and customer costs one more SELECT.
     */
    @EntityGraph(attributePaths = {"user", "user.preference", "product", "product.specification", "showroom"})
    List<TestRideBooking> findByUserUsername(String username, Pageable pageable);

    /**
     * The staff schedule. {@code showroomId} 0 means every showroom; the time window is half open ({@code from} inclusive, {@code to} exclusive).
     * The caller passes real values (not null) so the query reads the same on every database.
     */
    @EntityGraph(attributePaths = {"user", "user.preference", "product", "product.specification", "showroom"})
    @Query("select b from TestRideBooking b where b.slotStart >= :from and b.slotStart < :to and b.status in :statuses "
        + "and (:showroomId = 0L or b.showroom.id = :showroomId)")
    List<TestRideBooking> findSchedule(
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to,
        @Param("statuses") Collection<String> statuses,
        @Param("showroomId") long showroomId,
        Pageable pageable);

    /** Open (requested or confirmed) bookings of one showroom slot: the places that are taken. */
    @Query("select count(b) from TestRideBooking b where b.showroom.id = :showroomId and b.slotStart = :slot and b.status in :statuses")
    long countAt(@Param("showroomId") Long showroomId, @Param("slot") LocalDateTime slot, @Param("statuses") Collection<String> statuses);

    /** Places taken per slot over a time window of one showroom: rows of (slot start, count). */
    @Query("select b.slotStart, count(b) from TestRideBooking b where b.showroom.id = :showroomId and b.slotStart >= :from and b.slotStart < :to "
        + "and b.status in :statuses group by b.slotStart")
    List<Object[]> countBySlot(
        @Param("showroomId") Long showroomId,
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to,
        @Param("statuses") Collection<String> statuses);

    long countByStatus(String status);

    long countByStatusAndSlotStartLessThanEqual(String status, LocalDateTime slotStart);

    boolean existsByUserIdAndSlotStartAndStatusIn(Long userId, LocalDateTime slotStart, Collection<String> statuses);

    long countByUserIdAndStatusInAndSlotStartGreaterThanEqual(Long userId, Collection<String> statuses, LocalDateTime from);

    /** Confirmed bookings starting within the window whose reminder was not sent yet, soonest first (ids only; each is handled in its own transaction). */
    @Query("select b.id from TestRideBooking b where b.status = 'CONFIRMED' and b.reminderSentAt is null and b.slotStart > :now and b.slotStart <= :until order by b.slotStart, b.id")
    List<Long> findReminderDueIds(@Param("now") LocalDateTime now, @Param("until") LocalDateTime until, Pageable pageable);

    /** Requests nobody confirmed that reached their start time. */
    @Query("select b.id from TestRideBooking b where b.status = 'REQUESTED' and b.slotStart <= :now order by b.slotStart, b.id")
    List<Long> findExpiredRequestIds(@Param("now") LocalDateTime now, Pageable pageable);

    /** Account erasure: the customer's open bookings are cancelled so the places are free again. Returns how many rows changed. */
    @Modifying
    @Query("update TestRideBooking b set b.status = 'CANCELLED', b.updatedAt = :now where b.user.id = :userId and b.status in ('REQUESTED', 'CONFIRMED')")
    int cancelOpenOf(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    /**
     * Account erasure: the phone number, the words of the customer and the shop's note go (staff tend to write a name or a number in it, and the
     * customer could read it); the booking row itself stays as a record of the slot.
     */
    @Modifying
    @Query("update TestRideBooking b set b.contactPhone = '', b.customerNote = null, b.staffNote = null, b.updatedAt = :now where b.user.id = :userId")
    int clearPersonalDataOf(@Param("userId") Long userId, @Param("now") LocalDateTime now);
}
