package com.sap.cds.sdm.model;

import java.io.IOException;
import java.io.InputStream;
import java.util.function.Supplier;

public class SDMInputStream extends InputStream {

  private final Supplier<InputStream> inputStreamSupplier;
  private InputStream delegate;

  public SDMInputStream(Supplier<InputStream> inputStreamSupplier) {
    this.inputStreamSupplier = inputStreamSupplier;
  }

  @Override
  public int read() throws IOException {
    return getDelegate().read();
  }

  @Override
  public int read(byte[] b) throws IOException {
    return getDelegate().read(b);
  }

  @Override
  public int read(byte[] b, int off, int len) throws IOException {
    return getDelegate().read(b, off, len);
  }

  @Override
  public void close() throws IOException {
    if (delegate != null) {
      delegate.close();
    }
  }

  private InputStream getDelegate() {

    if (delegate == null) {
      delegate = inputStreamSupplier.get();
    }
    return delegate;
  }
}
