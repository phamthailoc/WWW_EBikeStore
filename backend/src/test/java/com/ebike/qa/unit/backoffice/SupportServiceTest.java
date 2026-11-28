package com.ebike.qa.unit.backoffice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ebike.dto.support.SupportRequestCreateRequest;
import com.ebike.dto.support.SupportRequestUpdateRequest;
import com.ebike.model.entity.auth.User;
import com.ebike.model.entity.support.SupportRequest;
import com.ebike.repository.auth.UserRepository;
import com.ebike.repository.support.SupportRequestRepository;
import com.ebike.service.support.SupportService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.web.server.ResponseStatusException;

class SupportServiceTest {

    SupportRequestRepository repo = mock(SupportRequestRepository.class);
    UserRepository users = mock(UserRepository.class);
    SupportService service;

    @BeforeEach
    void setUp() {
        service = new SupportService(repo, users);
        when(repo.save(any(SupportRequest.class))).thenAnswer(i -> i.getArgument(0));
    }

    private static void assertStatus(Throwable t, HttpStatus status) {
        assertThat(t).isInstanceOf(ResponseStatusException.class);
        assertThat(((ResponseStatusException) t).getStatusCode().value()).isEqualTo(status.value());
    }

    private static SupportRequestCreateRequest req(String name, String email, String phone, String subject, String message) {
        return new SupportRequestCreateRequest(name, email, phone, subject, message);
    }

    private static SupportRequestCreateRequest good() {
        return req("Nguyen Van A", "A@Example.com", "0901234567", "Hỏi bảo hành", "Xe của tôi bị lỗi pin, xin tư vấn.");
    }

    @Test
    void create_happy_normalisesEmail_trims_defaultsToNewStatus() {
        var res = service.create(req("  Nguyen Van A ", "  A@Example.COM ", " 0901234567 ", " Hỏi ", "  Nội dung dài đủ 10 ký tự  "), null);
        ArgumentCaptor<SupportRequest> saved = ArgumentCaptor.forClass(SupportRequest.class);
        verify(repo).save(saved.capture());
        SupportRequest s = saved.getValue();
        assertThat(s.getEmail()).isEqualTo("a@example.com");
        assertThat(s.getCustomerName()).isEqualTo("Nguyen Van A");
        assertThat(s.getSubject()).isEqualTo("Hỏi");
        assertThat(s.getMessage()).isEqualTo("Nội dung dài đủ 10 ký tự");
        assertThat(s.getUser()).isNull();
        assertThat(res.status()).isEqualTo(SupportRequest.NEW);
    }

