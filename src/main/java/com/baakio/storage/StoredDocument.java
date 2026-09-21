package com.baakio.storage;

/** A privately retrieved document and its verified media type. */
public record StoredDocument(byte[] content, String contentType) {}
