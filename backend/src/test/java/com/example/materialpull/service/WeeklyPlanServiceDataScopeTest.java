package com.example.materialpull.service;

import com.example.materialpull.common.BusinessException;
import com.example.materialpull.common.RequestContext;
import com.example.materialpull.entity.WeeklyPlanBatchEntity;
import com.example.materialpull.enums.UserRole;
import com.example.materialpull.repository.WeeklyPlanBatchRepository;
import com.example.materialpull.repository.WeeklyPlanRowRepository;
import com.example.materialpull.repository.WeeklyPlanShiftQtyRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WeeklyPlanServiceDataScopeTest {
    @Mock WeeklyPlanBatchRepository batchRepository;
    @Mock WeeklyPlanRowRepository rowRepository;
    @Mock WeeklyPlanShiftQtyRepository shiftRepository;
    @Spy DataScopeService dataScopeService = new DataScopeService();
    @InjectMocks WeeklyPlanService service;

    @BeforeEach
    void login() {
        RequestContext.setLoginUser(7L, "planner", "计划员", UserRole.PLANNER, "弋江", List.of("T26 Floor"));
    }

    @AfterEach
    void clear() { RequestContext.clear(); }

    @Test
    void batchListUsesCurrentFactory() {
        service.listBatches();
        verify(batchRepository).findTop200ByFactoryIgnoreCaseOrderByIdDesc("弋江");
        verify(batchRepository, never()).findTop200ByOrderByIdDesc();
    }

    @Test
    void rowsRejectBatchFromAnotherFactory() {
        WeeklyPlanBatchEntity batch = new WeeklyPlanBatchEntity();
        batch.setBatchNo("WPL-2"); batch.setFactory("三山");
        when(batchRepository.findByBatchNo("WPL-2")).thenReturn(Optional.of(batch));

        assertThrows(BusinessException.class, () -> service.rows("WPL-2", PageRequest.of(0, 20)));
        verify(rowRepository, never()).findByBatchNo(anyString(), any(org.springframework.data.domain.Pageable.class));
    }
}