    @Test
    void create_attachesSignedInAccount_butNotAnonymousToken() {
        User me = Fx.user(5L, "tien", "t@x.com", "CUSTOMER");
        when(users.findByUsername("tien")).thenReturn(Optional.of(me));
        service.create(good(), Fx.auth("tien"));
        ArgumentCaptor<SupportRequest> saved = ArgumentCaptor.forClass(SupportRequest.class);
        verify(repo).save(saved.capture());
        assertThat(saved.getValue().getUser()).isSameAs(me);

        var anon = new AnonymousAuthenticationToken("k", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
        service.create(good(), anon);
        verify(users, org.mockito.Mockito.times(1)).findByUsername(anyString());
    }

    @Test
    void create_nullRequest_is400() {
        assertThatThrownBy(() -> service.create(null, null)).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
    }

    @Test
    void create_nameRules() {
        assertThatThrownBy(() -> service.create(req(null, "a@b.com", null, "s", "0123456789"), null)).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.create(req("   ", "a@b.com", null, "s", "0123456789"), null)).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.create(req("n".repeat(101), "a@b.com", null, "s", "0123456789"), null)).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        service.create(req("n".repeat(100), "a@b.com", null, "s", "0123456789"), null);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "plain", "a@b", "a@@b.com", "a b@c.com", "@c.com", "a@.com"})
    void create_badEmail_is400(String email) {
        assertThatThrownBy(() -> service.create(req("A", email, null, "s", "0123456789"), null)).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        verify(repo, never()).save(any());
    }

    @Test
    void create_emailLongerThan255_is400() {
        String email = "a".repeat(250) + "@b.com";
        assertThatThrownBy(() -> service.create(req("A", email, null, "s", "0123456789"), null)).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0901234567", "09012345678", "0123456789"})
    void create_validPhones(String phone) {
        service.create(req("A", "a@b.com", phone, "s", "0123456789"), null);
        verify(repo).save(any(SupportRequest.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"090123456", "090123456789", "9901234567", "+84901234567", "090-123-4567", "abcdefghij"})
    void create_invalidPhones_is400(String phone) {
        assertThatThrownBy(() -> service.create(req("A", "a@b.com", phone, "s", "0123456789"), null)).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
    }

    @Test
    void create_blankPhoneIsOptional_storedNull() {
        service.create(req("A", "a@b.com", "   ", "s", "0123456789"), null);
        ArgumentCaptor<SupportRequest> saved = ArgumentCaptor.forClass(SupportRequest.class);
        verify(repo).save(saved.capture());
        assertThat(saved.getValue().getPhone()).isNull();
    }

    @Test
    void create_subjectAndMessageBoundaries() {
        assertThatThrownBy(() -> service.create(req("A", "a@b.com", null, "", "0123456789"), null)).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.create(req("A", "a@b.com", null, "s".repeat(151), "0123456789"), null)).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        service.create(req("A", "a@b.com", null, "s".repeat(150), "0123456789"), null);
        assertThatThrownBy(() -> service.create(req("A", "a@b.com", null, "s", "123456789"), null)).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.create(req("A", "a@b.com", null, "s", "   123456789   "), null)).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.create(req("A", "a@b.com", null, "s", "m".repeat(3001)), null)).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        service.create(req("A", "a@b.com", null, "s", "m".repeat(3000)), null);
        assertThatThrownBy(() -> service.create(req("A", "a@b.com", null, "s", null), null)).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
    }

    @Test
    void create_rateLimit_fiveAnHourPerEmail_sixthIs429() {
        when(repo.countByEmailIgnoreCaseAndCreatedAtAfter(anyString(), any(LocalDateTime.class))).thenReturn(4L);
        service.create(good(), null);
        when(repo.countByEmailIgnoreCaseAndCreatedAtAfter(anyString(), any(LocalDateTime.class))).thenReturn(5L);
        assertThatThrownBy(() -> service.create(good(), null)).satisfies(t -> assertStatus(t, HttpStatus.TOO_MANY_REQUESTS));
        verify(repo, org.mockito.Mockito.times(1)).save(any());
    }

    @Test
    void create_rateLimitCountsTheNormalisedEmail() {
        service.create(req("A", "  MiXed@Example.COM ", null, "s", "0123456789"), null);
        verify(repo).countByEmailIgnoreCaseAndCreatedAtAfter(org.mockito.ArgumentMatchers.eq("mixed@example.com"), any());
    }

    /**
     * The form limits the message to 3000 characters but a customer name with HTML is stored verbatim; the staff page must escape it.
     * Here we only pin that the service does not silently strip content: a name containing markup is stored as typed (output encoding is the view's job).
     */
    @Test
    void create_storesMarkupAsText_noSilentTampering() {
        service.create(req("<b>A</b>", "a@b.com", null, "<script>x</script>", "0123456789"), null);
        ArgumentCaptor<SupportRequest> saved = ArgumentCaptor.forClass(SupportRequest.class);
        verify(repo).save(saved.capture());
        assertThat(saved.getValue().getCustomerName()).isEqualTo("<b>A</b>");
    }

    // ------------------------------------------------------------------ staff side

    @Test
    void list_noFilter_allNewestFirst_filterIsCaseInsensitive_garbageIs400() {
        SupportRequest s = new SupportRequest();
        when(repo.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(s));
        when(repo.findByStatusOrderByCreatedAtDesc("NEW")).thenReturn(List.of());
        assertThat(service.list(null)).hasSize(1);
        assertThat(service.list("  ")).hasSize(1);
        assertThat(service.list(" new ")).isEmpty();
        assertThatThrownBy(() -> service.list("DONE")).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
    }

    private SupportRequest existing() {
        SupportRequest s = new SupportRequest();
        s.setStatus(SupportRequest.NEW);
        s.setEmail("a@b.com");
        when(repo.findById(3L)).thenReturn(Optional.of(s));
        return s;
    }

    @Test
    void update_unknown_is404() {
        assertThatThrownBy(() -> service.update(9L, new SupportRequestUpdateRequest("RESOLVED", null))).satisfies(t -> assertStatus(t, HttpStatus.NOT_FOUND));
    }

    @Test
    void update_statusAndNote_trimmed_blankNoteClears() {
        SupportRequest s = existing();
        service.update(3L, new SupportRequestUpdateRequest(" resolved ", "  called back  "));
        assertThat(s.getStatus()).isEqualTo(SupportRequest.RESOLVED);
        assertThat(s.getStaffNote()).isEqualTo("called back");
        service.update(3L, new SupportRequestUpdateRequest(null, "   "));
        assertThat(s.getStaffNote()).isNull();
        assertThat(s.getStatus()).isEqualTo(SupportRequest.RESOLVED);
    }

    @Test
    void update_badStatus_is400_noteOver3000_is400_valuesUnchanged() {
        SupportRequest s = existing();
        assertThatThrownBy(() -> service.update(3L, new SupportRequestUpdateRequest("WONTFIX", "x"))).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.update(3L, new SupportRequestUpdateRequest(null, "n".repeat(3001)))).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        assertThat(s.getStatus()).isEqualTo(SupportRequest.NEW);
        assertThat(s.getStaffNote()).isNull();
        service.update(3L, new SupportRequestUpdateRequest(null, "n".repeat(3000)));
    }

    @Test
    void update_nullBody_isHarmlessNoOp() {
        SupportRequest s = existing();
        service.update(3L, null);
        assertThat(s.getStatus()).isEqualTo(SupportRequest.NEW);
    }

    @Test
    void update_canReopenAResolvedRequest() {
        SupportRequest s = existing();
        s.setStatus(SupportRequest.RESOLVED);
        service.update(3L, new SupportRequestUpdateRequest("IN_PROGRESS", null));
        assertThat(s.getStatus()).isEqualTo(SupportRequest.IN_PROGRESS);
    }

    /**
     * A name that is only a space-padded string, e.g. "A" followed by 99 spaces and then text, is trimmed first, so the 100 limit applies
     * to the trimmed value. (pins the trim-then-measure order.)
     */
    @Test
    void create_lengthIsMeasuredAfterTrim() {
        service.create(req(" ".repeat(50) + "n".repeat(100) + " ".repeat(50), "a@b.com", null, "s", "0123456789"), null);
        verify(repo).save(any(SupportRequest.class));
    }

    @Test
    void regression_create_emailRegexAcceptsTrailingDotDomain() {
        // "a@b.com." is not a deliverable address (register/checkout reject it); support accepts it because its pattern is looser.
        assertThatThrownBy(() -> service.create(req("A", "a@b.com.", null, "s", "0123456789"), null)).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
    }
}
