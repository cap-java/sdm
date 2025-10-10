package unit.com.sap.cds.sdm.model;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.sap.cds.sdm.model.AttachmentReadContext;
import com.sap.cds.services.EventContext;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class AttachmentReadContextTest {

  @Test
  void testCreateAttachmentReadContext() {
    // Given
    AttachmentReadContext mockContext = mock(AttachmentReadContext.class);

    try (MockedStatic<EventContext> mockedEventContext = mockStatic(EventContext.class)) {
      mockedEventContext
          .when(() -> EventContext.create(AttachmentReadContext.class, null))
          .thenReturn(mockContext);

      // When
      AttachmentReadContext result = AttachmentReadContext.create();

      // Then
      assertNotNull(result);
      assertEquals(mockContext, result);
      mockedEventContext.verify(() -> EventContext.create(AttachmentReadContext.class, null));
    }
  }

  @Test
  void testSetAndGetResult() {
    // Given
    AttachmentReadContext context = mock(AttachmentReadContext.class);
    String testResult = "test-result-value";

    // Configure mock behavior
    doNothing().when(context).setResult(testResult);
    when(context.getResult()).thenReturn(testResult);

    // When
    context.setResult(testResult);
    String result = context.getResult();

    // Then
    assertEquals(testResult, result);
    verify(context).setResult(testResult);
    verify(context).getResult();
  }

  @Test
  void testSetResultWithNullValue() {
    // Given
    AttachmentReadContext context = mock(AttachmentReadContext.class);

    // Configure mock behavior
    doNothing().when(context).setResult(null);
    when(context.getResult()).thenReturn(null);

    // When
    context.setResult(null);
    String result = context.getResult();

    // Then
    assertNull(result);
    verify(context).setResult(null);
    verify(context).getResult();
  }

  @Test
  void testSetResultWithEmptyString() {
    // Given
    AttachmentReadContext context = mock(AttachmentReadContext.class);
    String emptyResult = "";

    // Configure mock behavior
    doNothing().when(context).setResult(emptyResult);
    when(context.getResult()).thenReturn(emptyResult);

    // When
    context.setResult(emptyResult);
    String result = context.getResult();

    // Then
    assertEquals(emptyResult, result);
    verify(context).setResult(emptyResult);
    verify(context).getResult();
  }

  @Test
  void testMultipleSetResultCalls() {
    // Given
    AttachmentReadContext context = mock(AttachmentReadContext.class);
    String firstResult = "first-result";
    String secondResult = "second-result";

    // Configure mock behavior
    doNothing().when(context).setResult(anyString());
    when(context.getResult()).thenReturn(firstResult).thenReturn(secondResult);

    // When
    context.setResult(firstResult);
    String firstGet = context.getResult();
    context.setResult(secondResult);
    String secondGet = context.getResult();

    // Then
    assertEquals(firstResult, firstGet);
    assertEquals(secondResult, secondGet);
    verify(context).setResult(firstResult);
    verify(context).setResult(secondResult);
    verify(context, times(2)).getResult();
  }

  @Test
  void testContextImplementsEventContext() {
    // Given
    AttachmentReadContext context = mock(AttachmentReadContext.class);

    // When & Then
    assertTrue(context instanceof EventContext);
  }

  @Test
  void testEventNameAnnotation() {
    // When
    Class<AttachmentReadContext> contextClass = AttachmentReadContext.class;

    // Then
    assertTrue(contextClass.isAnnotationPresent(com.sap.cds.services.EventName.class));

    com.sap.cds.services.EventName eventName =
        contextClass.getAnnotation(com.sap.cds.services.EventName.class);
    assertEquals("openAttachment", eventName.value());
  }

  @Test
  void testFactoryMethodWithMockedEventContext() {
    // Given
    AttachmentReadContext expectedContext = mock(AttachmentReadContext.class);

    try (MockedStatic<EventContext> mockedEventContext = mockStatic(EventContext.class)) {
      mockedEventContext
          .when(() -> EventContext.create(eq(AttachmentReadContext.class), isNull()))
          .thenReturn(expectedContext);

      // When
      AttachmentReadContext actualContext = AttachmentReadContext.create();

      // Then
      assertSame(expectedContext, actualContext);
      mockedEventContext.verify(
          () -> EventContext.create(AttachmentReadContext.class, null), times(1));
    }
  }
}
