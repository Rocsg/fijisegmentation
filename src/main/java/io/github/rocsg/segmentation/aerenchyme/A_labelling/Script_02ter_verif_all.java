package io.github.rocsg.segmentation.aerenchyme.A_labelling;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.gui.GenericDialog;
import ij.process.ImageProcessor;
import java.awt.Color;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

/**
 * Script_02_ter_verif_all.java
 *
 * Invite l'utilisateur à saisir le nom d'un cube (ex. "Cube_01").
 * Charge le CSV de correspondance labels_hani vs labels_romain.
 * Pour chaque ligne correspondant au cube sélectionné, ouvre l'image raw,
 * positionne la tranche Z, et affiche le code HANI et le code ROMAIN
 * superposés aux coordonnées X,Y (HANI en blanc, ROMAIN en rouge).
 */
public class Script_02ter_verif_all {
    private static final String BASE_PATH = "/Donnees/DD_CIRS626_DATA/Data_Cube_HTP";
    private static final String CSV_PATH  = BASE_PATH + "/correspondance_labels_hani_romain.csv";
    private static final String RAW_DIR   = BASE_PATH + "/cropimages";

    public static void main(String[] args) {
        ImageJ ij=new ImageJ();
        // Demande le nom du cube
        GenericDialog gd = new GenericDialog("Vérifier labels HANI vs ROMAIN");
        gd.addStringField("Cube name:", "Cube_01");
        gd.showDialog();
        if (gd.wasCanceled()) {
            IJ.log("Script annulé par l'utilisateur.");
            return;
        }
        String cube = gd.getNextString().trim();
        IJ.log("Cube sélectionné: " + cube);
        boolean zFixed=false;
        String rawPath = RAW_DIR + "/" + cube + "_crop.tif";
        ImagePlus imp = IJ.openImage(rawPath);
        if (imp == null) {
            IJ.error("Impossible d'ouvrir l'image raw: " + rawPath);
            return;
        }
        imp.show();
        ImageProcessor ip = imp.getProcessor();
        ip.setMinAndMax(0, 500);
        ip.setColor(Color.white);
        ip.setFont(imp.getProcessor().getFont().deriveFont(28f));

        // Ouvre le CSV de correspondance
        try (BufferedReader br = new BufferedReader(new FileReader(CSV_PATH))) {
            String line = br.readLine(); // header
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length < 6) continue;
                String nomBoite = parts[0];
                if (!nomBoite.equals(cube)) continue;
                String codeHani = parts[1];
                String codeRom  = parts[2];
                int x = Integer.parseInt(parts[3]);
                int y = Integer.parseInt(parts[4]);
                int z = Integer.parseInt(parts[5]);
                if(!zFixed) {
                    imp.setSlice(z);
                    zFixed=true;
                }
                // Superpose les labels
                ip.drawString(codeHani, x-5, y-50);
                ip.setColor(Color.WHITE);
                ip.drawString(codeRom, x-5, y - 20);
                // Affiche
                IJ.log(String.format("Affiché %s: HANI=%s ROMAIN=%s (z=%d) at (%d,%d)", cube, codeHani, codeRom, z, x, y));
            }
        } catch (IOException e) {
            IJ.error("Erreur lecture CSV: " + e.getMessage());
        }
        IJ.log("=== Vérification terminée ===");
    }
}
