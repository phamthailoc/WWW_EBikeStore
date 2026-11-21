package com.ebike.controller.user;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ebike.service.stock.StockAlertService;
import com.ebike.service.stock.StockAlertSubscription;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** V22: a malformed request body gets a readable Vietnamese message, not "Bad Request". */
class StockAlertEndpointTest {
    StockAlertService service = mock(StockAlertService.class);
    MockMvc mvc = MockMvcBuilders.standaloneSetup(new StockAlertController(service)).build();
    UsernamePasswordAuthenticationToken an = new UsernamePasswordAuthenticationToken("an", "x");

    @Test void aVariantIdThatIsNotANumberIsAskedAgainInPlainWords() throws Exception {
        mvc.perform(post("/api/v1/stock-alerts").principal(an).contentType(MediaType.APPLICATION_JSON).content("{\"variantId\":\"red\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Vui lòng chọn màu xe."));
        verify(service, never()).subscribe(any(), any());
    }

    @Test void aColourThatIsNotANumberInThePathIsAskedAgainInPlainWords() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/stock-alerts/abc").principal(an))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Vui lòng chọn màu xe."));
        verify(service, never()).unsubscribe(any(), any());
    }

    @Test void aBodyThatIsNotJsonGetsAReadableAnswer() throws Exception {
        mvc.perform(post("/api/v1/stock-alerts").principal(an).contentType(MediaType.TEXT_PLAIN).content("11"))
            .andExpect(status().isUnsupportedMediaType())
            .andExpect(jsonPath("$.message").value("Yêu cầu không hợp lệ. Vui lòng thử lại."));
        verify(service, never()).subscribe(any(), any());
    }

    @Test void aValidRequestSubscribesTheSignedInCustomer() throws Exception {
        org.mockito.Mockito.when(service.subscribe("an", 11L)).thenReturn(new StockAlertSubscription(null, true));
        mvc.perform(post("/api/v1/stock-alerts").principal(an).contentType(MediaType.APPLICATION_JSON).content("{\"variantId\":11}"))
            .andExpect(status().isCreated());
        verify(service).subscribe("an", 11L);
    }

    @Test void askingAgainWhileAlreadyWaitingAnswers200NotCreated() throws Exception {
        org.mockito.Mockito.when(service.subscribe("an", 11L)).thenReturn(new StockAlertSubscription(null, false));
        mvc.perform(post("/api/v1/stock-alerts").principal(an).contentType(MediaType.APPLICATION_JSON).content("{\"variantId\":11}"))
            .andExpect(status().isOk());
    }
}
