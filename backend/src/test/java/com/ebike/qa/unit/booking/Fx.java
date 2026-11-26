package com.ebike.qa.unit.booking;

import com.ebike.listener.notification.NotificationEventBridge;
import com.ebike.model.entity.auth.User;
import com.ebike.model.entity.order.Showroom;
import com.ebike.model.entity.product.Product;
import com.ebike.model.entity.product.ProductSpecification;
import com.ebike.model.entity.product.VehicleType;
import com.ebike.model.entity.testride.TestRideBooking;
import com.ebike.repository.auth.UserRepository;
import com.ebike.repository.order.ShowroomRepository;
import com.ebike.repository.product.ProductRepository;
import com.ebike.repository.testride.TestRideBookingRepository;
import com.ebike.service.admin.AuditLogService;
import com.ebike.service.testride.TestRideService;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;

/** Shared fixtures for the booking QA suite. Not a test class. */
final class Fx {

    static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    private Fx() {
    }

    /** A clock that reads {@code local} as a wall-clock time in the shop's zone. */
    static Clock clockAt(LocalDateTime local) {
        return Clock.fixed(local.atZone(VN).toInstant(), VN);
    }

    static Clock clockAt(String isoLocal) {
        return clockAt(LocalDateTime.parse(isoLocal));
    }

    /** The package-private constructor of TestRideService that takes a Clock (the public one builds a system clock). */
    static TestRideService rideService(TestRideBookingRepository bookings, ShowroomRepository showrooms, ProductRepository products,
                                       UserRepository users, AuditLogService audit, int daysAhead, int capacity, int lead, int maxOpen,
                                       int reminderHours, Clock clock) {
        try {
            Constructor<TestRideService> ctor = TestRideService.class.getDeclaredConstructor(
                TestRideBookingRepository.class, ShowroomRepository.class, ProductRepository.class, UserRepository.class, AuditLogService.class,
                int.class, int.class, int.class, int.class, int.class, Clock.class);
            ctor.setAccessible(true);
            return ctor.newInstance(bookings, showrooms, products, users, audit, daysAhead, capacity, lead, maxOpen, reminderHours, clock);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    static void set(Object target, String field, Object value) {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field f = type.getDeclaredField(field);
                f.setAccessible(true);
                f.set(target, value);
                return;
            } catch (NoSuchFieldException e) {
                type = type.getSuperclass();
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
        throw new IllegalArgumentException("no field " + field);
    }

    static User user(long id, String username) {
        User u = new User();
        u.setId(id);
        u.setUsername(username);
        u.setEmail(username + "@example.com");
        u.setFirstName("An");
        u.setLastName("Nguyen");
        u.setAccountStatus("ACTIVE");
        return u;
    }

    static Showroom showroom(long id, boolean active) {
        Showroom s = new Showroom();
        s.setId(id);
        s.setName("Showroom " + id);
        s.setAddress("1 Le Loi");
        s.setActive(active);
        s.setOpeningHours("08:00 - 21:00");
        return s;
    }

    static Showroom showroom(long id, String openingHours, LocalTime open, LocalTime close) {
        Showroom s = showroom(id, true);
        s.setOpeningHours(openingHours);
        s.setOpenTime(open);
        s.setCloseTime(close);
        return s;
    }

    static Product product(long id, VehicleType type, boolean active) {
        Product p = new Product();
        p.setId(id);
        p.setName("Bike " + id);
        p.setSlug("bike-" + id);
        p.setActive(active);
        if (type != null) {
            ProductSpecification spec = new ProductSpecification();
            spec.setVehicleType(type);
            p.setSpecification(spec);
        }
        return p;
    }

    static TestRideBooking booking(long id, User user, Showroom showroom, Product product, LocalDateTime slot, String status) {
        TestRideBooking b = new TestRideBooking();
        set(b, "id", id);
        b.setUser(user);
        b.setShowroom(showroom);
        b.setProduct(product);
        b.setSlotStart(slot);
        b.setStatus(status);
        b.setContactPhone("0901234567");
        b.setCreatedAt(slot.minusDays(1));
        b.setUpdatedAt(slot.minusDays(1));
        return b;
    }

    /** Captures what NotificationEventBridge.publish sends; call {@link Events#close()} in @AfterEach. */
    static final class Events implements AutoCloseable {
        final List<Object> published = new ArrayList<>();

        Events() {
            new NotificationEventBridge().setApplicationEventPublisher(new ApplicationEventPublisher() {
                @Override
                public void publishEvent(Object event) {
                    published.add(event);
                }

                @Override
                public void publishEvent(ApplicationEvent event) {
                    published.add(event);
                }
            });
        }

        @Override
        public void close() {
            new NotificationEventBridge().setApplicationEventPublisher(null);
        }
    }

    static Instant instant(String isoLocal) {
        return LocalDateTime.parse(isoLocal).atZone(VN).toInstant();
    }
}
