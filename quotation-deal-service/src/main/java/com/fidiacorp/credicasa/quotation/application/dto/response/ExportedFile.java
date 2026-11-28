package com.fidiacorp.credicasa.quotation.application.dto.response;

public record ExportedFile(String fileName, String mediaType, byte[] content, String contentSha256, String snapshotHash, int versionNumber) {
}
