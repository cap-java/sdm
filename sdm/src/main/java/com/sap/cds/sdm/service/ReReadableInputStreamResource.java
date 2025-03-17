package com.sap.cds.sdm.service;

import java.io.InputStream;
import org.springframework.core.io.InputStreamResource;

// Overriding InputStreamResource to avoid contentLength to be calculated by reading the
// InputStream.
// Note that we already know the content length

public class ReReadableInputStreamResource extends InputStreamResource {
  private final String filename;
  private final long contentLength;

  public ReReadableInputStreamResource(
      InputStream inputStream, String filename, long contentLength, String mimeType) {
    super(inputStream);
    this.filename = filename;
    this.contentLength = contentLength;
  }

  @Override
  public long contentLength() {
    return contentLength;
  }

  @Override
  public String getFilename() {
    return filename;
  }
}
