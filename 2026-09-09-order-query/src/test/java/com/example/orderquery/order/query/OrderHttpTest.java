package com.example.orderquery.order.query;

import com.example.orderquery.member.Member;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OrderHttpTest {
    @Autowired MockMvc mvc;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void returnsOrderItemsAndTotalPriceThroughHttpWithOsivDisabled() throws Exception {
        Long memberId = new TransactionTemplate(transactionManager).execute(status -> {
            Member member = OrderFixtures.memberWithOrders(em, 1, 2);
            return member.getId();
        });

        mvc.perform(get("/api/members/{memberId}/orders?page=0&size=20", memberId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].items.length()").value(2))
                .andExpect(jsonPath("$.content[0].items[0].productName").value("상품-0-0"))
                .andExpect(jsonPath("$.content[0].items[1].productName").value("상품-0-1"))
                .andExpect(jsonPath("$.content[0].totalPrice").value(3000.00))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void missingMemberReturns404() throws Exception {
        mvc.perform(get("/api/members/-1/orders?page=0&size=20"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
    }

    @Test
    void rejectsNegativePage() throws Exception {
        mvc.perform(get("/api/members/1/orders?page=-1&size=20"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PAGE"));
    }

    @Test
    void rejectsSizeOutsideAllowedRange() throws Exception {
        mvc.perform(get("/api/members/1/orders?page=0&size=101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PAGE"));
    }

    @Test
    void rejectsZeroSize() throws Exception {
        mvc.perform(get("/api/members/1/orders?page=0&size=0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PAGE"));
    }
}
