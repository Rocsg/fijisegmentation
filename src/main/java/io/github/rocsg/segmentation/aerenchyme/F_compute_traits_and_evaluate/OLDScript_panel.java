package io.github.rocsg.segmentation.aerenchyme.F_compute_traits_and_evaluate;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.Overlay;
import ij.gui.TextRoi;
import ij.io.Opener;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;
import ij.plugin.MontageMaker;

import java.awt.Font;
import java.awt.Color;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Script_panel.java
 *
 * Ouvre une liste de codes specimen depuis un CSV (une colonne, header "Code").
 * Pour chaque code (codeRom), récupère codeHani depuis le CSV de correspondance,
 * charge la première slice de raw_investigation/<codeRom>.tif et seg_investigation/<codeRom>.tif,
 * y superpose le codeRom en haut et codeHani en bas,
 * assemble tous ces images en montage (colonnes/rows ~16:9) pour raw et seg,
 * et affiche les deux montages (sans les sauvegarder).
 */
public class OLDScript_panel {
    private static final String BASE_PATH        = "/Donnees/DD_CIRS626_DATA/Data_Cube_HTP";
    private static final String LIST_CSV         = BASE_PATH + "/list_codes.csv"; // une colonne header Code
    private static final String MAP_CSV          = BASE_PATH + "/correspondance_labels_hani_romain.csv";
    private static final String RAW_DIR          = BASE_PATH + "/raw_investigation";
    private static final String SEG_DIR          = BASE_PATH + "/seg_investigation";

    public static void main(String[] args) {
        ImageJ  ij=new ImageJ();
        IJ.log("=== Script_panel ===");
        // lire mapping rom->hani
        java.util.Map<String,String> rom2hani = new java.util.HashMap<>();
        try (BufferedReader br = new BufferedReader(new FileReader(MAP_CSV))) {
            String line = br.readLine();
            while ((line = br.readLine())!=null) {
                String[] p = line.split(",");
                if (p.length<3) continue;
                rom2hani.put(p[2], p[1]);
            }
        } catch (IOException e) {
            IJ.error("Erreur lecture mapping: " + e.getMessage());
            return;
        }
        // lire liste de codes
        List<String> codes = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(LIST_CSV))) {
            String header = br.readLine();
            String line;
            while ((line=br.readLine())!=null) {
                codes.add(line.trim());
            }
        } catch (IOException e) {
            IJ.error("Erreur lecture liste codes: " + e.getMessage());
            return;
        }
        int n = codes.size();
        if (n==0) {
            IJ.error("Aucune entrée dans " + LIST_CSV);
            return;
        }
        // préparer stacks
        ImageStack stackRaw = null;
        ImageStack stackSeg = null;
        int w=0, h=0;
        Opener opener = new Opener();
        for (String codeRom : codes) {
            String codeHani = rom2hani.get(codeRom);
            if (codeHani==null) codeHani = "";
            // open raw
            String rawPath = RAW_DIR + "/" + codeRom + ".tif";
            ImagePlus impRaw = opener.openImage(rawPath);
            if (impRaw==null) { IJ.log("Raw missing: " + rawPath); continue; }
            impRaw.setSlice(1);
            ImageProcessor ipRaw = impRaw.getProcessor().duplicate();
            // annotate
            ipRaw.setFont(new Font("SansSerif", Font.BOLD, 9));
            ipRaw.setColor(Color.yellow);
            ipRaw.drawString(codeRom.replace("Cube_", ""), 5, 25);
            ipRaw.setColor(Color.yellow);
            ipRaw.drawString(codeHani, 5, 40);
            // init stack
            if (stackRaw==null) {
                w = ipRaw.getWidth();
                h = ipRaw.getHeight();
                stackRaw = new ImageStack(w,h);
            }
            stackRaw.addSlice(codeRom, ipRaw);
            // open seg
            String segPath = SEG_DIR + "/" + codeRom + ".tif";
            ImagePlus impSeg = opener.openImage(segPath);
            if (impSeg==null) { IJ.log("Seg missing: " + segPath); continue; }
            impSeg.setSlice(1);
            ImageProcessor ipSeg = impSeg.getProcessor().duplicate();
            // annotate
            ipSeg.setFont(new Font("SansSerif", Font.BOLD, 9));
            ipSeg.setColor(Color.yellow);
            ipSeg.drawString(codeRom.replace("Cube_", ""), 5, 25);
            ipSeg.setColor(Color.yellow);
            ipSeg.drawString(codeHani, 5, 40);
            if (stackSeg==null) {
                // assume same dims
                stackSeg = new ImageStack(w,h);
            }
            stackSeg.addSlice(codeRom, ipSeg);
        }
        // montage dimensions ~16:9
        double ratio = 16.0/9.0;
        int cols = (int)Math.ceil(Math.sqrt(n*ratio));
        int rows = (int)Math.ceil((double)n/cols);
        MontageMaker mm = new MontageMaker();
        if (stackRaw!=null) {
            ImagePlus montRaw = new ImagePlus("Montage Raw", stackRaw);
            mm.makeMontage(montRaw, cols, rows, 1.0, 1, n, 1, 0, false);
            ImagePlus montR = IJ.getImage();
            montR.show();
        }
        if (stackSeg!=null) {
            ImagePlus montSeg = new ImagePlus("Montage Seg", stackSeg);
            mm.makeMontage(montSeg, cols, rows, 1.0, 1, n, 1, 0, false);
            ImagePlus montS = IJ.getImage();
            montS.show();
        }
        IJ.log("=== Script_panel done ===");
    }
}
