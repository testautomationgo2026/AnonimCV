package company;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.text.PDFTextStripper;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileSystemView;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class PdfGuiFinalApp extends JFrame {

    private JTextField txtInputPath, txtOutputPath, txtOwnerName;
    private JTextArea txtBannedWords;
    private JButton btnSpeichern, btnStart, btnLoeschen;
    private JLabel lblStatus;

    private float currentY = 750;
    private final float MARGIN = 50;
    private PDPageContentStream currentContentStream;
    private PDDocument currentDoc;
    private List<String> forbiddenWords = new ArrayList<>();

    private final List<String> defaultBannedWords = Arrays.asList(
            "bundid", "itzbund", "Capgemini", "Sogeti", "Mercedes", "Bosch", "Siemens", "Polizei", "zoll"
    );

    public PdfGuiFinalApp() {
        setTitle("PDF Lebenslauf Anonymisierer - Professional v3.4");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(750, 700);
        setLocationRelativeTo(null);

        JPanel mainPanel = new JPanel(new BorderLayout(15, 15));
        mainPanel.setBorder(new EmptyBorder(25, 25, 25, 25));
        add(mainPanel);

        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(8, 8, 8, 8);

        File desktop = FileSystemView.getFileSystemView().getHomeDirectory();

        gbc.gridx = 0; gbc.gridy = 0;
        formPanel.add(new JLabel("Quell-PDF:"), gbc);
        txtInputPath = new JTextField();
        gbc.gridx = 1; gbc.weightx = 1.0;
        formPanel.add(txtInputPath, gbc);
        JButton btnBrowseInput = new JButton("Datei waehlen");
        gbc.gridx = 2; gbc.weightx = 0;
        formPanel.add(btnBrowseInput, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        formPanel.add(new JLabel("Speicherort:"), gbc);
        txtOutputPath = new JTextField();
        gbc.gridx = 1; gbc.weightx = 1.0;
        formPanel.add(txtOutputPath, gbc);
        JButton btnBrowseOutput = new JButton("Ziel waehlen");
        gbc.gridx = 2; gbc.weightx = 0;
        formPanel.add(btnBrowseOutput, gbc);

        gbc.gridx = 0; gbc.gridy = 2;
        formPanel.add(new JLabel("Name des Inhabers:"), gbc);
        txtOwnerName = new JTextField();
        gbc.gridx = 1; gbc.gridwidth = 2;
        formPanel.add(txtOwnerName, gbc);

        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 1;
        formPanel.add(new JLabel("Zusaetzliche Verbote:"), gbc);
        txtBannedWords = new JTextArea(10, 20);
        JScrollPane scrollPane = new JScrollPane(txtBannedWords);
        gbc.gridx = 1; gbc.gridwidth = 2;
        formPanel.add(scrollPane, gbc);

        mainPanel.add(formPanel, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new GridLayout(4, 1, 10, 10));
        btnSpeichern = new JButton("SPEICHERN (Daten bestaetigen)");
        btnStart = new JButton("PDF-ERSTELLUNG STARTEN");
        btnLoeschen = new JButton("LOESCHEN (Formular leeren)");
        btnLoeschen.setBackground(new Color(255, 200, 200));

        btnStart.setEnabled(false);
        lblStatus = new JLabel("Bereit", SwingConstants.CENTER);

        bottomPanel.add(btnSpeichern);
        bottomPanel.add(btnStart);
        bottomPanel.add(btnLoeschen);
        bottomPanel.add(lblStatus);
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        btnBrowseInput.addActionListener(e -> {
            JFileChooser fc = new JFileChooser(desktop);
            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) txtInputPath.setText(fc.getSelectedFile().getAbsolutePath());
        });

        btnBrowseOutput.addActionListener(e -> {
            JFileChooser fc = new JFileChooser(desktop);
            fc.setSelectedFile(new File("Anonymisierter_Lebenslauf.pdf"));
            if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) txtOutputPath.setText(fc.getSelectedFile().getAbsolutePath());
        });

        btnSpeichern.addActionListener(e -> {
            if (txtOwnerName.getText().trim().isEmpty()) {
                JOptionPane.showMessageDialog(this, "Bitte geben Sie einen Namen ein!");
                return;
            }
            forbiddenWords = Arrays.stream(txtBannedWords.getText().split("\\n"))
                    .map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
            for (String dw : defaultBannedWords) {
                if (forbiddenWords.stream().noneMatch(fw -> fw.equalsIgnoreCase(dw))) {
                    forbiddenWords.add(dw);
                }
            }
            btnStart.setEnabled(true);
            lblStatus.setText("Daten gespeichert (inkl. Auto-Filter).");
            JOptionPane.showMessageDialog(this, "Erfolgreich geladen. Standard-Filter aktiv.");
        });

        btnLoeschen.addActionListener(e -> {
            txtInputPath.setText(""); txtOutputPath.setText(""); txtOwnerName.setText(""); txtBannedWords.setText("");
            btnStart.setEnabled(false); forbiddenWords.clear(); lblStatus.setText("Bereit fuer neue Eingabe.");
        });

        btnStart.addActionListener(e -> startProcessing());
    }

    private void startProcessing() {
        String output = txtOutputPath.getText();
        btnStart.setEnabled(false); btnLoeschen.setEnabled(false);
        lblStatus.setText("PDF wird erstellt...");

        new Thread(() -> {
            try {
                runScannerLogic(txtInputPath.getText(), output, txtOwnerName.getText());
                SwingUtilities.invokeLater(() -> {
                    lblStatus.setText("Vorgang erfolgreich.");
                    JOptionPane.showMessageDialog(this, "PDF erstellt unter:\n" + output, "Erfolg", JOptionPane.INFORMATION_MESSAGE);
                    btnStart.setEnabled(true); btnLoeschen.setEnabled(true);
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    lblStatus.setText("Fehler!");
                    JOptionPane.showMessageDialog(this, "Fehler: " + ex.getMessage(), "Fehler", JOptionPane.ERROR_MESSAGE);
                    btnStart.setEnabled(true); btnLoeschen.setEnabled(true);
                });
            }
        }).start();
    }

    private void runScannerLogic(String inputPath, String outputPath, String ownerName) throws IOException {
        try (PDDocument document = PDDocument.load(new File(inputPath))) {
            PDFTextStripper stripper = new PDFTextStripper();
            String[] lines = stripper.getText(document).split("\\r?\\n");

            List<String> educations = new ArrayList<>(), certifications = new ArrayList<>(),
                    languages = new ArrayList<>(), techSkills = new ArrayList<>(),
                    orgSkills = new ArrayList<>(), methSkills = new ArrayList<>(),
                    aboutProfile = new ArrayList<>();
            Map<String, List<String>> workMap = new LinkedHashMap<>();

            boolean inEdu = false, inCerts = false, inLangs = false, inSkills = false, inAbout = false, inWork = false;
            boolean skipNextLine = false;
            String currentSkillType = "", lastWorkHeader = null;
            StringBuilder aboutAcc = new StringBuilder();

            for (String line : lines) {
                String cleaned = cleanForPdf(line);
                String masked = maskWords(cleaned);
                String trimmed = masked.trim();

                if (trimmed.isEmpty() || isGarbage(trimmed)) continue;

                if (trimmed.equalsIgnoreCase("EDUCATIONS")) { inEdu = true; inCerts = inLangs = inSkills = inAbout = inWork = false; continue; }
                if (trimmed.equalsIgnoreCase("CERTIFICATIONS")) { inCerts = true; inEdu = inLangs = inSkills = inAbout = inWork = false; continue; }
                if (trimmed.equalsIgnoreCase("LANGUAGES")) { inLangs = true; inEdu = inCerts = inSkills = inAbout = inWork = false; continue; }
                if (trimmed.equalsIgnoreCase("KEY SKILLS")) { inSkills = true; inEdu = inCerts = inLangs = inAbout = inWork = false; continue; }
                if (trimmed.equalsIgnoreCase("ABOUT THIS PROFILE")) { inAbout = true; inEdu = inCerts = inLangs = inSkills = inWork = false; continue; }
                if (trimmed.equalsIgnoreCase("WORK EXPERIENCE") || trimmed.equalsIgnoreCase("WORK EXPERIENCE CONTINUED")) {
                    inWork = true; inEdu = inCerts = inLangs = inSkills = inAbout = false;
                    continue;
                }

                if (inEdu && trimmed.contains("|")) educations.add(trimmed);
                else if (inCerts) certifications.add(trimmed);
                else if (inLangs) {
                    if (trimmed.length() < 50 && !trimmed.toLowerCase().contains(ownerName.toLowerCase())) languages.add(trimmed);
                }
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
                    if (trimmed.contains("|") && (trimmed.toLowerCase().contains("20") || trimmed.toLowerCase().contains("current"))) {
                        String[] parts = trimmed.split("\\|");
                        String headerPart = parts[0].trim();
                        String datePart = (parts.length > 1) ? parts[1].trim() : "";
                        if (headerPart.contains(",")) headerPart = headerPart.split(",")[0].trim();
                        lastWorkHeader = headerPart + " | " + datePart;
                        workMap.put(lastWorkHeader, new ArrayList<>());
                        skipNextLine = true;
                    } else if (lastWorkHeader != null) {
                        if (skipNextLine) { skipNextLine = false; continue; }
                        if (trimmed.length() > 3 && !trimmed.toLowerCase().contains(ownerName.toLowerCase())) {
                            workMap.get(lastWorkHeader).add(trimmed);
                        }
                    }
                }
            }
            createFinalPdf(outputPath, educations, certifications, languages, techSkills, orgSkills, methSkills, aboutProfile, workMap);
        }
    }

    private void createFinalPdf(String outPath, List<String> edu, List<String> cert, List<String> lang,
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
            for (String detail : work.get(header)) writeText("  > " + detail, PDType1Font.HELVETICA, 9);
            currentY -= 5;
        }

        if (currentContentStream != null) currentContentStream.close();
        currentDoc.save(new File(outPath));
        currentDoc.close();
    }

    private String maskWords(String text) {
        if (text == null || forbiddenWords.isEmpty()) return text;
        String res = text;
        for (String w : forbiddenWords) {
            res = res.replaceAll("(?i)" + Pattern.quote(w), "***************");
        }
        return res;
    }

    private void startNewPage() throws IOException {
        if (currentContentStream != null) currentContentStream.close();
        PDPage page = new PDPage(PDRectangle.A4);
        currentDoc.addPage(page);
        currentContentStream = new PDPageContentStream(currentDoc, page);
        currentY = 750;
    }

    private void writeBlock(String title, List<String> items, PDType1Font font) throws IOException {
        writeHeadingOnly(title, font);
        for (String item : items) writeText(" - " + item, PDType1Font.HELVETICA, 10);
        currentY -= 10;
    }

    private void writeHeadingOnly(String text, PDType1Font font) throws IOException {
        if (currentY < 100) startNewPage();
        currentContentStream.beginText();
        currentContentStream.setFont(font, 12);
        currentContentStream.newLineAtOffset(MARGIN, currentY);
        currentContentStream.showText(text);
        currentContentStream.endText();
        currentY -= 18;
    }

    // GÜNCELLENEN METOT: Metni otomatik olarak böler ve alt satıra geçer
    private void writeText(String text, PDType1Font font, int size) throws IOException {
        String safe = text.replaceAll("[^\\x20-\\x7E]", " ");
        float maxWidth = PDRectangle.A4.getWidth() - (2 * MARGIN) - 20;

        List<String> lines = new ArrayList<>();
        String[] words = safe.split(" ");
        StringBuilder currentLine = new StringBuilder();

        for (String word : words) {
            String testLine = currentLine.length() == 0 ? word : currentLine + " " + word;
            float width = font.getStringWidth(testLine) / 1000 * size;
            if (width > maxWidth) {
                lines.add(currentLine.toString());
                currentLine = new StringBuilder(word);
            } else {
                currentLine = new StringBuilder(testLine);
            }
        }
        lines.add(currentLine.toString());

        for (String line : lines) {
            if (currentY < 50) startNewPage();
            currentContentStream.beginText();
            currentContentStream.setFont(font, size);
            currentContentStream.newLineAtOffset(MARGIN + 10, currentY);
            currentContentStream.showText(line);
            currentContentStream.endText();
            currentY -= (size + 5);
        }
    }

    private String cleanForPdf(String line) {
        if (line == null) return "";
        return line.replace("ae", "ae").replace("oe", "oe").replace("ue", "ue").replace("ss", "ss");
    }

    private boolean isGarbage(String text) {
        return text.toLowerCase().startsWith("page ") || text.toLowerCase().contains("language ");
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new PdfGuiFinalApp().setVisible(true));
    }
}