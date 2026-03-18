package company;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;

public class PdfScanner {

    private static float currentY = 750;
    private static final float MARGIN = 50;
    private static final float WIDTH = 500;
    private static PDPageContentStream currentContentStream;
    private static PDDocument currentDoc;
    private static List<String> forbiddenWords = new ArrayList<>();

    public static void main(String[] args) {
        Scanner consoleScanner = new Scanner(System.in);
        System.out.print("Bitte geben Sie den Namen auf dem Lebenslauf ein: ");
        String userInputName = consoleScanner.nextLine().trim();
        String searchNameKey = userInputName.toLowerCase().replace(" ", "");

        System.out.println("Geben Sie verbotene Woerter ein (Geben Sie 'done' zum Beenden ein):");
        while (true) {
            String word = consoleScanner.nextLine().trim();
            if (word.equalsIgnoreCase("done")) break;
            if (!word.isEmpty()) forbiddenWords.add(word);
        }

        String projectDir = System.getProperty("user.dir");
        String resPath = Paths.get(projectDir, "src", "main", "java", "resources").toString();
        File inputFile = new File(resPath, "Sogen02w_Sogeti_Cv_Eng.pdf");

        if (!inputFile.exists()) {
            System.err.println("FEHLER: PDF-Datei nicht gefunden unter: " + inputFile.getAbsolutePath());
            return;
        }

        try (PDDocument document = PDDocument.load(inputFile)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String[] lines = stripper.getText(document).split("\\r?\\n");

            List<String> educations = new ArrayList<>();
            List<String> certifications = new ArrayList<>();
            List<String> languages = new ArrayList<>();
            List<String> techSkills = new ArrayList<>();
            List<String> orgSkills = new ArrayList<>();
            List<String> methSkills = new ArrayList<>();
            List<String> aboutProfile = new ArrayList<>();
            Map<String, List<String>> workMap = new LinkedHashMap<>();

            boolean inEdu = false, inCerts = false, inLangs = false, inSkills = false, inAbout = false, inWork = false;
            boolean skipFirstLineAfterHeader = false;
            String currentSkillType = "";
            String lastWorkHeader = null;
            StringBuilder aboutAcc = new StringBuilder();

            for (String line : lines) {
                String cleaned = cleanForStandardPdf(line);
                // Erweiterte Maskierung wird hier aufgerufen
                String masked = maskForbiddenWords(cleaned);
                String trimmed = masked.trim();

                if (trimmed.isEmpty() || isGarbage(trimmed)) continue;

                if (inLangs && trimmed.toLowerCase().replace(" ", "").equals(searchNameKey)) {
                    inLangs = false; continue;
                }

                // Abschnittserkennung
                if (trimmed.equalsIgnoreCase("EDUCATIONS")) { inEdu = true; inCerts = false; inLangs = false; inSkills = false; continue; }
                if (trimmed.equalsIgnoreCase("CERTIFICATIONS")) { inCerts = true; inEdu = false; continue; }
                if (trimmed.equalsIgnoreCase("LANGUAGES")) { inLangs = true; inEdu = false; continue; }
                if (trimmed.equalsIgnoreCase("KEY SKILLS")) { inSkills = true; inLangs = false; continue; }
                if (trimmed.equalsIgnoreCase("ABOUT THIS PROFILE")) { inAbout = true; inSkills = false; continue; }
                if (trimmed.equalsIgnoreCase("WORK EXPERIENCE") || trimmed.equalsIgnoreCase("WORK EXPERIENCE CONTINUED")) {
                    inWork = true; inAbout = false; inSkills = false;
                    if (aboutAcc.length() > 0) { aboutProfile.add(aboutAcc.toString().trim()); aboutAcc.setLength(0); }
                    continue;
                }

                // Datenverarbeitung
                if (inEdu && trimmed.contains("|")) educations.add(trimmed);
                else if (inCerts) certifications.add(trimmed);
                else if (inLangs) languages.add(trimmed);
                else if (inSkills) {
                    if (trimmed.toUpperCase().contains("TECHNICAL")) currentSkillType = "T";
                    else if (trimmed.toUpperCase().contains("ORGANIZATIONAL")) currentSkillType = "O";
                    else if (trimmed.toUpperCase().contains("METHODOLOGICAL")) currentSkillType = "M";
                    else {
                        if (currentSkillType.equals("T")) techSkills.add(trimmed);
                        else if (currentSkillType.equals("O")) orgSkills.add(trimmed);
                        else if (currentSkillType.equals("M")) methSkills.add(trimmed);
                    }
                } else if (inAbout) {
                    aboutAcc.append(trimmed).append(" ");
                    if (trimmed.contains(".")) { aboutProfile.add(aboutAcc.toString().trim()); aboutAcc.setLength(0); }
                } else if (inWork) {
                    // Header-Erkennung fuer Berufserfahrung (Titel | Datum)
                    if (trimmed.contains("|") && (trimmed.toLowerCase().contains("20") || trimmed.toLowerCase().contains("current"))) {
                        String[] parts = trimmed.split("\\|");
                        String titlePart = parts[0].split(",")[0].trim();
                        String datePart = (parts.length > 1) ? parts[1].trim() : "";
                        lastWorkHeader = titlePart + " | " + datePart;
                        workMap.put(lastWorkHeader, new ArrayList<>());
                        skipFirstLineAfterHeader = true; // Ueberspringt die erste Zeile nach dem Header (z.B. Firmenname)
                    } else if (lastWorkHeader != null) {
                        if (skipFirstLineAfterHeader) { skipFirstLineAfterHeader = false; continue; }
                        String lower = trimmed.toLowerCase();
                        if (trimmed.length() > 3 && !trimmed.startsWith("Page") &&
                                !lower.contains(userInputName.toLowerCase()) &&
                                !lower.contains("hochschule darmstadt") && !lower.contains("demirel universitaet")) {
                            workMap.get(lastWorkHeader).add(trimmed);
                        }
                    }
                }
            }

            printToConsole(educations, certifications, languages, techSkills, orgSkills, methSkills, aboutProfile, workMap);
            generateFinalReport(resPath, educations, certifications, languages, techSkills, orgSkills, methSkills, aboutProfile, workMap);

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            consoleScanner.close();
        }
    }

