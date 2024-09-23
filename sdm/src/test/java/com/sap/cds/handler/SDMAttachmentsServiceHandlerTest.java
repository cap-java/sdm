package com.sap.cds.handler;

import static org.assertj.core.api.Assertions.assertThat;

import com.sap.cds.sdm.service.handler.SDMAttachmentsServiceHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;

public class SDMAttachmentsServiceHandlerTest {
  private SDMAttachmentsServiceHandler handler;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    handler = new SDMAttachmentsServiceHandler();
  }

  @Test
  void testPerformAction() {
    String result = handler.performAction();
    assertThat(result).isEqualTo("Action Performed");
  }
}
