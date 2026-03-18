package company;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class AppConsole {

    public static void main(String[] args) {

        Scanner scanner = new Scanner(System.in);

        System.out.println("=== PDF Anonymizer (Console Version) ===");

        // ============================================
        // AUTOMATISCHE PDF-PFADE IM PROJEKTORDNER
        // ============================================
        String projectDir = System.getProperty("user.dir");

        String inputPdf  = projectDir + File.separator + "src/main/resources/cemtan.pdf";
        String outputPdf = projectDir + File.separator + "bereinigt.pdf";

        System.out.println("Input PDF  : " + inputPdf);
        System.out.println("Output PDF : " + outputPdf);

        // ============================================
        // OWNER NAME (optional)
        // ============================================
        System.out.print("Zu entfernender Name (oder leer lassen): ");
        String owner = scanner.nextLine().trim();

        // ============================================
        // BANNED WORDS
        // ============================================
        System.out.println("\nVerbotene Wörter eingeben (ENTER für neues Wort, LEER zum Beenden):");

        List<String> bannedWords = new ArrayList<>();

        while (true) {
            System.out.print("Wort: ");
            String w = scanner.nextLine().trim();
            if (w.isEmpty()) break;
            bannedWords.add(w);
        }

        System.out.println("\nFolgende Wörter werden maskiert: " + bannedWords);

        try {
            // ============================================
            // PDF VERARBEITEN
            // ============================================
            System.out.println("\nVerarbeite PDF...");

            String cleanedText = DynamicPdfCleaner.cleanPersonalData(
                    inputPdf,
                    owner,
                    bannedWords
            );

            DynamicPdfCleaner.saveToPdf(cleanedText, outputPdf);

            System.out.println("\n✔ Fertig! Neue PDF erstellt:");
            System.out.println(outputPdf);

        } catch (Exception e) {
            System.err.println("\n❌ Fehler: " + e.getMessage());
            e.printStackTrace();
        }

        scanner.close();
    }
}