    // Erweiterte Maskierungsmethode: Loescht den gesamten Block, der ein verbotenes Wort enthaelt
    private static String maskForbiddenWords(String text) {
        if (text == null || forbiddenWords.isEmpty()) return text;
        String result = text;
        for (String word : forbiddenWords) {
            // Regex: Loescht das Wort und alle damit verbundenen Zeichen ohne Leerzeichen
            String regex = "(?i)[^\\s]*" + Pattern.quote(word) + "[^\\s]*";
            result = result.replaceAll(regex, "***************");
        }
        return result;
    }

    private static boolean isGarbage(String text) {
        String lower = text.toLowerCase();
        return lower.matches("language [a-z]") || lower.startsWith("page ");
    }

    private static void printToConsole(List<String> edu, List<String> cert, List<String> lang, List<String> tech, List<String> org, List<String> meth, List<String> about, Map<String, List<String>> work) {
        System.out.println("\n[1] EDUCATIONS");
        for (String s : edu) System.out.println("- " + s);
        System.out.println("[2] CERTIFICATIONS");
        for (String s : cert) System.out.println("- " + s);
        System.out.println("[3] LANGUAGES");
        System.out.println(String.join(", ", lang));
        System.out.println("[4] KEY SKILLS");
        System.out.println("TECHNICAL: " + String.join(", ", tech));
        System.out.println("ORGANIZATIONAL: " + String.join(", ", org));
        System.out.println("METHODOLOGICAL: " + String.join(", ", meth));
        System.out.println("[5] ABOUT THIS PROFILE");
        for (String s : about) System.out.println("- " + s);
        System.out.println("[6] WORK EXPERIENCE");
        for (var entry : work.entrySet()) {
            System.out.println("# " + entry.getKey());
            for (String detail : entry.getValue()) {
                String bullet = (detail.startsWith("-") || detail.startsWith(">") || detail.startsWith("·")) ? "" : "> ";
                System.out.println(bullet + detail);
            }
        }
    }

