package com.sugamflow.school.subscription.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sugamflow.school.subscription.persistence.entity.BusinessTypeEntity;
import com.sugamflow.school.subscription.persistence.entity.FeatureDefinitionEntity;
import com.sugamflow.school.subscription.persistence.entity.LimitDefinitionEntity;
import com.sugamflow.school.subscription.persistence.entity.ModuleDefinitionEntity;
import com.sugamflow.school.subscription.persistence.repo.BusinessTypeRepository;
import com.sugamflow.school.subscription.persistence.repo.FeatureDefinitionRepository;
import com.sugamflow.school.subscription.persistence.repo.LimitDefinitionRepository;
import com.sugamflow.school.subscription.persistence.repo.ModuleDefinitionRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CatalogServiceTest {

  @Mock private BusinessTypeRepository businessTypeRepository;
  @Mock private ModuleDefinitionRepository moduleDefinitionRepository;
  @Mock private FeatureDefinitionRepository featureDefinitionRepository;
  @Mock private LimitDefinitionRepository limitDefinitionRepository;

  private CatalogService catalogService;

  @BeforeEach
  void setUp() {
    catalogService =
        new CatalogService(
            businessTypeRepository,
            moduleDefinitionRepository,
            featureDefinitionRepository,
            limitDefinitionRepository);
  }

  @Test
  void listBusinessTypesMapsActiveRows() {
    BusinessTypeEntity school = new BusinessTypeEntity();
    school.setCode("SCHOOL");
    school.setName("School ERP");
    school.setDescription("K-12");
    school.setSortOrder(10);
    when(businessTypeRepository.findByActiveTrueOrderBySortOrderAscNameAsc())
        .thenReturn(List.of(school));

    List<Map<String, Object>> out = catalogService.listBusinessTypes();

    assertEquals(1, out.size());
    assertEquals("SCHOOL", out.get(0).get("code"));
    assertEquals("School ERP", out.get(0).get("name"));
  }

  @Test
  void listModulesFiltersByBusinessType() {
    ModuleDefinitionEntity mod = new ModuleDefinitionEntity();
    mod.setCode("SCHOOL_FEE");
    mod.setBusinessTypeCode("SCHOOL");
    mod.setName("Fee");
    mod.setSortOrder(60);
    when(moduleDefinitionRepository.findByBusinessTypeCodeAndActiveTrueOrderBySortOrderAscNameAsc(
            "SCHOOL"))
        .thenReturn(List.of(mod));

    List<Map<String, Object>> out = catalogService.listModules("SCHOOL");

    assertEquals(1, out.size());
    assertEquals("SCHOOL_FEE", out.get(0).get("code"));
    assertEquals("SCHOOL", out.get(0).get("businessTypeCode"));
  }

  @Test
  void listFeaturesFiltersByModule() {
    FeatureDefinitionEntity feat = new FeatureDefinitionEntity();
    feat.setCode("FEATURE_FEE");
    feat.setModuleCode("SCHOOL_FEE");
    feat.setName("Fee collection");
    feat.setSortOrder(10);
    when(featureDefinitionRepository.findByModuleCodeAndActiveTrueOrderBySortOrderAscNameAsc(
            "SCHOOL_FEE"))
        .thenReturn(List.of(feat));

    List<Map<String, Object>> out = catalogService.listFeatures("SCHOOL_FEE");

    assertEquals(1, out.size());
    assertEquals("FEATURE_FEE", out.get(0).get("code"));
  }

  @Test
  void upsertModulePersistsViaRepository() {
    when(moduleDefinitionRepository.existsById("SCHOOL_FEE")).thenReturn(false);
    when(moduleDefinitionRepository.findById("SCHOOL_FEE")).thenReturn(Optional.empty());
    when(moduleDefinitionRepository.save(any(ModuleDefinitionEntity.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    Map<String, Object> out =
        catalogService.upsertModule(
            Map.of(
                "code", "SCHOOL_FEE",
                "name", "Fee",
                "businessTypeCode", "SCHOOL",
                "sortOrder", 60));

    assertEquals("SCHOOL_FEE", out.get("code"));
    assertEquals("SCHOOL", out.get("businessTypeCode"));
    verify(moduleDefinitionRepository).save(any(ModuleDefinitionEntity.class));
  }
}
