/*
 * © 2024 SAP SE or an SAP affiliate company and cds-feature-attachments contributors.
 */
package unit.com.sap.cds.sdm.handler.applicationservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.sap.cds.CdsData;
import com.sap.cds.Result;
import com.sap.cds.reflect.CdsAnnotation;
import com.sap.cds.reflect.CdsElement;
import com.sap.cds.reflect.CdsEntity;
import com.sap.cds.reflect.CdsModel;
import com.sap.cds.sdm.constants.SDMConstants;
import com.sap.cds.sdm.handler.TokenHandler;
import com.sap.cds.sdm.handler.applicationservice.SDMReadAttachmentsHandler;
import com.sap.cds.sdm.persistence.DBQuery;
import com.sap.cds.sdm.service.SDMService;
import com.sap.cds.sdm.utilities.SDMUtils;
import com.sap.cds.services.cds.CdsReadEventContext;
import com.sap.cds.services.persistence.PersistenceService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class SDMReadAttachmentsHandlerPopulateUploadableFlagsTest {

  @Mock private CdsReadEventContext context;
  @Mock private PersistenceService persistenceService;
  @Mock private SDMService sdmService;
  @Mock private TokenHandler tokenHandler;
  @Mock private DBQuery dbQuery;

  private SDMReadAttachmentsHandler cut;

  @BeforeEach
  void setUp() {
    cut = new SDMReadAttachmentsHandler(persistenceService, sdmService, tokenHandler, dbQuery);
  }

  // ── Early returns ─────────────────────────────────────────────────────────

  @Test
  void testPopulateUploadableFlags_nullData() {
    cut.populateUploadableFlags(context, null);
    verifyNoInteractions(context);
  }

  @Test
  void testPopulateUploadableFlags_emptyData() {
    cut.populateUploadableFlags(context, List.of());
    verifyNoInteractions(context);
  }

  // ── Path 1: parent entity with maxCount compositions ──────────────────────

  @Test
  void testPopulateUploadableFlags_path1_belowMaxCount_setsTrue() {
    CdsEntity target = mock(CdsEntity.class);
    CdsModel model = mock(CdsModel.class);
    CdsEntity attachmentEntity = mock(CdsEntity.class);
    Result countResult = mock(Result.class);

    when(context.getTarget()).thenReturn(target);
    when(context.getModel()).thenReturn(model);
    when(target.getQualifiedName()).thenReturn("sap.capire.Books");
    when(target.compositions()).thenAnswer(inv -> Stream.of(buildComposition("attachments", "2")));
    when(target.elements()).thenAnswer(inv -> Stream.of(buildKeyElement("ID")));
    when(model.findEntity("sap.capire.Books.attachments"))
        .thenReturn(Optional.of(attachmentEntity));
    when(countResult.rowCount()).thenReturn(1L);
    when(dbQuery.getAttachmentsForUPID(
            eq(attachmentEntity), eq(persistenceService), eq("p1"), eq("up__ID")))
        .thenReturn(countResult);

    Map<String, Object> rowMap = new HashMap<>();
    rowMap.put("ID", "p1");
    CdsData row = CdsData.create(rowMap);

    try (MockedStatic<SDMUtils> sdmUtils = Mockito.mockStatic(SDMUtils.class)) {
      sdmUtils.when(() -> SDMUtils.getUpIdKey(attachmentEntity)).thenReturn("up__ID");
      cut.populateUploadableFlags(context, List.of(row));
    }

    assertThat(row.get("isAttachmentsUploadable")).isEqualTo(true);
  }

  @Test
  void testPopulateUploadableFlags_path1_atMaxCount_setsFalse() {
    CdsEntity target = mock(CdsEntity.class);
    CdsModel model = mock(CdsModel.class);
    CdsEntity attachmentEntity = mock(CdsEntity.class);
    Result countResult = mock(Result.class);

    when(context.getTarget()).thenReturn(target);
    when(context.getModel()).thenReturn(model);
    when(target.getQualifiedName()).thenReturn("sap.capire.Books");
    when(target.compositions()).thenAnswer(inv -> Stream.of(buildComposition("attachments", "2")));
    when(target.elements()).thenAnswer(inv -> Stream.of(buildKeyElement("ID")));
    when(model.findEntity("sap.capire.Books.attachments"))
        .thenReturn(Optional.of(attachmentEntity));
    when(countResult.rowCount()).thenReturn(2L);
    when(dbQuery.getAttachmentsForUPID(
            eq(attachmentEntity), eq(persistenceService), eq("p1"), eq("up__ID")))
        .thenReturn(countResult);

    Map<String, Object> rowMap = new HashMap<>();
    rowMap.put("ID", "p1");
    CdsData row = CdsData.create(rowMap);

    try (MockedStatic<SDMUtils> sdmUtils = Mockito.mockStatic(SDMUtils.class)) {
      sdmUtils.when(() -> SDMUtils.getUpIdKey(attachmentEntity)).thenReturn("up__ID");
      cut.populateUploadableFlags(context, List.of(row));
    }

    assertThat(row.get("isAttachmentsUploadable")).isEqualTo(false);
  }

  @Test
  void testPopulateUploadableFlags_path1_keyFieldNull_returnsEarly() {
    CdsEntity target = mock(CdsEntity.class);

    when(context.getTarget()).thenReturn(target);
    when(target.compositions()).thenAnswer(inv -> Stream.of(buildComposition("attachments", "2")));
    when(target.elements()).thenAnswer(inv -> Stream.empty());

    Map<String, Object> rowMap = new HashMap<>();
    rowMap.put("ID", "p1");
    CdsData row = CdsData.create(rowMap);

    cut.populateUploadableFlags(context, List.of(row));

    verify(dbQuery, never()).getAttachmentsForUPID(any(), any(), any(), any());
    assertThat(row.get("isAttachmentsUploadable")).isNull();
  }

  @Test
  void testPopulateUploadableFlags_path1_keyValNull_rowSkipped() {
    CdsEntity target = mock(CdsEntity.class);
    CdsModel model = mock(CdsModel.class);

    when(context.getTarget()).thenReturn(target);
    when(context.getModel()).thenReturn(model);
    when(target.getQualifiedName()).thenReturn("sap.capire.Books");
    when(target.compositions()).thenAnswer(inv -> Stream.of(buildComposition("attachments", "2")));
    when(target.elements()).thenAnswer(inv -> Stream.of(buildKeyElement("ID")));

    Map<String, Object> rowMap = new HashMap<>();
    // "ID" key absent — row.get("ID") returns null
    CdsData row = CdsData.create(rowMap);

    cut.populateUploadableFlags(context, List.of(row));

    verify(dbQuery, never()).getAttachmentsForUPID(any(), any(), any(), any());
  }

  @Test
  void testPopulateUploadableFlags_path1_attachmentEntityNull_facetSkipped() {
    CdsEntity target = mock(CdsEntity.class);
    CdsModel model = mock(CdsModel.class);

    when(context.getTarget()).thenReturn(target);
    when(context.getModel()).thenReturn(model);
    when(target.getQualifiedName()).thenReturn("sap.capire.Books");
    when(target.compositions()).thenAnswer(inv -> Stream.of(buildComposition("attachments", "2")));
    when(target.elements()).thenAnswer(inv -> Stream.of(buildKeyElement("ID")));
    when(model.findEntity("sap.capire.Books.attachments")).thenReturn(Optional.empty());

    Map<String, Object> rowMap = new HashMap<>();
    rowMap.put("ID", "p1");
    CdsData row = CdsData.create(rowMap);

    cut.populateUploadableFlags(context, List.of(row));

    verify(dbQuery, never()).getAttachmentsForUPID(any(), any(), any(), any());
    assertThat(row.get("isAttachmentsUploadable")).isNull();
  }

  @Test
  void testPopulateUploadableFlags_path1_upIdKeyEmpty_facetSkipped() {
    CdsEntity target = mock(CdsEntity.class);
    CdsModel model = mock(CdsModel.class);
    CdsEntity attachmentEntity = mock(CdsEntity.class);

    when(context.getTarget()).thenReturn(target);
    when(context.getModel()).thenReturn(model);
    when(target.getQualifiedName()).thenReturn("sap.capire.Books");
    when(target.compositions()).thenAnswer(inv -> Stream.of(buildComposition("attachments", "2")));
    when(target.elements()).thenAnswer(inv -> Stream.of(buildKeyElement("ID")));
    when(model.findEntity("sap.capire.Books.attachments"))
        .thenReturn(Optional.of(attachmentEntity));

    Map<String, Object> rowMap = new HashMap<>();
    rowMap.put("ID", "p1");
    CdsData row = CdsData.create(rowMap);

    try (MockedStatic<SDMUtils> sdmUtils = Mockito.mockStatic(SDMUtils.class)) {
      sdmUtils.when(() -> SDMUtils.getUpIdKey(attachmentEntity)).thenReturn("");
      cut.populateUploadableFlags(context, List.of(row));
    }

    verify(dbQuery, never()).getAttachmentsForUPID(any(), any(), any(), any());
    assertThat(row.get("isAttachmentsUploadable")).isNull();
  }

  @Test
  void testPopulateUploadableFlags_path1_isDraft_draftEntityFound() {
    CdsEntity target = mock(CdsEntity.class);
    CdsModel model = mock(CdsModel.class);
    CdsEntity draftAttachmentEntity = mock(CdsEntity.class);
    Result countResult = mock(Result.class);

    when(context.getTarget()).thenReturn(target);
    when(context.getModel()).thenReturn(model);
    when(target.getQualifiedName()).thenReturn("sap.capire.Books");
    when(target.compositions()).thenAnswer(inv -> Stream.of(buildComposition("attachments", "3")));
    when(target.elements()).thenAnswer(inv -> Stream.of(buildKeyElement("ID")));
    // isDraft=true because IsActiveEntity=false
    when(model.findEntity("sap.capire.Books.attachments_drafts"))
        .thenReturn(Optional.of(draftAttachmentEntity));
    when(countResult.rowCount()).thenReturn(1L);
    when(dbQuery.getAttachmentsForUPID(
            eq(draftAttachmentEntity), eq(persistenceService), eq("p1"), eq("up__ID")))
        .thenReturn(countResult);

    Map<String, Object> rowMap = new HashMap<>();
    rowMap.put("ID", "p1");
    rowMap.put("IsActiveEntity", false);
    CdsData row = CdsData.create(rowMap);

    try (MockedStatic<SDMUtils> sdmUtils = Mockito.mockStatic(SDMUtils.class)) {
      sdmUtils.when(() -> SDMUtils.getUpIdKey(draftAttachmentEntity)).thenReturn("up__ID");
      cut.populateUploadableFlags(context, List.of(row));
    }

    verify(model, never()).findEntity("sap.capire.Books.attachments");
    assertThat(row.get("isAttachmentsUploadable")).isEqualTo(true);
  }

  @Test
  void testPopulateUploadableFlags_path1_isDraft_draftEntityNotFound_fallbackActive() {
    CdsEntity target = mock(CdsEntity.class);
    CdsModel model = mock(CdsModel.class);
    CdsEntity activeAttachmentEntity = mock(CdsEntity.class);
    Result countResult = mock(Result.class);

    when(context.getTarget()).thenReturn(target);
    when(context.getModel()).thenReturn(model);
    when(target.getQualifiedName()).thenReturn("sap.capire.Books");
    when(target.compositions()).thenAnswer(inv -> Stream.of(buildComposition("attachments", "2")));
    when(target.elements()).thenAnswer(inv -> Stream.of(buildKeyElement("ID")));
    when(model.findEntity("sap.capire.Books.attachments_drafts")).thenReturn(Optional.empty());
    when(model.findEntity("sap.capire.Books.attachments"))
        .thenReturn(Optional.of(activeAttachmentEntity));
    when(countResult.rowCount()).thenReturn(0L);
    when(dbQuery.getAttachmentsForUPID(
            eq(activeAttachmentEntity), eq(persistenceService), eq("p1"), eq("up__ID")))
        .thenReturn(countResult);

    Map<String, Object> rowMap = new HashMap<>();
    rowMap.put("ID", "p1");
    rowMap.put("IsActiveEntity", false);
    CdsData row = CdsData.create(rowMap);

    try (MockedStatic<SDMUtils> sdmUtils = Mockito.mockStatic(SDMUtils.class)) {
      sdmUtils.when(() -> SDMUtils.getUpIdKey(activeAttachmentEntity)).thenReturn("up__ID");
      cut.populateUploadableFlags(context, List.of(row));
    }

    assertThat(row.get("isAttachmentsUploadable")).isEqualTo(true);
  }

  @Test
  void testPopulateUploadableFlags_path1_multipleCompositions_bothFlagsSet() {
    CdsEntity target = mock(CdsEntity.class);
    CdsModel model = mock(CdsModel.class);
    CdsEntity attachmentsEntity = mock(CdsEntity.class);
    CdsEntity referencesEntity = mock(CdsEntity.class);
    Result attachResult = mock(Result.class);
    Result refResult = mock(Result.class);

    when(context.getTarget()).thenReturn(target);
    when(context.getModel()).thenReturn(model);
    when(target.getQualifiedName()).thenReturn("sap.capire.Books");
    when(target.compositions())
        .thenAnswer(
            inv ->
                Stream.of(
                    buildComposition("attachments", "2"), buildComposition("references", "3")));
    when(target.elements()).thenAnswer(inv -> Stream.of(buildKeyElement("ID")));
    when(model.findEntity("sap.capire.Books.attachments"))
        .thenReturn(Optional.of(attachmentsEntity));
    when(model.findEntity("sap.capire.Books.references")).thenReturn(Optional.of(referencesEntity));
    when(attachResult.rowCount()).thenReturn(2L); // at limit → false
    when(refResult.rowCount()).thenReturn(1L); // below limit → true
    when(dbQuery.getAttachmentsForUPID(eq(attachmentsEntity), any(), eq("p1"), eq("up__ID")))
        .thenReturn(attachResult);
    when(dbQuery.getAttachmentsForUPID(eq(referencesEntity), any(), eq("p1"), eq("refKey")))
        .thenReturn(refResult);

    Map<String, Object> rowMap = new HashMap<>();
    rowMap.put("ID", "p1");
    CdsData row = CdsData.create(rowMap);

    try (MockedStatic<SDMUtils> sdmUtils = Mockito.mockStatic(SDMUtils.class)) {
      sdmUtils.when(() -> SDMUtils.getUpIdKey(attachmentsEntity)).thenReturn("up__ID");
      sdmUtils.when(() -> SDMUtils.getUpIdKey(referencesEntity)).thenReturn("refKey");
      cut.populateUploadableFlags(context, List.of(row));
    }

    assertThat(row.get("isAttachmentsUploadable")).isEqualTo(false);
    assertThat(row.get("isReferencesUploadable")).isEqualTo(true);
  }

  @Test
  void testPopulateUploadableFlags_path1_multipleRows_dbCalledForEach() {
    CdsEntity target = mock(CdsEntity.class);
    CdsModel model = mock(CdsModel.class);
    CdsEntity attachmentEntity = mock(CdsEntity.class);
    Result countResult1 = mock(Result.class);
    Result countResult2 = mock(Result.class);

    when(context.getTarget()).thenReturn(target);
    when(context.getModel()).thenReturn(model);
    when(target.getQualifiedName()).thenReturn("sap.capire.Books");
    when(target.compositions()).thenAnswer(inv -> Stream.of(buildComposition("attachments", "2")));
    when(target.elements()).thenAnswer(inv -> Stream.of(buildKeyElement("ID")));
    when(model.findEntity("sap.capire.Books.attachments"))
        .thenReturn(Optional.of(attachmentEntity));
    when(countResult1.rowCount()).thenReturn(1L);
    when(countResult2.rowCount()).thenReturn(2L);
    when(dbQuery.getAttachmentsForUPID(eq(attachmentEntity), any(), eq("p1"), eq("up__ID")))
        .thenReturn(countResult1);
    when(dbQuery.getAttachmentsForUPID(eq(attachmentEntity), any(), eq("p2"), eq("up__ID")))
        .thenReturn(countResult2);

    Map<String, Object> rowMap1 = new HashMap<>();
    rowMap1.put("ID", "p1");
    Map<String, Object> rowMap2 = new HashMap<>();
    rowMap2.put("ID", "p2");
    CdsData row1 = CdsData.create(rowMap1);
    CdsData row2 = CdsData.create(rowMap2);

    try (MockedStatic<SDMUtils> sdmUtils = Mockito.mockStatic(SDMUtils.class)) {
      sdmUtils.when(() -> SDMUtils.getUpIdKey(attachmentEntity)).thenReturn("up__ID");
      cut.populateUploadableFlags(context, List.of(row1, row2));
    }

    assertThat(row1.get("isAttachmentsUploadable")).isEqualTo(true);
    assertThat(row2.get("isAttachmentsUploadable")).isEqualTo(false);
    verify(dbQuery, times(2)).getAttachmentsForUPID(eq(attachmentEntity), any(), any(), any());
  }

  // ── findFacetsWithMaxCount edge cases (tested via routing to Path 2) ──────

  @Test
  void testPopulateUploadableFlags_noMaxCountAnnotation_routesToPath2() {
    CdsEntity target = mock(CdsEntity.class);

    when(context.getTarget()).thenReturn(target);
    // Composition present but no maxCount annotation → empty facet list → Path 2
    CdsElement comp = mock(CdsElement.class);
    when(comp.findAnnotation(SDMConstants.ATTACHMENT_MAXCOUNT)).thenReturn(Optional.empty());
    when(target.compositions()).thenAnswer(inv -> Stream.of(comp));
    when(target.getQualifiedName()).thenReturn("sap.capire.Books.attachments");

    // No "up_" in data → Path 2 returns early
    Map<String, Object> rowMap = new HashMap<>();
    CdsData row = CdsData.create(rowMap);

    cut.populateUploadableFlags(context, List.of(row));

    // Path 1 DB call never made (facets empty), Path 2 returns early (no up_)
    verify(dbQuery, never()).getAttachmentsForUPID(any(), any(), any(), any());
  }

  @Test
  void testPopulateUploadableFlags_maxCountInvalidNumber_facetSkipped_routesToPath2() {
    CdsEntity target = mock(CdsEntity.class);

    when(context.getTarget()).thenReturn(target);
    CdsElement comp = mock(CdsElement.class);
    CdsAnnotation<Object> anno = buildAnnotation("not-a-number");
    when(comp.findAnnotation(SDMConstants.ATTACHMENT_MAXCOUNT)).thenReturn(Optional.of(anno));
    when(target.compositions()).thenAnswer(inv -> Stream.of(comp));
    when(target.getQualifiedName()).thenReturn("sap.capire.Books.attachments");

    Map<String, Object> rowMap = new HashMap<>();
    CdsData row = CdsData.create(rowMap);

    cut.populateUploadableFlags(context, List.of(row));

    verify(dbQuery, never()).getAttachmentsForUPID(any(), any(), any(), any());
  }

  @Test
  void testPopulateUploadableFlags_maxCountZero_facetSkipped_routesToPath2() {
    CdsEntity target = mock(CdsEntity.class);

    when(context.getTarget()).thenReturn(target);
    CdsElement comp = mock(CdsElement.class);
    CdsAnnotation<Object> anno = buildAnnotation("0");
    when(comp.findAnnotation(SDMConstants.ATTACHMENT_MAXCOUNT)).thenReturn(Optional.of(anno));
    when(target.compositions()).thenAnswer(inv -> Stream.of(comp));
    when(target.getQualifiedName()).thenReturn("sap.capire.Books.attachments");

    Map<String, Object> rowMap = new HashMap<>();
    CdsData row = CdsData.create(rowMap);

    cut.populateUploadableFlags(context, List.of(row));

    verify(dbQuery, never()).getAttachmentsForUPID(any(), any(), any(), any());
  }

  @Test
  void testPopulateUploadableFlags_maxCountNegative_facetSkipped_routesToPath2() {
    CdsEntity target = mock(CdsEntity.class);

    when(context.getTarget()).thenReturn(target);
    CdsElement comp = mock(CdsElement.class);
    CdsAnnotation<Object> anno = buildAnnotation("-1");
    when(comp.findAnnotation(SDMConstants.ATTACHMENT_MAXCOUNT)).thenReturn(Optional.of(anno));
    when(target.compositions()).thenAnswer(inv -> Stream.of(comp));
    when(target.getQualifiedName()).thenReturn("sap.capire.Books.attachments");

    Map<String, Object> rowMap = new HashMap<>();
    CdsData row = CdsData.create(rowMap);

    cut.populateUploadableFlags(context, List.of(row));

    verify(dbQuery, never()).getAttachmentsForUPID(any(), any(), any(), any());
  }

  // ── Path 2: populateUploadableFlagsViaUp ──────────────────────────────────

  @Test
  void testPopulateUploadableFlags_path2_noUpData_returnsEarly() {
    CdsEntity target = mock(CdsEntity.class);

    when(context.getTarget()).thenReturn(target);
    when(target.compositions()).thenAnswer(inv -> Stream.empty());
    when(target.getQualifiedName()).thenReturn("sap.capire.Books.attachments");

    // Row has no "up_" key
    Map<String, Object> rowMap = new HashMap<>();
    rowMap.put("up__ID", "p1");
    CdsData row = CdsData.create(rowMap);

    cut.populateUploadableFlags(context, List.of(row));

    verify(dbQuery, never()).getAttachmentsForUPID(any(), any(), any(), any());
  }

  @Test
  void testPopulateUploadableFlags_path2_entityNameNoDot_returnsEarly() {
    CdsEntity target = mock(CdsEntity.class);

    when(context.getTarget()).thenReturn(target);
    when(target.compositions()).thenAnswer(inv -> Stream.empty());
    // No dot in entity name → lastDot < 0 → early return
    when(target.getQualifiedName()).thenReturn("attachments");

    Map<String, Object> rowMap = new HashMap<>();
    rowMap.put("up_", new HashMap<>());
    CdsData row = CdsData.create(rowMap);

    cut.populateUploadableFlags(context, List.of(row));

    verify(dbQuery, never()).getAttachmentsForUPID(any(), any(), any(), any());
  }

  @Test
  void testPopulateUploadableFlags_path2_parentEntityNotFound_returnsEarly() {
    CdsEntity target = mock(CdsEntity.class);
    CdsModel model = mock(CdsModel.class);

    when(context.getTarget()).thenReturn(target);
    when(context.getModel()).thenReturn(model);
    when(target.compositions()).thenAnswer(inv -> Stream.empty());
    when(target.getQualifiedName()).thenReturn("sap.capire.Books.attachments");
    when(model.findEntity("sap.capire.Books")).thenReturn(Optional.empty());

    Map<String, Object> rowMap = new HashMap<>();
    rowMap.put("up_", new HashMap<>());
    CdsData row = CdsData.create(rowMap);

    cut.populateUploadableFlags(context, List.of(row));

    verify(dbQuery, never()).getAttachmentsForUPID(any(), any(), any(), any());
  }

  @Test
  void testPopulateUploadableFlags_path2_noMaxCountOnParentComposition_returnsEarly() {
    CdsEntity target = mock(CdsEntity.class);
    CdsModel model = mock(CdsModel.class);
    CdsEntity parentEntity = mock(CdsEntity.class);
    CdsElement comp = mock(CdsElement.class);

    when(context.getTarget()).thenReturn(target);
    when(context.getModel()).thenReturn(model);
    when(target.compositions()).thenAnswer(inv -> Stream.empty());
    when(target.getQualifiedName()).thenReturn("sap.capire.Books.attachments");
    when(model.findEntity("sap.capire.Books")).thenReturn(Optional.of(parentEntity));
    when(comp.getName()).thenReturn("attachments");
    when(comp.findAnnotation(SDMConstants.ATTACHMENT_MAXCOUNT)).thenReturn(Optional.empty());
    when(parentEntity.compositions()).thenAnswer(inv -> Stream.of(comp));

    Map<String, Object> rowMap = new HashMap<>();
    rowMap.put("up_", new HashMap<>());
    CdsData row = CdsData.create(rowMap);

    cut.populateUploadableFlags(context, List.of(row));

    verify(dbQuery, never()).getAttachmentsForUPID(any(), any(), any(), any());
  }

  @Test
  void testPopulateUploadableFlags_path2_maxCountInvalid_returnsEarly() {
    CdsEntity target = mock(CdsEntity.class);
    CdsModel model = mock(CdsModel.class);
    CdsEntity parentEntity = mock(CdsEntity.class);

    when(context.getTarget()).thenReturn(target);
    when(context.getModel()).thenReturn(model);
    when(target.compositions()).thenAnswer(inv -> Stream.empty());
    when(target.getQualifiedName()).thenReturn("sap.capire.Books.attachments");
    when(model.findEntity("sap.capire.Books")).thenReturn(Optional.of(parentEntity));
    setupParentCompositionWithMaxCount(parentEntity, "attachments", "not-a-number");

    Map<String, Object> rowMap = new HashMap<>();
    rowMap.put("up_", new HashMap<>());
    CdsData row = CdsData.create(rowMap);

    cut.populateUploadableFlags(context, List.of(row));

    verify(dbQuery, never()).getAttachmentsForUPID(any(), any(), any(), any());
  }

  @Test
  void testPopulateUploadableFlags_path2_maxCountZero_returnsEarly() {
    CdsEntity target = mock(CdsEntity.class);
    CdsModel model = mock(CdsModel.class);
    CdsEntity parentEntity = mock(CdsEntity.class);

    when(context.getTarget()).thenReturn(target);
    when(context.getModel()).thenReturn(model);
    when(target.compositions()).thenAnswer(inv -> Stream.empty());
    when(target.getQualifiedName()).thenReturn("sap.capire.Books.attachments");
    when(model.findEntity("sap.capire.Books")).thenReturn(Optional.of(parentEntity));
    setupParentCompositionWithMaxCount(parentEntity, "attachments", "0");

    Map<String, Object> rowMap = new HashMap<>();
    rowMap.put("up_", new HashMap<>());
    CdsData row = CdsData.create(rowMap);

    cut.populateUploadableFlags(context, List.of(row));

    verify(dbQuery, never()).getAttachmentsForUPID(any(), any(), any(), any());
  }

  @Test
  void testPopulateUploadableFlags_path2_upIdKeyEmpty_returnsEarly() {
    CdsEntity target = mock(CdsEntity.class);
    CdsModel model = mock(CdsModel.class);
    CdsEntity parentEntity = mock(CdsEntity.class);

    when(context.getTarget()).thenReturn(target);
    when(context.getModel()).thenReturn(model);
    when(target.compositions()).thenAnswer(inv -> Stream.empty());
    when(target.getQualifiedName()).thenReturn("sap.capire.Books.attachments");
    when(model.findEntity("sap.capire.Books")).thenReturn(Optional.of(parentEntity));
    setupParentCompositionWithMaxCount(parentEntity, "attachments", "2");

    Map<String, Object> rowMap = new HashMap<>();
    rowMap.put("up_", new HashMap<>());
    CdsData row = CdsData.create(rowMap);

    try (MockedStatic<SDMUtils> sdmUtils = Mockito.mockStatic(SDMUtils.class)) {
      sdmUtils.when(() -> SDMUtils.getUpIdKey(target)).thenReturn("");
      cut.populateUploadableFlags(context, List.of(row));
    }

    verify(dbQuery, never()).getAttachmentsForUPID(any(), any(), any(), any());
  }

  @Test
  void testPopulateUploadableFlags_path2_upDataNotMap_rowSkipped() {
    CdsEntity target = mock(CdsEntity.class);
    CdsModel model = mock(CdsModel.class);
    CdsEntity parentEntity = mock(CdsEntity.class);

    when(context.getTarget()).thenReturn(target);
    when(context.getModel()).thenReturn(model);
    when(target.compositions()).thenAnswer(inv -> Stream.empty());
    when(target.getQualifiedName()).thenReturn("sap.capire.Books.attachments");
    when(model.findEntity("sap.capire.Books")).thenReturn(Optional.of(parentEntity));
    setupParentCompositionWithMaxCount(parentEntity, "attachments", "2");

    Map<String, Object> rowMap = new HashMap<>();
    rowMap.put("up_", "not-a-map"); // not a Map
    rowMap.put("up__ID", "p1");
    CdsData row = CdsData.create(rowMap);

    try (MockedStatic<SDMUtils> sdmUtils = Mockito.mockStatic(SDMUtils.class)) {
      sdmUtils.when(() -> SDMUtils.getUpIdKey(target)).thenReturn("up__ID");
      cut.populateUploadableFlags(context, List.of(row));
    }

    verify(dbQuery, never()).getAttachmentsForUPID(any(), any(), any(), any());
  }

  @Test
  void testPopulateUploadableFlags_path2_parentIdNull_rowSkipped() {
    CdsEntity target = mock(CdsEntity.class);
    CdsModel model = mock(CdsModel.class);
    CdsEntity parentEntity = mock(CdsEntity.class);

    when(context.getTarget()).thenReturn(target);
    when(context.getModel()).thenReturn(model);
    when(target.compositions()).thenAnswer(inv -> Stream.empty());
    when(target.getQualifiedName()).thenReturn("sap.capire.Books.attachments");
    when(model.findEntity("sap.capire.Books")).thenReturn(Optional.of(parentEntity));
    setupParentCompositionWithMaxCount(parentEntity, "attachments", "2");

    Map<String, Object> upMap = new HashMap<>();
    Map<String, Object> rowMap = new HashMap<>();
    rowMap.put("up_", upMap);
    // "up__ID" absent → parentIdObj is null → row skipped
    CdsData row = CdsData.create(rowMap);

    try (MockedStatic<SDMUtils> sdmUtils = Mockito.mockStatic(SDMUtils.class)) {
      sdmUtils.when(() -> SDMUtils.getUpIdKey(target)).thenReturn("up__ID");
      cut.populateUploadableFlags(context, List.of(row));
    }

    verify(dbQuery, never()).getAttachmentsForUPID(any(), any(), any(), any());
    assertThat(upMap.get("isAttachmentsUploadable")).isNull();
  }

  @Test
  void testPopulateUploadableFlags_path2_belowMaxCount_setsTrueOnUpMap() {
    CdsEntity target = mock(CdsEntity.class);
    CdsModel model = mock(CdsModel.class);
    CdsEntity parentEntity = mock(CdsEntity.class);
    Result countResult = mock(Result.class);

    when(context.getTarget()).thenReturn(target);
    when(context.getModel()).thenReturn(model);
    when(target.compositions()).thenAnswer(inv -> Stream.empty());
    when(target.getQualifiedName()).thenReturn("sap.capire.Books.attachments");
    when(model.findEntity("sap.capire.Books")).thenReturn(Optional.of(parentEntity));
    setupParentCompositionWithMaxCount(parentEntity, "attachments", "2");
    when(countResult.rowCount()).thenReturn(1L);
    when(dbQuery.getAttachmentsForUPID(eq(target), eq(persistenceService), eq("p1"), eq("up__ID")))
        .thenReturn(countResult);

    Map<String, Object> upMap = new HashMap<>();
    Map<String, Object> rowMap = new HashMap<>();
    rowMap.put("up_", upMap);
    rowMap.put("up__ID", "p1");
    CdsData row = CdsData.create(rowMap);

    try (MockedStatic<SDMUtils> sdmUtils = Mockito.mockStatic(SDMUtils.class)) {
      sdmUtils.when(() -> SDMUtils.getUpIdKey(target)).thenReturn("up__ID");
      cut.populateUploadableFlags(context, List.of(row));
    }

    assertThat(upMap.get("isAttachmentsUploadable")).isEqualTo(true);
  }

  @Test
  void testPopulateUploadableFlags_path2_atMaxCount_setsFalseOnUpMap() {
    CdsEntity target = mock(CdsEntity.class);
    CdsModel model = mock(CdsModel.class);
    CdsEntity parentEntity = mock(CdsEntity.class);
    Result countResult = mock(Result.class);

    when(context.getTarget()).thenReturn(target);
    when(context.getModel()).thenReturn(model);
    when(target.compositions()).thenAnswer(inv -> Stream.empty());
    when(target.getQualifiedName()).thenReturn("sap.capire.Books.attachments");
    when(model.findEntity("sap.capire.Books")).thenReturn(Optional.of(parentEntity));
    setupParentCompositionWithMaxCount(parentEntity, "attachments", "2");
    when(countResult.rowCount()).thenReturn(2L);
    when(dbQuery.getAttachmentsForUPID(eq(target), eq(persistenceService), eq("p1"), eq("up__ID")))
        .thenReturn(countResult);

    Map<String, Object> upMap = new HashMap<>();
    Map<String, Object> rowMap = new HashMap<>();
    rowMap.put("up_", upMap);
    rowMap.put("up__ID", "p1");
    CdsData row = CdsData.create(rowMap);

    try (MockedStatic<SDMUtils> sdmUtils = Mockito.mockStatic(SDMUtils.class)) {
      sdmUtils.when(() -> SDMUtils.getUpIdKey(target)).thenReturn("up__ID");
      cut.populateUploadableFlags(context, List.of(row));
    }

    assertThat(upMap.get("isAttachmentsUploadable")).isEqualTo(false);
  }

  @Test
  void testPopulateUploadableFlags_path2_isDraftEntity_stripsSuffix() {
    CdsEntity target = mock(CdsEntity.class);
    CdsModel model = mock(CdsModel.class);
    CdsEntity parentEntity = mock(CdsEntity.class);
    Result countResult = mock(Result.class);

    when(context.getTarget()).thenReturn(target);
    when(context.getModel()).thenReturn(model);
    when(target.compositions()).thenAnswer(inv -> Stream.empty());
    // Entity name ends with _drafts
    when(target.getQualifiedName()).thenReturn("sap.capire.Books.attachments_drafts");
    // After stripping suffix: "sap.capire.Books.attachments" → facet="attachments",
    // parent="sap.capire.Books"
    when(model.findEntity("sap.capire.Books")).thenReturn(Optional.of(parentEntity));
    setupParentCompositionWithMaxCount(parentEntity, "attachments", "2");
    when(countResult.rowCount()).thenReturn(0L);
    when(dbQuery.getAttachmentsForUPID(eq(target), any(), eq("p1"), eq("up__ID")))
        .thenReturn(countResult);

    Map<String, Object> upMap = new HashMap<>();
    Map<String, Object> rowMap = new HashMap<>();
    rowMap.put("up_", upMap);
    rowMap.put("up__ID", "p1");
    CdsData row = CdsData.create(rowMap);

    try (MockedStatic<SDMUtils> sdmUtils = Mockito.mockStatic(SDMUtils.class)) {
      sdmUtils.when(() -> SDMUtils.getUpIdKey(target)).thenReturn("up__ID");
      cut.populateUploadableFlags(context, List.of(row));
    }

    assertThat(upMap.get("isAttachmentsUploadable")).isEqualTo(true);
  }

  @Test
  void testPopulateUploadableFlags_path2_caching_sameParent_dbCalledOnce() {
    CdsEntity target = mock(CdsEntity.class);
    CdsModel model = mock(CdsModel.class);
    CdsEntity parentEntity = mock(CdsEntity.class);
    Result countResult = mock(Result.class);

    when(context.getTarget()).thenReturn(target);
    when(context.getModel()).thenReturn(model);
    when(target.compositions()).thenAnswer(inv -> Stream.empty());
    when(target.getQualifiedName()).thenReturn("sap.capire.Books.attachments");
    when(model.findEntity("sap.capire.Books")).thenReturn(Optional.of(parentEntity));
    setupParentCompositionWithMaxCount(parentEntity, "attachments", "2");
    when(countResult.rowCount()).thenReturn(1L);
    when(dbQuery.getAttachmentsForUPID(eq(target), eq(persistenceService), eq("p1"), eq("up__ID")))
        .thenReturn(countResult);

    // Two rows, same parent ID — DB should be called only once
    Map<String, Object> upMap1 = new HashMap<>();
    Map<String, Object> rowMap1 = new HashMap<>();
    rowMap1.put("up_", upMap1);
    rowMap1.put("up__ID", "p1");

    Map<String, Object> upMap2 = new HashMap<>();
    Map<String, Object> rowMap2 = new HashMap<>();
    rowMap2.put("up_", upMap2);
    rowMap2.put("up__ID", "p1");

    CdsData row1 = CdsData.create(rowMap1);
    CdsData row2 = CdsData.create(rowMap2);

    try (MockedStatic<SDMUtils> sdmUtils = Mockito.mockStatic(SDMUtils.class)) {
      sdmUtils.when(() -> SDMUtils.getUpIdKey(target)).thenReturn("up__ID");
      cut.populateUploadableFlags(context, List.of(row1, row2));
    }

    verify(dbQuery, times(1)).getAttachmentsForUPID(any(), any(), any(), any());
    assertThat(upMap1.get("isAttachmentsUploadable")).isEqualTo(true);
    assertThat(upMap2.get("isAttachmentsUploadable")).isEqualTo(true);
  }

  @Test
  void testPopulateUploadableFlags_path2_caching_differentParents_dbCalledForEach() {
    CdsEntity target = mock(CdsEntity.class);
    CdsModel model = mock(CdsModel.class);
    CdsEntity parentEntity = mock(CdsEntity.class);
    Result countResult1 = mock(Result.class);
    Result countResult2 = mock(Result.class);

    when(context.getTarget()).thenReturn(target);
    when(context.getModel()).thenReturn(model);
    when(target.compositions()).thenAnswer(inv -> Stream.empty());
    when(target.getQualifiedName()).thenReturn("sap.capire.Books.attachments");
    when(model.findEntity("sap.capire.Books")).thenReturn(Optional.of(parentEntity));
    setupParentCompositionWithMaxCount(parentEntity, "attachments", "2");
    when(countResult1.rowCount()).thenReturn(1L);
    when(countResult2.rowCount()).thenReturn(2L);
    when(dbQuery.getAttachmentsForUPID(eq(target), eq(persistenceService), eq("p1"), eq("up__ID")))
        .thenReturn(countResult1);
    when(dbQuery.getAttachmentsForUPID(eq(target), eq(persistenceService), eq("p2"), eq("up__ID")))
        .thenReturn(countResult2);

    Map<String, Object> upMap1 = new HashMap<>();
    Map<String, Object> rowMap1 = new HashMap<>();
    rowMap1.put("up_", upMap1);
    rowMap1.put("up__ID", "p1");

    Map<String, Object> upMap2 = new HashMap<>();
    Map<String, Object> rowMap2 = new HashMap<>();
    rowMap2.put("up_", upMap2);
    rowMap2.put("up__ID", "p2");

    CdsData row1 = CdsData.create(rowMap1);
    CdsData row2 = CdsData.create(rowMap2);

    try (MockedStatic<SDMUtils> sdmUtils = Mockito.mockStatic(SDMUtils.class)) {
      sdmUtils.when(() -> SDMUtils.getUpIdKey(target)).thenReturn("up__ID");
      cut.populateUploadableFlags(context, List.of(row1, row2));
    }

    verify(dbQuery, times(2)).getAttachmentsForUPID(any(), any(), any(), any());
    assertThat(upMap1.get("isAttachmentsUploadable")).isEqualTo(true);
    assertThat(upMap2.get("isAttachmentsUploadable")).isEqualTo(false);
  }

  @Test
  void testPopulateUploadableFlags_path2_facetNameFootnotes_virtualFieldCorrect() {
    CdsEntity target = mock(CdsEntity.class);
    CdsModel model = mock(CdsModel.class);
    CdsEntity parentEntity = mock(CdsEntity.class);
    Result countResult = mock(Result.class);

    when(context.getTarget()).thenReturn(target);
    when(context.getModel()).thenReturn(model);
    when(target.compositions()).thenAnswer(inv -> Stream.empty());
    // facet = "footnotes" → virtualField = "isFootnotesUploadable"
    when(target.getQualifiedName()).thenReturn("sap.capire.Chapters.footnotes");
    when(model.findEntity("sap.capire.Chapters")).thenReturn(Optional.of(parentEntity));
    setupParentCompositionWithMaxCount(parentEntity, "footnotes", "1");
    when(countResult.rowCount()).thenReturn(0L);
    when(dbQuery.getAttachmentsForUPID(eq(target), any(), eq("c1"), eq("up__ID")))
        .thenReturn(countResult);

    Map<String, Object> upMap = new HashMap<>();
    Map<String, Object> rowMap = new HashMap<>();
    rowMap.put("up_", upMap);
    rowMap.put("up__ID", "c1");
    CdsData row = CdsData.create(rowMap);

    try (MockedStatic<SDMUtils> sdmUtils = Mockito.mockStatic(SDMUtils.class)) {
      sdmUtils.when(() -> SDMUtils.getUpIdKey(target)).thenReturn("up__ID");
      cut.populateUploadableFlags(context, List.of(row));
    }

    assertThat(upMap.get("isFootnotesUploadable")).isEqualTo(true);
    assertThat(upMap.get("isAttachmentsUploadable")).isNull();
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  @SuppressWarnings("unchecked")
  private CdsElement buildComposition(String facetName, String maxCountValue) {
    CdsElement comp = mock(CdsElement.class);
    CdsAnnotation<Object> anno = buildAnnotation(maxCountValue);
    when(comp.findAnnotation(SDMConstants.ATTACHMENT_MAXCOUNT)).thenReturn(Optional.of(anno));
    when(comp.getName()).thenReturn(facetName);
    return comp;
  }

  private CdsElement buildKeyElement(String name) {
    CdsElement el = mock(CdsElement.class);
    when(el.isKey()).thenReturn(true);
    when(el.getName()).thenReturn(name);
    return el;
  }

  @SuppressWarnings("unchecked")
  private CdsAnnotation<Object> buildAnnotation(String value) {
    CdsAnnotation<Object> anno = mock(CdsAnnotation.class);
    when(anno.getValue()).thenReturn(value);
    return anno;
  }

  @SuppressWarnings("unchecked")
  private void setupParentCompositionWithMaxCount(
      CdsEntity parentEntity, String facetName, String maxCountValue) {
    CdsElement comp = mock(CdsElement.class);
    CdsAnnotation<Object> anno = buildAnnotation(maxCountValue);
    when(comp.getName()).thenReturn(facetName);
    when(comp.findAnnotation(SDMConstants.ATTACHMENT_MAXCOUNT)).thenReturn(Optional.of(anno));
    when(parentEntity.compositions()).thenAnswer(inv -> Stream.of(comp));
  }
}
