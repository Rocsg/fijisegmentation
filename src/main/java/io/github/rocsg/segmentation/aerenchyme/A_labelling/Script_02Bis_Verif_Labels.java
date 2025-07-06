package io.github.rocsg.segmentation.aerenchyme.A_labelling;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.gui.Overlay;
import ij.gui.TextRoi;
import ij.plugin.PlugIn;
import ij.process.ImageProcessor;

import java.awt.Color;
import java.awt.Font;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public class Script_02Bis_Verif_Labels implements PlugIn {
    private static final String BASE_PATH = "/Donnees/DD_CIRS626_DATA/Data_Cube_HTP";
    private ImagePlus imp;
    private String boxName;


    public static void main(String[] args) {
        ImageJ ij = new ImageJ();
        new Script_02Bis_Verif_Labels().run("");
    }

    @Override
    public void run(String arg) {
        // 1. Demande du nom de la boîte
        boxName = IJ.getString("Nom de la boîte pour vérification", "Cube_01");
        if (boxName == null || boxName.trim().isEmpty()) {
            IJ.error("Nom de la boîte invalide.");
            return;
        }

        // 2. Ouverture de l'image volumique
        String imgPath = BASE_PATH + "/cropimages/" + boxName + "_crop.tif";
        imp = IJ.openImage(imgPath);
        if (imp == null) {
            IJ.error("Impossible d'ouvrir l'image : " + imgPath);
            return;
        }
        imp.show();

        // 3. Lecture du CSV
        String csvPath = BASE_PATH + File.separator + "labels_hani" + File.separator + boxName + "_labels.csv";
        File csvFile = new File(csvPath);
        if (!csvFile.exists()) {
            IJ.error("Fichier CSV introuvable : " + csvPath);
            return;
        }

        Overlay overlay = new Overlay();
        overlay.setStrokeColor(java.awt.Color.RED);
        overlay.setLabelFont(new Font("SansSerif", Font.PLAIN, 12));

        //Afficher en printout le path du csv
        
        try (BufferedReader br = new BufferedReader(new FileReader(csvFile))) {
            String line = br.readLine(); // skip header
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length < 5) continue;
                String codeObj = parts[1];
                int x = Integer.parseInt(parts[2]);
                int y = Integer.parseInt(parts[3]);
                int z = Integer.parseInt(parts[4]);

                imp.setSlice(z);
                ImageProcessor ip = imp.getProcessor();
                ip.setMinAndMax(0, 500);
                ip.setColor(java.awt.Color.WHITE);
                int radius = 10;
                ip.drawOval(x - radius, y - radius, radius * 2, radius * 2);
                ip.setFont(new Font("SansSerif", Font.PLAIN, 45));
                ip.setColor(java.awt.Color.WHITE);

                ip.drawString(codeObj, x-55, y+10);
                imp.updateAndDraw();                System.out.println(codeObj + " (" + x + ", " + y + ", " + z + ")");
            }
        } catch (IOException e) {
            IJ.error("Erreur lecture CSV : " + e.getMessage());
            return;
        }

        // 4. Application de l'overlay
        imp.setOverlay(overlay);
        IJ.log("Vérification prête : codes superposés.");
    }
}
