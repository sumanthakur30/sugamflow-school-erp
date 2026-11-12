package com.sugamflow.school.cms.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

class UploadRulesTest {

  @Test
  void acceptsPng() {
    MockMultipartFile file =
        new MockMultipartFile("file", "campus.png", "image/png", new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47});
    UploadRules.Prepared prepared = UploadRules.prepareImage(file);
    assertEquals("image/png", prepared.contentType());
    assertEquals("campus.png", prepared.fileName());
  }

  @Test
  void rejectsSvgWithScript() {
    MockMultipartFile file =
        new MockMultipartFile(
            "file",
            "logo.svg",
            "image/svg+xml",
            "<svg><script>alert(1)</script></svg>".getBytes());
    assertThrows(ResponseStatusException.class, () -> UploadRules.prepareImage(file));
  }

  @Test
  void acceptsPlainSvg() {
    MockMultipartFile file =
        new MockMultipartFile(
            "file", "logo.svg", "image/svg+xml", "<svg xmlns=\"http://www.w3.org/2000/svg\"></svg>".getBytes());
    UploadRules.Prepared prepared = UploadRules.prepareImage(file);
    assertEquals("image/svg+xml", prepared.contentType());
  }

  @Test
  void rejectsExecutableAsDocument() {
    MockMultipartFile file =
        new MockMultipartFile("file", "notes.exe", "application/octet-stream", new byte[] {1, 2, 3});
    assertThrows(ResponseStatusException.class, () -> UploadRules.prepareDocument(file));
  }

  @Test
  void rejectsPdfThatIsNotAPdf() {
    MockMultipartFile file =
        new MockMultipartFile("file", "notes.pdf", "application/pdf", "not-a-pdf".getBytes());
    assertThrows(ResponseStatusException.class, () -> UploadRules.preparePdf(file));
  }

  @Test
  void acceptsPdf() {
    MockMultipartFile file =
        new MockMultipartFile("file", "calendar.pdf", "application/pdf", "%PDF-1.4".getBytes());
    UploadRules.Prepared prepared = UploadRules.prepareDocument(file);
    assertEquals("application/pdf", prepared.contentType());
  }
}
