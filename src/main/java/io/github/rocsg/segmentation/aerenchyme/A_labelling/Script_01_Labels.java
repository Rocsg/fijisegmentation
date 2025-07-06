package io.github.rocsg.segmentation.aerenchyme.A_labelling;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.gui.ImageCanvas;
import ij.plugin.PlugIn;
import ij.process.ImageProcessor;
import io.github.rocsg.fijiyama.common.VitimageUtils;
import ome.xml.model.primitives.Color;

import java.awt.Font;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
public class Script_01_Labels implements PlugIn {
    private static final String BASE_PATH = "/Donnees/DD_CIRS626_DATA/Data_Cube_HTP";
    private ImagePlus imp;
    private PrintWriter csvWriter;
    private String boxName;
    private int currentRow = 1;
    private int currentCol = 1;

    public Script_01_Labels() {}

    public static void main(String[] args) {
        ImageJ ij=new ImageJ();
        new Script_01_Labels().run("");
    }

    @Override
    public void run(String arg) {
        // 1. Demande du nom de la boîte
        boxName = IJ.getString("Nom de la boîte (sans suffixe)", "Cube_01");
        if (boxName == null || boxName.trim().isEmpty()) {
            IJ.error("Nom de la boîte invalide.");
            return;
        }

        // 2. Ouverture de l'image volumique
        String imgPath = BASE_PATH + "/cropimages/" + boxName + "_crop.tif";
        System.out.println(imgPath);
        imp = IJ.openImage(imgPath);
        if (imp == null) {
            IJ.error("Impossible d'ouvrir l'image : " + imgPath);
            return;
        }
        imp.show();
        for(int z=1;z<=imp.getNSlices();z++){

            imp.setSlice(z);
            imp.getProcessor().setColor(java.awt.Color.white);
            imp.getProcessor().drawLine(100, 0, 100, imp.getHeight());
            imp.updateAndDraw();
        }
        imp.setSlice(imp.getNSlices()/2);
        imp.updateAndDraw();

        // 3. Préparation du CSV de sortie
        try {
            File outFile = new File(BASE_PATH+"/labels", boxName + "_annotations.csv");
            csvWriter = new PrintWriter(new FileWriter(outFile, false));
            csvWriter.println("NOMBOITE,CODEOBJET,X,Y,Z");
        } catch (IOException e) {
            IJ.error("Erreur création CSV : " + e.getMessage());
            imp.close();
            return;
        }

        // 4. Installation du listener souris sur le canvas
        ImageCanvas canvas = imp.getCanvas();
        canvas.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                // Conversion des coordonnées fenetre -> coordonnées image
                int ix = canvas.offScreenX(e.getX());
                int iy = canvas.offScreenY(e.getY());
                int iz = imp.getCurrentSlice();

                if (ix < 100) {
                    // Changement de ligne
                    currentRow++;
                    currentCol = 1;
                    IJ.log("Nouvelle ligne : " + currentRow);
                }
                else if (iy < 50) {
                    // Changement de ligne
                    currentRow++;
                    currentCol = 1;
                    IJ.log("Nouvelle ligne : " + currentRow);
                } else {
                    // Enregistrement de l'annotation
                    String codeObj = String.format("%s_L%d_C%d", boxName, currentRow, currentCol);
                    csvWriter.printf("%s,%s,%d,%d,%d\n", boxName, codeObj, ix, iy, iz);
                    csvWriter.flush();
                    ImageProcessor ip = imp.getProcessor();
                    ip.setColor(java.awt.Color.WHITE);
                    int radius = 10;
                    ip.drawOval(ix - radius, iy - radius, radius * 2, radius * 2);
                    ip.setFont(new Font("SansSerif", Font.PLAIN, 20));
                    ip.setColor(java.awt.Color.WHITE);

                    ip.drawString(codeObj, ix-55, iy+10);
                    imp.updateAndDraw();
                    IJ.log("Enregistré : " + codeObj + " @ (" + ix + ", " + iy + ", " + iz + ")");
                    currentCol++;
                }
            }
        });

        IJ.log("Plugin prêt. Clics dans <50px à gauche pour changer de ligne.");
    }
}


