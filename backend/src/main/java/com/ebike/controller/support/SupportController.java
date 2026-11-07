package com.ebike.controller.support;

import com.ebike.dto.support.FaqItemDto;
import com.ebike.dto.support.SupportRequestCreateRequest;
import com.ebike.dto.support.SupportRequestResponse;
import com.ebike.dto.support.SupportRequestUpdateRequest;
import com.ebike.service.chatbot.ChatbotService;
import com.ebike.service.support.SupportService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SupportController {

    private final SupportService supportService;
    private final ChatbotService chatbotService;

    public SupportController(SupportService supportService, ChatbotService chatbotService) {
        this.supportService = supportService;
        this.chatbotService = chatbotService;
    }

    /** The same questions and answers the chatbot uses, with the current fees filled in. */
    @GetMapping("/api/v1/faq")
    public List<FaqItemDto> faq() {
        return chatbotService.listFaq();
    }

    @PostMapping("/api/v1/support-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public SupportRequestResponse create(@RequestBody SupportRequestCreateRequest request, Authentication authentication) {
        return supportService.create(request, authentication);
    }

    @GetMapping("/api/v1/manager/support-requests")
    public List<SupportRequestResponse> list(@RequestParam(required = false) String status) {
        return supportService.list(status);
    }

    @PatchMapping("/api/v1/manager/support-requests/{id}")
    public SupportRequestResponse update(@PathVariable Long id, @RequestBody SupportRequestUpdateRequest request) {
        return supportService.update(id, request);
    }
}
