package com.baakio.ai;

public interface ScanExtractor {
    ScanExtraction extract(byte[] content, String mimeType);
}
