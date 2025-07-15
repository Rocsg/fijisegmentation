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
public class Script_panel2 {
    private static final String BASE_PATH        = "/Donnees/DD_CIRS626_DATA/Data_Cube_HTP";
    private static final String LIST_CSV         = BASE_PATH + "/list_codes_full.csv"; // une colonne header Code
    private static final String MAP_CSV          = BASE_PATH + "/correspondance_labels_hani_romain.csv";
    private static final String RAW_DIR          = BASE_PATH + "/raw_investigation";
    private static final String SEG_DIR          = BASE_PATH + "/seg_investigation";
    private static final String OUT_SEG          = BASE_PATH + "/panel_seg.tif";
    private static final String OUT_RAW          = BASE_PATH + "/panel_raw.tif";

    static boolean isTemoinMoins(int code){
        int[]tab=new int[]{ 873,871,872,874,875,876,711 ,669,810,877,878,468,135,239,362};
        for(int i=0;i<tab.length;i++)
            if(tab[i]==code)return true;
        return false;
    }
    static boolean isTemoinPlus(int code){
        int[]tab=new int[]{ 768,656,503 ,864,428,160,266,332};
        for(int i=0;i<tab.length;i++)
            if(tab[i]==code)return true;
        return false;
    }

    static boolean isParent(int code){
        int[]tab=new int[]{714 ,715,722,724,760,615,601,610 ,643,568,648,501,547,510,525,833,802,839,804,819,448,429,430,408,435,117,158,125,147,169,208,219,265,250,260,370,366,340,331};
        for(int i=0;i<tab.length;i++)
            if(tab[i]==code)return true;
        return false;
    }


    public static void main(String[] args) {
        ImageJ ij=new ImageJ();
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
        List<Double> ratios = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(LIST_CSV))) {
            System.out.println(LIST_CSV);
            String header = br.readLine();
            String line;
            while ((line=br.readLine())!=null) {
                System.out.println(line);
                String[] p = line.split(",");
                codes.add(p[0]);
                ratios.add(Double.parseDouble(p[1]));
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
        ImagePlus []imgTotSeg=new ImagePlus[11];
        ImagePlus []imgTotRaw=new ImagePlus[11];
        for(int sl=1;sl<12;sl++){
            // préparer stacks
            ImageStack stackRaw = null;
            ImageStack stackSeg = null;
            int w=0, h=0;
            Opener opener = new Opener();
            for (int i=0;i<codes.size();i++) { 
                String codeRom = codes.get(i);
                double ratio   = ratios.get(i);
                String codeHani = rom2hani.get(codeRom);
                if (codeHani==null) codeHani = "";
                // open raw
                String rawPath = RAW_DIR + "/" + codeRom + ".tif";
                ImagePlus impRaw = opener.openImage(rawPath);
                if (impRaw==null) { IJ.log("Raw missing: " + rawPath); continue; }
                impRaw.setSlice(sl);
                ImageProcessor ipRaw = impRaw.getProcessor().duplicate();
                // annotate
                ipRaw.setFont(new Font("SansSerif", Font.BOLD, 11));
                ipRaw.setColor(Color.yellow);
                ipRaw.drawString(codeRom.replace("Cube_", ""), 5, 15);
                ipRaw.setColor(Color.yellow);
                ipRaw.drawString(codeHani, 5, 30);
                ipRaw.setFont(new Font("SansSerif", Font.BOLD, 11));
                ipRaw.drawString(""+ratio, 80, 110);
                ipRaw.setFont(new Font("SansSerif", Font.BOLD, 13));
                if(isParent(Integer.parseInt(codeHani))){
                    ipRaw.setColor(Color.blue);
                    ipRaw.drawString("PARENT", 15, 110);
    
                }
                if(isTemoinPlus(Integer.parseInt(codeHani))){
                    ipRaw.setColor(Color.green);
                    ipRaw.drawString("AA+", 15, 120);
    
                }
                if(isTemoinMoins(Integer.parseInt(codeHani))){
                    ipRaw.setColor(Color.red);
                    ipRaw.drawString("AA-", 15, 120);
    
                }
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
                impSeg.setSlice(sl);
                ImageProcessor ipSeg = impSeg.getProcessor().duplicate();
                // annotate
                ipSeg.setFont(new Font("SansSerif", Font.BOLD, 11));
                ipSeg.setColor(Color.yellow);
                ipSeg.drawString(codeRom.replace("Cube_", ""), 5, 15);
                ipSeg.setColor(Color.yellow);
                ipSeg.drawString(codeHani, 5, 30);
                ipSeg.setFont(new Font("SansSerif", Font.BOLD, 11));
                ipSeg.setColor(Color.pink);
                ipSeg.drawString(""+ratio, 80, 120);
                ipSeg.setColor(Color.yellow);
                ipSeg.setFont(new Font("SansSerif", Font.BOLD, 23));
                if(isParent(Integer.parseInt(codeHani))){
                    ipSeg.setColor(Color.blue);
                    ipSeg.drawString("PARENT", 15, 110);
    
                }
                if(isTemoinPlus(Integer.parseInt(codeHani))){
                    ipSeg.setColor(Color.green);
                    ipSeg.drawString("AA+", 15, 120);
    
                }
                if(isTemoinMoins(Integer.parseInt(codeHani))){
                    ipSeg.setColor(Color.red);
                    ipSeg.drawString("AA-", 15, 120);
                }
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
                mm.makeMontage(montRaw, cols, rows, 1.0, 1, n, 1, 5, false);
                imgTotRaw[sl-1] = IJ.getImage();
//                montR.show();
            }
            if (stackSeg!=null) {
                ImagePlus montSeg = new ImagePlus("Montage Seg", stackSeg);
                mm.makeMontage(montSeg, cols, rows, 1.0, 1, n, 1, 5, false);
                imgTotSeg[sl-1] = IJ.getImage();
 //                montS.show();
            }
        }
        //Creer une image stack qui va accueillir les 11 slices de imgTotSeg
        ImageStack stackSeg = new ImageStack(imgTotSeg[0].getWidth(), imgTotSeg[0].getHeight());
        ImageStack stackRaw = new ImageStack(imgTotSeg[0].getWidth(), imgTotSeg[0].getHeight());
        for (int i = 0; i < 11; i++) {
            stackSeg.addSlice(String.valueOf(i), imgTotSeg[i].getProcessor().duplicate());
            stackRaw.addSlice(String.valueOf(i), imgTotRaw[i].getProcessor().duplicate());
        }
        ImagePlus montageSeg = new ImagePlus("Montage Seg", stackSeg);
        ImagePlus montageRaw = new ImagePlus("Montage Raw", stackRaw);
        for(int i=0;i<11;i++){
            imgTotRaw[i].close();
            imgTotSeg[i].close();
        }
        IJ.saveAsTiff(montageSeg,OUT_SEG);
        IJ.saveAsTiff(montageRaw,OUT_RAW);        
        IJ.log("=== Script_panel done ===");
    }
}
