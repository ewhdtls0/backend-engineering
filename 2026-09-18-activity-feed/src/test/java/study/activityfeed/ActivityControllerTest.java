package study.activityfeed;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import study.activityfeed.controller.ActivityController;
import study.activityfeed.dto.*;
import study.activityfeed.exception.MemberNotFoundException;
import study.activityfeed.service.ActivityService;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ActivityController.class)
class ActivityControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean ActivityService service;

    @Test void defaultsAndJsonContract() throws Exception {
        when(service.getActivities(1L, 0, 20)).thenReturn(new ActivityPageResponse(1L, 0, 20,
                List.of(new ActivityResponse(ActivityType.POST, 7L, "hello", LocalDateTime.of(2026, 9, 18, 12, 0)),
                        new ActivityResponse(ActivityType.COMMENT, 7L, "reply", LocalDateTime.of(2026, 9, 18, 11, 0)))));
        mvc.perform(get("/api/members/1/activities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberId").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.activities.length()").value(2))
                .andExpect(jsonPath("$.activities[0].type").value("POST"))
                .andExpect(jsonPath("$.activities[0].activityId").value(7))
                .andExpect(jsonPath("$.activities[0].content").value("hello"))
                .andExpect(jsonPath("$.activities[0].createdAt").value("2026-09-18T12:00:00"))
                .andExpect(jsonPath("$.activities[1].type").value("COMMENT"))
                .andExpect(jsonPath("$.activities[1].content").value("reply"));
        verify(service).getActivities(1L, 0, 20);
    }

    @ParameterizedTest @CsvSource({"0,1", "2,20", "2147483647,100"})
    void acceptsValidBoundariesAndForwardsExplicitParameters(int page, int size) throws Exception {
        when(service.getActivities(42L, page, size)).thenReturn(new ActivityPageResponse(42L, page, size, List.of()));
        mvc.perform(get("/api/members/42/activities").param("page", "" + page).param("size", "" + size))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page").value(page))
                .andExpect(jsonPath("$.size").value(size)).andExpect(jsonPath("$.activities").isEmpty());
        verify(service).getActivities(42L, page, size);
    }

    @ParameterizedTest
    @CsvSource({"1,-1,20", "1,0,0", "1,0,-1", "1,0,101", "1,abc,20", "1,0,no",
            "1,2147483648,20", "1,0,2147483648", "0,0,20", "-1,0,20", "bad,0,20"})
    void invalidParametersReturn400WithoutServiceCall(String memberId, String page, String size) throws Exception {
        mvc.perform(get("/api/members/{id}/activities", memberId).param("page", page).param("size", size))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        verifyNoInteractions(service);
    }

    @Test void missingMemberMapsTo404() throws Exception {
        when(service.getActivities(99L, 0, 20)).thenThrow(new MemberNotFoundException(99L));
        mvc.perform(get("/api/members/99/activities"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Member not found: 99"));
    }
}
