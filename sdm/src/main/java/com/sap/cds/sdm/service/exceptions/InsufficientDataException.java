package com.sap.cds.sdm.service.exceptions;

import java.io.IOException;

public class InsufficientDataException extends IOException {
  public InsufficientDataException(String message) {
    super(message);
  }
}
