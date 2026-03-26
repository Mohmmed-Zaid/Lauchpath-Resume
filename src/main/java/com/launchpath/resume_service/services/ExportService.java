package com.launchpath.resume_service.services;

import com.launchpath.resume_service.entity.Resume;
import com.launchpath.resume_service.entity.ResumeSection;
import com.launchpath.resume_service.enums.ExportFormat;
import com.launchpath.resume_service.exception.ExportException;
import com.launchpath.resume_service.feign.UserServiceClient;
import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.stereotype.Service;
import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.util.Map;
import com.lowagie.text.Document;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExportService {

    private final ResumeService resumeService;
    private final FileUploadService fileUploadService;
    private final UserServiceClient userServiceClient;

    // ══════════════════════════════════════════════════════════
    // EXPORT ENTRY POINT
    // ══════════════════════════════════════════════════════════

    /**
     * Exports resume to PDF or DOCX.
     * 1. Consume download credit
     * 2. Generate file bytes
     * 3. Upload to Cloudinary
     * 4. Return download URL
     * 5. Refund if generation fails
     */
    public Map<String, Object> exportResume(String resumeId,
                                            Long userId,
                                            ExportFormat format) {
        log.info("Exporting resume - id: {}, format: {}, userId: {}",
                resumeId, format, userId);

        // 1. Consume download credit before generating
        userServiceClient.consumeDownloadCredit(userId);

        try {
            Resume resume = resumeService.getResumeById(resumeId, userId);

            // 2. Generate bytes
            byte[] fileBytes = switch (format) {
                case PDF  -> generatePdf(resume);
                case DOCX -> generateDocx(resume);
            };

            // 3. Upload to Cloudinary
            Map<String, Object> uploadResult =
                    fileUploadService.uploadGeneratedFile(
                            fileBytes, resumeId, format.name(), userId
                    );

            // 4. Store cloudinary ID in resume
            String cloudinaryId = (String) uploadResult.get("public_id");
            resume.getExportedFileIds().add(cloudinaryId);
            // resumeRepository.save(resume) — handled by ResumeService

            log.info("Export successful - resumeId: {}, format: {}, url: {}",
                    resumeId, format, uploadResult.get("url"));

            return Map.of(
                    "url", uploadResult.get("url"),
                    "format", format.name(),
                    "cloudinaryId", cloudinaryId,
                    "resumeTitle", resume.getTitle()
            );

        } catch (Exception e) {
            log.error("Export failed - resumeId: {}, refunding credit", resumeId);
            // Refund credit on failure
            try {
                userServiceClient.refundDownloadCredit(userId);
            } catch (Exception refundEx) {
                log.error("Download credit refund failed - userId: {}", userId);
            }
            throw new ExportException(
                    "Export failed. Your download credit has been refunded. " +
                            "Error: " + e.getMessage()
            );
        }
    }

    // ══════════════════════════════════════════════════════════
    // PDF GENERATION — OpenPDF (iText fork)
    // ══════════════════════════════════════════════════════════

    private byte[] generatePdf(Resume resume) {
        log.debug("Generating PDF for resumeId: {}", resume.getId());

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        try {
            Document document = new Document(PageSize.A4);
            PdfWriter.getInstance(document, outputStream);
            document.open();

            // ── Fonts ─────────────────────────────────────────
            Font nameFont = new Font(Font.HELVETICA, 22, Font.BOLD,
                    new Color(26, 26, 46));
            Font sectionFont = new Font(Font.HELVETICA, 13, Font.BOLD,
                    new Color(15, 52, 96));
            Font bodyFont = new Font(Font.HELVETICA, 10, Font.NORMAL,
                    new Color(30, 41, 59));
            Font subFont = new Font(Font.HELVETICA, 10, Font.ITALIC,
                    new Color(71, 85, 105));

            // ── Personal Info ─────────────────────────────────
            ResumeSection personalInfo = resume.getSections().stream()
                    .filter(s -> s.getType().name().equals("PERSONAL_INFO"))
                    .findFirst().orElse(null);

            if (personalInfo != null && personalInfo.getContent() != null) {
                Map<String, Object> info = personalInfo.getContent();

                String name = (String) info.getOrDefault("name",
                        resume.getTitle());
                Paragraph namePara = new Paragraph(name, nameFont);
                namePara.setAlignment(Element.ALIGN_CENTER);
                document.add(namePara);

                // Contact line
                StringBuilder contact = new StringBuilder();
                if (info.get("email") != null)
                    contact.append(info.get("email")).append("  |  ");
                if (info.get("phone") != null)
                    contact.append(info.get("phone")).append("  |  ");
                if (info.get("location") != null)
                    contact.append(info.get("location"));

                if (contact.length() > 0) {
                    Paragraph contactPara = new Paragraph(
                            contact.toString().trim(), subFont
                    );
                    contactPara.setAlignment(Element.ALIGN_CENTER);
                    document.add(contactPara);
                }
                document.add(new Paragraph(" "));
            }

            // ── Other Sections ────────────────────────────────
            for (ResumeSection section : resume.getSections()) {
                if (section.getType().name().equals("PERSONAL_INFO")) continue;
                if (!Boolean.TRUE.equals(section.getIsVisible())) continue;
                if (section.getContent() == null
                        || section.getContent().isEmpty()) continue;

                // Section header
                Paragraph sectionHeader = new Paragraph(
                        section.getTitle().toUpperCase(), sectionFont
                );
                sectionHeader.setSpacingBefore(12);
                document.add(sectionHeader);

                // Separator line
                com.lowagie.text.pdf.draw.LineSeparator line =
                        new com.lowagie.text.pdf.draw.LineSeparator(
                                1, 100, new Color(15, 52, 96),
                                Element.ALIGN_CENTER, -2
                        );
                document.add(new Chunk(line));
                document.add(new Paragraph(" "));

                // Section content
                renderSectionContent(document, section, bodyFont, subFont);
            }

            document.close();
            log.debug("PDF generated - {} bytes", outputStream.size());
            return outputStream.toByteArray();

        } catch (DocumentException e) {
            throw new ExportException("PDF generation failed: " + e.getMessage());
        }
    }

    private void renderSectionContent(Document document,
                                      ResumeSection section,
                                      Font bodyFont,
                                      Font subFont) throws DocumentException {
        Map<String, Object> content = section.getContent();

        switch (section.getType()) {
            case SUMMARY -> {
                String summary = (String) content.getOrDefault("text", "");
                if (!summary.isBlank()) {
                    document.add(new Paragraph(summary, bodyFont));
                }
            }
            case SKILLS -> {
                if (content.get("skills") instanceof java.util.List<?> skills) {
                    StringBuilder sb = new StringBuilder();
                    for (Object skill : skills) {
                        sb.append("• ").append(skill).append("  ");
                    }
                    document.add(new Paragraph(sb.toString(), bodyFont));
                }
            }
            default -> {
                // Generic rendering for EXPERIENCE, EDUCATION, PROJECTS etc
                content.forEach((key, value) -> {
                    if (value != null && !value.toString().isBlank()) {
                        try {
                            document.add(new Paragraph(
                                    key + ": " + value, bodyFont
                            ));
                        } catch (DocumentException e) {
                            log.warn("Could not render field: {}", key);
                        }
                    }
                });
            }
        }
        document.add(new Paragraph(" "));
    }

    // ══════════════════════════════════════════════════════════
    // DOCX GENERATION — Apache POI
    // ══════════════════════════════════════════════════════════

    private byte[] generateDocx(Resume resume) {
        log.debug("Generating DOCX for resumeId: {}", resume.getId());

        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            // ── Title (Name) ──────────────────────────────────
            ResumeSection personalInfo = resume.getSections().stream()
                    .filter(s -> s.getType().name().equals("PERSONAL_INFO"))
                    .findFirst().orElse(null);

            if (personalInfo != null && personalInfo.getContent() != null) {
                Map<String, Object> info = personalInfo.getContent();

                XWPFParagraph namePara = document.createParagraph();
                namePara.setAlignment(ParagraphAlignment.CENTER);
                XWPFRun nameRun = namePara.createRun();
                nameRun.setText((String) info.getOrDefault(
                        "name", resume.getTitle()
                ));
                nameRun.setBold(true);
                nameRun.setFontSize(20);
                nameRun.setColor("1A1A2E");

                // Contact info
                XWPFParagraph contactPara = document.createParagraph();
                contactPara.setAlignment(ParagraphAlignment.CENTER);
                XWPFRun contactRun = contactPara.createRun();

                StringBuilder contact = new StringBuilder();
                if (info.get("email") != null)
                    contact.append(info.get("email")).append("  |  ");
                if (info.get("phone") != null)
                    contact.append(info.get("phone")).append("  |  ");
                if (info.get("location") != null)
                    contact.append(info.get("location"));

                contactRun.setText(contact.toString().trim());
                contactRun.setFontSize(10);
                contactRun.setColor("475569");
            }

            // ── Other Sections ────────────────────────────────
            for (ResumeSection section : resume.getSections()) {
                if (section.getType().name().equals("PERSONAL_INFO")) continue;
                if (!Boolean.TRUE.equals(section.getIsVisible())) continue;
                if (section.getContent() == null
                        || section.getContent().isEmpty()) continue;

                // Section heading
                XWPFParagraph heading = document.createParagraph();
                XWPFRun headingRun = heading.createRun();
                headingRun.setText(section.getTitle().toUpperCase());
                headingRun.setBold(true);
                headingRun.setFontSize(12);
                headingRun.setColor("0F3460");
                headingRun.addBreak();

                // Section content
                renderDocxSection(document, section);
            }

            document.write(outputStream);
            log.debug("DOCX generated - {} bytes", outputStream.size());
            return outputStream.toByteArray();

        } catch (Exception e) {
            throw new ExportException(
                    "DOCX generation failed: " + e.getMessage()
            );
        }
    }

    private void renderDocxSection(XWPFDocument document,
                                   ResumeSection section) {
        Map<String, Object> content = section.getContent();

        XWPFParagraph para = document.createParagraph();
        XWPFRun run = para.createRun();
        run.setFontSize(10);

        switch (section.getType()) {
            case SUMMARY -> run.setText(
                    (String) content.getOrDefault("text", "")
            );
            case SKILLS -> {
                if (content.get("skills") instanceof java.util.List<?> skills) {
                    StringBuilder sb = new StringBuilder();
                    skills.forEach(s -> sb.append("• ").append(s).append("  "));
                    run.setText(sb.toString());
                }
            }
            default -> content.forEach((key, value) -> {
                if (value != null) {
                    XWPFParagraph p = document.createParagraph();
                    XWPFRun r = p.createRun();
                    r.setText(key + ": " + value);
                    r.setFontSize(10);
                }
            });
        }
    }
}
