package com.baakio.dto.scan;

/** A privately retrieved document and its verified media type. */
public record StoredDocument(byte[] content, String contentType) {}
