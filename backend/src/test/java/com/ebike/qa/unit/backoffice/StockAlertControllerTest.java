package com.ebike.qa.unit.backoffice;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ebike.controller.user.StockAlertController;
import com.ebike.service.stock.StockAlertService;
import com.ebike.service.stock.StockAlertSubscription;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class StockAlertControllerTest {

    StockAlertService svc = mock(StockAlertService.class);
    MockMvc mvc = MockMvcBuilders.standaloneSetup(new StockAlertController(svc)).build();

    @Test
    void subscribe_new_is201_alreadyWaiting_is200() throws Exception {
        when(svc.subscribe("tien", 4L)).thenReturn(new StockAlertSubscription(null, true));
        mvc.perform(post("/api/v1/stock-alerts").principal(Fx.auth("tien")).contentType(MediaType.APPLICATION_JSON).content("{\"variantId\":4}"))
            .andExpect(status().isCreated());
        when(svc.subscribe("tien", 5L)).thenReturn(new StockAlertSubscription(null, false));
        mvc.perform(post("/api/v1/stock-alerts").principal(Fx.auth("tien")).contentType(MediaType.APPLICATION_JSON).content("{\"variantId\":5}"))
            .andExpect(status().isOk());
    }

    @Test
    void subscribe_malformedBodyOrWrongType_areFriendly400And415() throws Exception {
        mvc.perform(post("/api/v1/stock-alerts").principal(Fx.auth("tien")).contentType(MediaType.APPLICATION_JSON).content("{\"variantId\":\"abc\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        mvc.perform(post("/api/v1/stock-alerts").principal(Fx.auth("tien")).contentType(MediaType.TEXT_PLAIN).content("x"))
            .andExpect(status().isUnsupportedMediaType());
        verify(svc, never()).subscribe(any(), any());
    }

    @Test
    void unsubscribe_pathNotNumeric_is400_numeric_is204() throws Exception {
        mvc.perform(delete("/api/v1/stock-alerts/abc").principal(Fx.auth("tien"))).andExpect(status().isBadRequest());
        mvc.perform(delete("/api/v1/stock-alerts/4").principal(Fx.auth("tien"))).andExpect(status().isNoContent());
        verify(svc).unsubscribe("tien", 4L);
    }
}
