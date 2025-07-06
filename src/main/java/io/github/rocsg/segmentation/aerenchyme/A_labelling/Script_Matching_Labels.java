package io.github.rocsg.segmentation.aerenchyme.A_labelling;

import ij.IJ;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Script_Matching_Labels.java
 *
 * Pour chaque cube, lit les fichiers :
 *   labels_hani/Cube_<XX>_labels.csv
 *   labels_romain/Cube_<XX>_annotations.csv
 * Chaque CSV contient des lignes : NOMBOITE,CODEOBJET,X,Y,Z
 * Associe chaque CODEOBJETHANI à CODEOBJETROMAIN si mêmes X et Y.
 * Signale via IJ.log toute entrée sans correspondant.
 * Écrit un CSV unique :
 *   NOMBOITE,CODEOBJETHANI,CODEOBJETROMAIN,X,Y,Z
 * dans BASE_PATH + "/correspondance_labels_hani_romain.csv".
 */
public class Script_Matching_Labels {
    private static final String BASE_PATH      = "/Donnees/DD_CIRS626_DATA/Data_Cube_HTP";
    private static final String HANI_DIR       = BASE_PATH + "/labels_hani";
    private static final String ROMAIN_DIR     = BASE_PATH + "/labels_romain";
    private static final String OUT_CSV        = BASE_PATH + "/correspondance_labels_hani_romain.csv";

    public static void main(String[] args) {
        IJ.log("=== Script_Matching_Labels ===");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(OUT_CSV))) {
            // Écrire l'en-tête
            writer.write("NOMBOITE,CODEOBJETHANI,CODEOBJETROMAIN,X,Y,Z\n");

            File haniFolder = new File(HANI_DIR);
            File[] haniFiles = haniFolder.listFiles((d, n) -> n.endsWith("_labels.csv"));
            if (haniFiles == null) {
                IJ.error("Aucun fichier labels_hani trouvé dans " + HANI_DIR);
                return;
            }

            for (File haniFile : haniFiles) {
                String fname = haniFile.getName();
                String cube = fname.replaceFirst("_labels\\.csv$", "");
                IJ.log("Traitement cube : " + cube);

                File romainFile = new File(ROMAIN_DIR, cube + "_annotations.csv");
                if (!romainFile.exists()) {
                    IJ.log("  Fichier romain manquant pour " + cube + ": " + romainFile.getName());
                    continue;
                }

                // Charger romain dans map clé=(X,Y) -> CODEOBJET
                Map<String, String> mapRomain = new HashMap<>();
                try (BufferedReader brR = new BufferedReader(new FileReader(romainFile))) {
                    String line;
                    // header skip
                    brR.readLine();
                    while ((line = brR.readLine()) != null) {
                        String[] parts = line.split(",");
                        if (parts.length < 5) continue;
                        String nomBoite = parts[0];
                        String codeRom = parts[1];
                        String x = parts[2], y = parts[3];
                        // on ignore Z pour le matching
                        String key = x + "," + y;
                        mapRomain.put(key, codeRom);
                    }
                }

                // Parcourir hani et matcher
                try (BufferedReader brH = new BufferedReader(new FileReader(haniFile))) {
                    String line;
                    // header skip
                    brH.readLine();
                    while ((line = brH.readLine()) != null) {
                        String[] parts = line.split(",");
                        if (parts.length < 5) continue;
                        String nomBoite = parts[0];
                        String codeHani = parts[1];
                        String x = parts[2], y = parts[3], z = parts[4];
                        String key = x + "," + y;
                        String codeRom = mapRomain.get(key);
                        if (codeRom == null) {
                            IJ.log("  Pas de correspondant pour HANI " + codeHani + " (" + key + ")");
                        } else {
                            // écrire ligne de correspondance
                            writer.write(String.join(",", nomBoite, codeHani, codeRom, x, y, z));
                            writer.write("\n");
                        }
                    }
                }
            }

            IJ.log("CSV de correspondance écrit dans " + OUT_CSV);
        } catch (IOException e) {
            IJ.error("Erreur I/O : " + e.getMessage());
        }
        IJ.log("=== Fin Script_Matching_Labels ===");
    }
}
