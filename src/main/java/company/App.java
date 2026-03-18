package company;

import javax.swing.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class App {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(App::createGui);
    }

    private static void createGui() {

        JFrame frame = new JFrame("PDF Anonymisierer");
        frame.setSize(800, 600);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(null);
        frame.setResizable(true);  // Fenster größenveränderbar

        // ===========================================
        // GUI-KOMPONENTEN
        // ===========================================

        JLabel lblInput = new JLabel("PDF-Datei:");
        lblInput.setBounds(20, 20, 150, 25);

        JTextField txtInput = new JTextField();
        txtInput.setBounds(160, 20, 450, 25);

        JButton btnBrowseInput = new JButton("Durchsuchen");
        btnBrowseInput.setBounds(620, 20, 140, 25);


        JLabel lblOutput = new JLabel("Ausgabe-PDF:");
        lblOutput.setBounds(20, 60, 150, 25);

        JTextField txtOutput = new JTextField("bereinigt.pdf");
        txtOutput.setBounds(160, 60, 450, 25);

        JButton btnBrowseOutput = new JButton("Speicherort");
        btnBrowseOutput.setBounds(620, 60, 140, 25);


        JLabel lblOwner = new JLabel("Name aus PDF entfernen:");
        lblOwner.setBounds(20, 100, 200, 25);

        JTextField txtOwner = new JTextField();
        txtOwner.setBounds(220, 100, 390, 25);


        JLabel lblBanned = new JLabel("Verbotene Wörter:");
        lblBanned.setBounds(20, 150, 200, 25);

        DefaultListModel<String> bannedListModel = new DefaultListModel<>();
        JList<String> bannedList = new JList<>(bannedListModel);
        JScrollPane bannedScroll = new JScrollPane(bannedList);
        bannedScroll.setBounds(160, 150, 300, 160);

        JButton btnAddWord = new JButton("Wörter eingeben");
        btnAddWord.setBounds(480, 150, 150, 30);

        JButton btnRemoveWord = new JButton("Löschen");
        btnRemoveWord.setBounds(480, 190, 150, 30);


        JButton btnRun = new JButton("PDF erstellen");
        btnRun.setBounds(300, 350, 200, 50);


        // ===========================================
        // DATEI AUSWÄHLEN
        // ===========================================

        btnBrowseInput.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("PDF-Datei auswählen");

            if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
                txtInput.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });

        btnBrowseOutput.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setSelectedFile(new File("bereinigt.pdf"));
            chooser.setDialogTitle("Speicherort wählen");

            if (chooser.showSaveDialog(frame) == JFileChooser.APPROVE_OPTION) {
                txtOutput.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });


        // ===========================================
        // MODALES FENSTER: VERBOTENE WÖRTER
        // ===========================================

        btnAddWord.addActionListener(e -> openBannedWordDialog(frame, bannedListModel));
        btnRemoveWord.addActionListener(e -> {
            int idx = bannedList.getSelectedIndex();
            if (idx != -1) bannedListModel.remove(idx);
        });


        // ===========================================
        // PDF ERSTELLEN
        // ===========================================

        btnRun.addActionListener(e -> {

            try {
                String inputPdf = txtInput.getText().trim();
                String outputPdf = txtOutput.getText().trim();
                String owner = txtOwner.getText().trim();

                if (inputPdf.isEmpty()) {
                    JOptionPane.showMessageDialog(frame, "Bitte PDF-Datei auswählen!");
                    return;
                }

                List<String> bannedWords = new ArrayList<>();
                for (int i = 0; i < bannedListModel.size(); i++) {
                    bannedWords.add(bannedListModel.get(i));
                }

                String cleaned = DynamicPdfCleaner.cleanPersonalData(inputPdf, owner, bannedWords);
                DynamicPdfCleaner.saveToPdf(cleaned, outputPdf);

                JOptionPane.showMessageDialog(frame,
                        "Die bereinigte PDF wurde erfolgreich erstellt:\n" + outputPdf);

            } catch (Exception ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(frame,
                        "Es ist ein Fehler aufgetreten:\n" + ex.getMessage());
            }
        });

        // ===========================================
        // GUI HINZUFÜGEN
        // ===========================================

        frame.add(lblInput);
        frame.add(txtInput);
        frame.add(btnBrowseInput);

        frame.add(lblOutput);
        frame.add(txtOutput);
        frame.add(btnBrowseOutput);

        frame.add(lblOwner);
        frame.add(txtOwner);

        frame.add(lblBanned);
        frame.add(bannedScroll);
        frame.add(btnAddWord);
        frame.add(btnRemoveWord);

        frame.add(btnRun);

        frame.setVisible(true);
    }

    // ===========================================================
    // MODALES FENSTER FÜR MEHRFACHE WORTEINGABE (ENTER)
    // ===========================================================
    private static void openBannedWordDialog(JFrame parent, DefaultListModel<String> model) {

        JDialog dialog = new JDialog(parent, "Verbotene Wörter eingeben", true);
        dialog.setSize(450, 350);
        dialog.setLayout(null);
        dialog.setResizable(true);

        JLabel lbl = new JLabel("Wörter eingeben (ENTER für weiteres):");
        lbl.setBounds(20, 20, 300, 25);

        JTextField txt = new JTextField();
        txt.setBounds(20, 60, 250, 25);

        DefaultListModel<String> tempModel = new DefaultListModel<>();
        JList<String> tempList = new JList<>(tempModel);
        JScrollPane scroll = new JScrollPane(tempList);
        scroll.setBounds(20, 100, 250, 150);

        JButton btnSave = new JButton("Speichern");
        btnSave.setBounds(300, 220, 100, 30);

        // ENTER fügt Wort hinzu
        txt.addActionListener(e -> {
            String word = txt.getText().trim();
            if (!word.isEmpty()) {
                tempModel.addElement(word);
                txt.setText("");
            }
        });

        // SPEICHERN überträgt Wörter in die Haupt-Liste
        btnSave.addActionListener(e -> {
            for (int i = 0; i < tempModel.size(); i++) {
                model.addElement(tempModel.get(i));
            }
            dialog.dispose();
        });

        dialog.add(lbl);
        dialog.add(txt);
        dialog.add(scroll);
        dialog.add(btnSave);

        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
    }
}