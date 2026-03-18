package company;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class DynamicPdfCleaner {

    // ============================================================
    // PDF → TEXT EXTRACT
    // ============================================================
    public static String extractText(String pdfPath) throws Exception {
        PDDocument doc = PDDocument.load(new File(pdfPath));
        PDFTextStripper stripper = new PDFTextStripper();
        String text = stripper.getText(doc);
        doc.close();
        return text;
    }

    // ============================================================
    // CLEAN PERSONAL DATA + SKILL BLOCK + LANGUAGE BLOCK
    // ============================================================
    public static String cleanPersonalData(
            String pdfPath,
            String ownerName,
            List<String> bannedWords
    ) throws Exception {

        String raw = extractText(pdfPath);
        String[] lines = raw.split("\n");

        StringBuilder out = new StringBuilder();
        String prev = "";

        boolean skillBlock = false;
        boolean languageBlock = false;

        for (String line : lines) {

            if (line == null || line.trim().isEmpty())
                continue;

            String text = line
                    .replace("\u00A0", " ")
                    .replace("‑", "-")
                    .replace("–", "-")
                    .replace("—", "-")
                    .trim();

// -------------------------------------------------
// NAME CLEANING — remove  regardless of ownerName
// -------------------------------------------------
            if (text.matches("^([A-ZÇĞİÖŞÜ][a-zçğıöşü]+)\\s+([A-ZÇĞİÖŞÜ][a-zçğıöşü]+)$")) {
                continue;
            }


            String lower = text.toLowerCase();

            // -------------------------------------------------
            // ⭐ 1) NAME CLEANING — HER ŞEYDEN ÖNCE GELİYOR
            // -------------------------------------------------
            if (!ownerName.isEmpty() && lower.contains(ownerName.toLowerCase())) {
                prev = text;
                continue;
            }

            // -------------------------------------------------
            // SKILL BLOCK START
            // -------------------------------------------------
            if (text.equalsIgnoreCase("KEY SKILLS")) {
                skillBlock = true;
                out.append(text).append("\n\n");
                continue;
            }

            // -------------------------------------------------
            // SKILL BLOCK PROCESSING
            // -------------------------------------------------
            if (skillBlock) {

                if (text.equalsIgnoreCase("ABOUT THIS PROFILE")) {
                    skillBlock = false;
                    out.append("\n").append(text).append("\n\n");
                    prev = text;
                    continue;
                }

                text = applyMask(text, bannedWords);
                out.append(text).append("\n");

                prev = text;
                continue;
            }

            // -------------------------------------------------
            // LANGUAGES BLOCK START
            // -------------------------------------------------
            if (text.equalsIgnoreCase("LANGUAGES")) {
                languageBlock = true;
                out.append(text).append("\n\n");
                continue;
            }

            // -------------------------------------------------
            // LANGUAGES BLOCK PROCESSING
            // -------------------------------------------------
            if (languageBlock) {

                // NEW SECTION (ALL CAPS) ends language block
                if (text.equals(text.toUpperCase()) &&
                        !text.equalsIgnoreCase("LANGUAGES")) {

                    languageBlock = false;
                    out.append("\n").append(text).append("\n\n");
                    prev = text;
                    continue;
                }

                // Mask language line
                String masked = applyMask(text, bannedWords);
                out.append(masked).append("\n");

                prev = text;
                continue;
            }

            // -------------------------------------------------
            // NORMAL CLEANING LOGIC
            // -------------------------------------------------

            // Inline customer: "Role, Firm | 2022"
            if (text.contains(",") && text.contains("|")) {
                prev = text;
                continue;
            }

            // ROLE LINES
            boolean isRole = text.contains("|");
            if (isRole) {
                text = applyMask(text, bannedWords);
                out.append(text).append("\n\n");
                prev = text;
                continue;
            }

            // Under role: skip client/company line
            if (prev.contains("|")) {
                prev = text;
                continue;
            }

            // Educational "YEAR | xxx"
            if (text.matches("^[0-9]{4}\\s*\\|.*")) {
                prev = text;
                continue;
            }

            // org name / header detection
            int wc = text.split(" ").length;
            boolean looksOrg =
                    wc <= 4 &&
                            Character.isUpperCase(text.charAt(0)) &&
                            !lower.matches(".*\\b(ich|arbeit|entwick|test|habe)\\b.*");

            if (looksOrg) {
                prev = text;
                continue;
            }

            // normal masking
            text = applyMask(text, bannedWords);

            out.append(text).append("\n\n");
            prev = text;
        }

        return out.toString();
    }

    // ============================================================
    // MASK FOR BANNED WORDS
    // ============================================================
    private static String applyMask(String text, List<String> bannedWords) {

        if (bannedWords == null)
            return text;

        String result = text;

        for (String bad : bannedWords) {
            if (bad == null || bad.trim().isEmpty()) continue;
            result = result.replaceAll("(?i)" + bad.trim(), "***************");
        }

        return result;
    }

    // ============================================================
    // PDF WRITE
    // ============================================================
    public static void saveToPdf(String text, String outPath) throws Exception {

        PDDocument doc = new PDDocument();
        PDPage page = new PDPage();
        doc.addPage(page);

        PDType0Font font = PDType0Font.load(doc, new File("C:/Windows/Fonts/arial.ttf"));

        float fontSize = 11;
        float leading = 16;
        float startX = 50;
        float startY = 750;
        float maxWidth = page.getMediaBox().getWidth() - 100;

        PDPageContentStream cs = new PDPageContentStream(doc, page);

        cs.beginText();
        cs.setFont(font, fontSize);
        cs.setLeading(leading);
        cs.newLineAtOffset(startX, startY);

        float y = startY;

        for (String paragraph : text.split("\n")) {

            if (paragraph.trim().isEmpty()) {
                cs.newLine();
                y -= leading;
                continue;
            }

            List<String> wrapped = wrap(paragraph, font, fontSize, maxWidth);

            for (String w : wrapped) {

                if (y < 60) {
                    cs.endText();
                    cs.close();

                    page = new PDPage();
                    doc.addPage(page);

                    cs = new PDPageContentStream(doc, page);
                    cs.beginText();
                    cs.setFont(font, fontSize);
                    cs.setLeading(leading);
                    cs.newLineAtOffset(startX, startY);

                    y = startY;
                }

                cs.showText(w);
                cs.newLine();
                y -= leading;
            }

            cs.newLine();
            y -= leading;
        }

        cs.endText();
        cs.close();

        doc.save(outPath);
        doc.close();
    }

    // ============================================================
    // WORD WRAP
    // ============================================================
    private static List<String> wrap(String text, PDType0Font font, float fontSize, float maxWidth)
            throws Exception {

        List<String> lines = new ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder cur = new StringBuilder();

        for (String w : words) {

            String test = cur + (cur.length() == 0 ? "" : " ") + w;
            float width = font.getStringWidth(test) / 1000 * fontSize;

            if (width > maxWidth) {
                lines.add(cur.toString());
                cur = new StringBuilder(w);
            } else {
                if (cur.length() == 0)
                    cur.append(w);
                else
                    cur.append(" ").append(w);
            }
        }

        lines.add(cur.toString());
        return lines;
    }
}