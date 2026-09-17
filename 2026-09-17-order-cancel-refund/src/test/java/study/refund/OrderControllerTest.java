package study.refund;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import study.refund.controller.OrderController;
import study.refund.domain.OrderStatus;
import study.refund.dto.CancelOrderResponse;
import study.refund.exception.*;
import study.refund.service.CancelOrderService;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
@DisplayName("주문 취소 API 계약 테스트")
class OrderControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean CancelOrderService service;

    @Test
    @DisplayName("정상 취소 응답을 JSON으로 직렬화하고 경로의 주문 ID를 서비스에 전달한다")
    void serializesSuccessContractAndRoutesId() throws Exception {
        when(service.cancel(42L)).thenReturn(new CancelOrderResponse(42L, OrderStatus.CANCELED,
                CancelOrderResponse.RefundStatus.COMPLETED));
        mvc.perform(post("/api/orders/42/cancel")).andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(42))
                .andExpect(jsonPath("$.status").value("CANCELED"))
                .andExpect(jsonPath("$.refundStatus").value("COMPLETED"));
        verify(service).cancel(42L);
    }

    @Test
    @DisplayName("존재하지 않는 주문 예외를 HTTP 404로 변환한다")
    void mapsNotFoundTo404() throws Exception {
        when(service.cancel(42L)).thenThrow(new OrderNotFoundException(42L));
        mvc.perform(post("/api/orders/42/cancel")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("취소할 수 없는 주문 예외를 HTTP 409로 변환한다")
    void mapsConflictTo409() throws Exception {
        when(service.cancel(42L)).thenThrow(new OrderNotCancelableException(42L));
        mvc.perform(post("/api/orders/42/cancel")).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("환불 처리 예외를 HTTP 503으로 변환한다")
    void mapsRefundFailureTo503() throws Exception {
        when(service.cancel(42L)).thenThrow(new RefundProcessingException("refund unavailable"));
        mvc.perform(post("/api/orders/42/cancel")).andExpect(status().isServiceUnavailable());
    }

    @Test
    @DisplayName("0 이하 주문 ID는 서비스 호출 전에 HTTP 400으로 거절한다")
    void nonPositiveIdIsRejectedBeforeService() throws Exception {
        mvc.perform(post("/api/orders/0/cancel")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
