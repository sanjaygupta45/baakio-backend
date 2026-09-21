package com.baakio.scan.ai;

public interface ScanExtractor {
    ScanExtraction extract(byte[] content, String mimeType);
}
