package io.github.rocsg.segmentation.aerenchyme.F_compute_traits_and_evaluate;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.plugin.FolderOpener;
import io.github.rocsg.fijiyama.common.VitimageUtils;
import ij.io.FileSaver;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

/**
 * Script_assemble_seg_investigation.java
 *
 * Génère, pour chaque feuille référencée dans le CSV de correspondance,
 * un composite RGB (raw/lacuna/gaine) identique à Investigate_one_leaf,
 * mais pour toutes les feuilles en une seule passe.
 * Sauvegarde chaque composite dans :
 *   BASE_PATH/seg_investigation/<codeRom>.tif
 */
public class Script_produce_seg_and_raw_investigation {
    private static final String BASE_PATH        = "/Donnees/DD_CIRS626_DATA/Data_Cube_HTP";
    private static final String MAP_CSV          = BASE_PATH + "/correspondance_labels_hani_romain.csv";
    private static final String PATCH_RAW_DIR    = BASE_PATH + "/patches_128_aug/raw";
    private static final String STACK_LACUNA_DIR = BASE_PATH + "/slice_stacks_segmented_tissue_ALT9_lacuna_cleaned/raw";
    private static final String STACK_GAINE_DIR  = BASE_PATH + "/slice_stacks_segmented_tissue_ALT7_single/raw";
    private static final String OUT_DIR          = BASE_PATH + "/seg_investigation";
    private static final String OUT_DIR_RAW          = BASE_PATH + "/raw_investigation";
    private static final int    NORMALIZE_MIN    = 0;
    private static final int    NORMALIZE_MAX    = 150;

    public static void main(String[] args) {
        ImageJ  ij=new ImageJ();
        IJ.log("=== Script_assemble_seg_investigation ===");
        File outFolder = new File(OUT_DIR);
        if (!outFolder.exists() && !outFolder.mkdirs()) {
            IJ.error("Impossible de créer le dossier de sortie : " + OUT_DIR);
            return;
        }

        try (BufferedReader br = new BufferedReader(new FileReader(MAP_CSV))) {
            String line = br.readLine(); // header
            FolderOpener opener = new FolderOpener();
            while ((line = br.readLine()) != null) {
                String[] p = line.split(",");
                if (p.length < 3) continue;
                String cube     = p[0];
                String codeHani = p[1];
                String codeRom  = p[2];
                IJ.log("Processing leaf: cube=" + cube + " rom=" + codeRom + " hani=" + codeHani);

                // Raw patch
                File rawDir = new File(PATCH_RAW_DIR, cube);
                File[] raws = rawDir.listFiles((d,n) -> n.startsWith(codeRom) && n.contains("_rot000.tif"));
                if (raws == null || raws.length == 0) {
                    IJ.log("  Raw not found for " + codeRom);
                    continue;
                }
                System.out.println(raws[0].getAbsolutePath());
                ImagePlus stackRaw = IJ.openImage(raws[0].getAbsolutePath());
                new FileSaver(stackRaw).saveAsTiffStack(OUT_DIR_RAW+"/"+codeRom+".tif");
                
                
                // Segmented stacks
                System.out.println(new File(STACK_LACUNA_DIR, cube + "/" + codeRom).getAbsolutePath());
                ImagePlus stackLacuna = opener.open(new File(STACK_LACUNA_DIR, cube + "/" + codeRom).getAbsolutePath());
                stackLacuna.show();
                System.out.println(new File(STACK_GAINE_DIR,  cube + "/" + codeRom).getAbsolutePath());
                ImagePlus stackGaine  = opener.open(new File(STACK_GAINE_DIR,  cube + "/" + codeRom).getAbsolutePath());
                stackGaine.show();
                // Normalize raw & convert
                System.out.println("Toto 1");
                stackRaw.getProcessor().setMinAndMax(NORMALIZE_MIN, NORMALIZE_MAX);
                IJ.run(stackRaw, "8-bit", "");

                System.out.println("Toto 2");
                // Scale masks
                IJ.run(stackLacuna, "Multiply...", "value=0.33 stack");
                IJ.run(stackGaine,  "Multiply...", "value=0.33 stack");
                System.out.println("Toto 3");

                ImagePlus nullImage = VitimageUtils.nullImage(stackRaw);
                ImagePlus rgb=VitimageUtils.compositeRGBLByte(stackGaine, stackLacuna, nullImage, stackRaw, 0.9,1.2,1,1);
                // Merge channels: red=gaine, green=lacuna, blue=raw
                /*IJ.run(stackRaw, "Merge Channels...",
                    "c1=" + stackGaine.getTitle() +
                    " c2=" + stackLacuna.getTitle() +
                    " c4=" + stackRaw.getTitle() +
                    " c5=" + stackGaine.getTitle() +
                    " create");*/
                rgb.show();
                IJ.run(rgb, "RGB Color", "slices slices");
                ImagePlus rgb2=IJ.getImage();
                String outPath = OUT_DIR + "/" + codeRom + ".tif";
                new FileSaver(rgb2).saveAsTiffStack(outPath);
                IJ.log("  Saved: " + outPath);
                rgb2.changes=false;
                rgb2.close();

                stackRaw.changes=false;
                stackRaw.close();

                stackLacuna.changes=false;
                stackLacuna.close();

                stackGaine.changes=false;
                stackGaine.close();
            }
            IJ.log("=== All composites saved in " + OUT_DIR + " ===");
        } catch (IOException e) {
            IJ.error("IO Error: " + e.getMessage());
        }
    }
}
