package com.example.materialpull.controller;

import com.example.materialpull.entity.LabelScanRuleEntity;
import com.example.materialpull.entity.LabelTemplateEntity;
import com.example.materialpull.repository.LabelScanRuleRepository;
import com.example.materialpull.repository.LabelTemplateRepository;
import com.example.materialpull.repository.SystemConfigRepository;
import com.example.materialpull.service.CodeRenderService;
import com.example.materialpull.service.DataScopeService;
import com.example.materialpull.service.LabelService;
import com.example.materialpull.service.MenuPermissionService;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class GlobalAdminEntryPointTest {

    @Test
    void menuPermissionManagementStopsBeforeRepositoryWhenNotGlobal() {
        SystemConfigRepository repository = mock(SystemConfigRepository.class);
        DataScopeService scope = deniedScope();
        MenuPermissionService service = new MenuPermissionService(repository, scope);

        assertThrows(RuntimeException.class, service::readAll);
        assertThrows(RuntimeException.class, () -> service.saveAll(Map.of()));

        verifyNoInteractions(repository);
    }

    @Test
    void labelTemplateManagementStopsBeforeRepositoryWhenNotGlobal() {
        LabelTemplateRepository templates = mock(LabelTemplateRepository.class);
        LabelScanRuleRepository rules = mock(LabelScanRuleRepository.class);
        LabelController controller = controller(templates, rules, deniedScope());

        assertThrows(RuntimeException.class, () -> controller.saveTemplate(new LabelTemplateEntity()));
        assertThrows(RuntimeException.class, () -> controller.delTemplate(1L));

        verifyNoInteractions(templates, rules);
    }

    @Test
    void labelScanRuleManagementStopsBeforeRepositoryWhenNotGlobal() {
        LabelTemplateRepository templates = mock(LabelTemplateRepository.class);
        LabelScanRuleRepository rules = mock(LabelScanRuleRepository.class);
        LabelController controller = controller(templates, rules, deniedScope());

        assertThrows(RuntimeException.class, () -> controller.saveScanRule(new LabelScanRuleEntity()));
        assertThrows(RuntimeException.class, () -> controller.delScanRule(1L));

        verifyNoInteractions(templates, rules);
    }

    private LabelController controller(LabelTemplateRepository templates, LabelScanRuleRepository rules,
                                       DataScopeService scope) {
        return new LabelController(mock(LabelService.class), templates, rules, mock(CodeRenderService.class), scope);
    }

    private DataScopeService deniedScope() {
        DataScopeService scope = mock(DataScopeService.class);
        doThrow(new RuntimeException("not global")).when(scope).requireGlobalAdmin();
        return scope;
    }
}
