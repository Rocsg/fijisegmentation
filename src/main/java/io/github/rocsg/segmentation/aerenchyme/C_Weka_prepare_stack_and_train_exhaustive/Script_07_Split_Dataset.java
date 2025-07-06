package io.github.rocsg.segmentation.aerenchyme.C_Weka_prepare_stack_and_train_exhaustive;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Script_07_Split_Dataset.java
 *
 * Lit un CSV listant toutes les annotations (CubeName,CodeObj), effectue un split aléatoire
 * 60%% train / 40%% test, et écrit deux CSV : train_split.csv et test_split.csv.
 */
  public class Script_07_Split_Dataset {
    private static final String BASE_PATH = "/Donnees/DD_CIRS626_DATA/Data_Cube_HTP";
    private static final String INPUT_CSV  = BASE_PATH + "/verified_patches.csv";
    private static final String TRAIN_CSV  = BASE_PATH + "/train_split.csv";
    private static final String TEST_CSV   = BASE_PATH + "/test_split.csv";
    private static final double TRAIN_RATIO = 0.6;

    public static void main(String[] args) {
        List<String> lines = new ArrayList<>();
        // Lecture des annotations
        try (BufferedReader br = new BufferedReader(new FileReader(INPUT_CSV))) {
            String header = br.readLine(); // header
            String line;
            while ((line = br.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    lines.add(line);
                }
            }
        } catch (IOException e) {
            System.err.println("Erreur lecture CSV " + INPUT_CSV + " : " + e.getMessage());
            return;
        }

        if (lines.isEmpty()) {
            System.out.println("Aucune annotation trouvée dans " + INPUT_CSV);
            return;
        }

        // Mélange aléatoire
        Collections.shuffle(lines);
        int splitIndex = (int) Math.round(lines.size() * TRAIN_RATIO);

        List<String> trainList = lines.subList(0, splitIndex);
        List<String> testList  = lines.subList(splitIndex, lines.size());

        // Écriture des CSV
        writeCsv(TRAIN_CSV, trainList);
        writeCsv(TEST_CSV, testList);

        System.out.println("Split terminé : " + trainList.size() + " train, " + testList.size() + " test.");
    }

    private static void writeCsv(String path, List<String> data) {
        File out = new File(path);
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(out, false))) {
            bw.write("CubeName,CodeObj");
            bw.newLine();
            for (String entry : data) {
                bw.write(entry);
                bw.newLine();
            }
        } catch (IOException e) {
            System.err.println("Erreur écriture CSV " + path + " : " + e.getMessage());
        }
    }
}