    private static void generateFinalReport(String path, List<String> edu, List<String> cert, List<String> lang,
                                            List<String> tech, List<String> org, List<String> meth, List<String> about, Map<String, List<String>> work) throws IOException {

        currentDoc = new PDDocument();
        startNewPage();

        writeBlock("[1] EDUCATIONS", edu, PDType1Font.HELVETICA_BOLD);
        writeBlock("[2] CERTIFICATIONS", cert, PDType1Font.HELVETICA_BOLD);

        writeHeadingOnly("[3] LANGUAGES", PDType1Font.HELVETICA_BOLD);
        writeText(String.join(", ", lang), PDType1Font.HELVETICA, 10);
        currentY -= 15;

        writeHeadingOnly("[4] KEY SKILLS", PDType1Font.HELVETICA_BOLD);
        writeText("TECHNICAL: " + String.join(", ", tech), PDType1Font.HELVETICA, 10);
        writeText("ORGANIZATIONAL: " + String.join(", ", org), PDType1Font.HELVETICA, 10);
        writeText("METHODOLOGICAL: " + String.join(", ", meth), PDType1Font.HELVETICA, 10);
        currentY -= 15;

        writeBlock("[5] ABOUT THIS PROFILE", about, PDType1Font.HELVETICA_BOLD);

        writeHeadingOnly("[6] WORK EXPERIENCE", PDType1Font.HELVETICA_BOLD);
        for (String header : work.keySet()) {
            writeText("# " + header, PDType1Font.HELVETICA_BOLD, 10);
            for (String detail : work.get(header)) {
                String bullet = (detail.startsWith("-") || detail.startsWith(">") || detail.startsWith("·")) ? "" : "> ";
                writeText("  " + bullet + detail, PDType1Font.HELVETICA, 9);
            }
            currentY -= 5;
        }

        if (currentContentStream != null) currentContentStream.close();

        File outFile = new File(path, "CV_Report_Final.pdf");
        try {
            currentDoc.save(outFile);
            System.out.println("\nERFOLG! PDF-Datei wurde erstellt unter: " + outFile.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("\nKRITISCHER FEHLER: PDF konnte nicht gespeichert werden!");
        } finally {
            currentDoc.close();
        }
    }

    private static void startNewPage() throws IOException {
        if (currentContentStream != null) currentContentStream.close();
        PDPage page = new PDPage(PDRectangle.A4);
        currentDoc.addPage(page);
        currentContentStream = new PDPageContentStream(currentDoc, page);
        currentY = 750;
    }

    private static void checkPageBreak(float requiredSpace) throws IOException {
        if (currentY - requiredSpace < 50) startNewPage();
    }

    private static void writeBlock(String title, List<String> items, PDType1Font font) throws IOException {
        writeHeadingOnly(title, font);
        for (String item : items) writeText(" - " + item, PDType1Font.HELVETICA, 10);
        currentY -= 10;
    }

    private static void writeHeadingOnly(String text, PDType1Font font) throws IOException {
        checkPageBreak(40);
        currentContentStream.beginText();
        currentContentStream.setFont(font, 12);
        currentContentStream.newLineAtOffset(MARGIN, currentY);
        currentContentStream.showText(text);
        currentContentStream.endText();
        currentY -= 18;
    }

    private static void writeText(String text, PDType1Font font, int size) throws IOException {
        String safeText = text.replaceAll("[^\\x20-\\x7E]", " ");
        String[] words = safeText.split(" ");
        StringBuilder line = new StringBuilder();
        for (String word : words) {
            String test = line.length() == 0 ? word : line + " " + word;
            if (size * font.getStringWidth(test) / 1000 > WIDTH) {
                renderLine(line.toString(), font, size);
                line = new StringBuilder(word);
            } else {
                line.append(line.length() == 0 ? "" : " ").append(word);
            }
        }
        if (line.length() > 0) renderLine(line.toString(), font, size);
    }

    private static void renderLine(String line, PDType1Font font, int size) throws IOException {
        checkPageBreak(size + 5);
        currentContentStream.beginText();
        currentContentStream.setFont(font, size);
        currentContentStream.newLineAtOffset(MARGIN + 10, currentY);
        currentContentStream.showText(line);
        currentContentStream.endText();
        currentY -= (size + 4);
    }

    private static String cleanForStandardPdf(String line) {
        if (line == null) return "";
        return line.replace("\u00A0", " ")
                .replace("‑", "-").replace("–", "-").replace("—", "-")
                .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue")
                .replace("Ä", "Ae").replace("Ö", "Oe").replace("Ü", "Ue")
                .replace("ß", "ss").replace("", "-").replace("·", "-");
    }
}