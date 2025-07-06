package io.github.rocsg.segmentation.aerenchyme.E_Extract_areas_with_morpho_and_corresponding_annotations;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.io.FileSaver;
import ij.process.ImageProcessor;

import java.io.File;

/**
 * Script_Alter_09.java
 *
 * Construit trois stacks globaux :
 *  - gaine_seg_all_monoCC.tif  : toutes les gaines segmentées (opened mono CC)
 *  - lacuna_seg_all.tif        : toutes les lacunes segmentées
 *  - raw_seg_all_z1.tif        : images raw correspondantes (seulement z=1)
 */
public class Script_Alter_Fin_stack {
    private static final String BASE_PATH      = "/Donnees/DD_CIRS626_DATA/Data_Cube_HTP";
    private static final String IN_GAINE_DIR   = BASE_PATH + "/slice_stacks_segmented_tissue_openedmonoCC/raw";
    private static final String IN_LACUNA_DIR  = BASE_PATH + "/slice_stacks_segmented_tissue_lacuna_cleaned/raw";
    private static final String IN_RAW_DIR     = BASE_PATH + "/slice_stacks/raw";

    private static final String OUT_GAINE_ALL  = BASE_PATH + "/gaine_seg_all.tif";
    private static final String OUT_LACUNA_ALL = BASE_PATH + "/lacuna_seg_all.tif";
    private static final String OUT_RAW_ALL    = BASE_PATH + "/raw_seg_all.tif";

    public static void main(String[] args) {
        IJ.log("=== Script_Alter_09: Creating combined stacks ===");

        ImageStack stackGaine = null;
        ImageStack stackLacuna = null;
        ImageStack stackRaw = null;
        boolean first = true;

        // Stack gaine
        File gaineBase = new File(IN_GAINE_DIR);
        for (File cubeDir : gaineBase.listFiles(File::isDirectory)) {
            String cube = cubeDir.getName();
            for (File objDir : cubeDir.listFiles(File::isDirectory)) {
                String code = objDir.getName();
                IJ.log("Adding gaine masks for " + cube + " / " + code);
                for (File file : objDir.listFiles((d,n)->n.endsWith(".tif"))) {
                    ImagePlus imp = IJ.openImage(file.getAbsolutePath());
                    if (imp == null) continue;
                    if (first) {
                        stackGaine = new ImageStack(imp.getWidth(), imp.getHeight());
                        first=false;
                    }
                    // assume single-slice
                    ImageProcessor ip = imp.getProcessor();
                    stackGaine.addSlice(cube + "_" + code + "_" + file.getName(), ip.duplicate());
                }
            }
        }

        // Stack lacuna
        first = true;
        File lacunaBase = new File(IN_LACUNA_DIR);
        for (File cubeDir : lacunaBase.listFiles(File::isDirectory)) {
            String cube = cubeDir.getName();
            for (File objDir : cubeDir.listFiles(File::isDirectory)) {
                String code = objDir.getName();
                IJ.log("Adding lacuna masks for " + cube + " / " + code);
                for (File file : objDir.listFiles((d,n)->n.endsWith(".tif"))) {
                    ImagePlus imp = IJ.openImage(file.getAbsolutePath());
                    if (imp == null) continue;
                    if (first) {
                        stackLacuna = new ImageStack(imp.getWidth(), imp.getHeight());
                        first=false;
                    }
                    ImageProcessor ip = imp.getProcessor();
                    stackLacuna.addSlice(cube + "_" + code + "_" + file.getName(), ip.duplicate());
                }
            }
        }

        first = true;
         // Stack raw (z=1)
        File rawBase = new File(IN_RAW_DIR);
        for (File cubeDir : rawBase.listFiles(File::isDirectory)) {
            String cube = cubeDir.getName();
            for (File objDir : cubeDir.listFiles(File::isDirectory)) {
                String code = objDir.getName();
                IJ.log("Adding raw slices for " + cube + " / " + code);
                for (File file : objDir.listFiles((d,n)->n.endsWith(".tif"))) {
                    ImagePlus imp = IJ.openImage(file.getAbsolutePath());
                    if (imp == null) continue;
                    if (first) {
                        stackRaw = new ImageStack(imp.getWidth(), imp.getHeight());
                        first = false;
                    }
                    // take z=1 only
                    imp.setSlice(1);
                    ImageProcessor ip = imp.getProcessor();
                    stackRaw.addSlice(cube + "_" + code + "_" + file.getName(), ip.duplicate());
                }
            }
        }

        // Save stacks
        if (stackGaine != null) {
            new FileSaver(new ImagePlus("gaine_seg_all_monoCC", stackGaine)).saveAsTiffStack(OUT_GAINE_ALL);
            IJ.log("Saved gaine stack: " + OUT_GAINE_ALL);
        }
        if (stackLacuna != null) {
            new FileSaver(new ImagePlus("lacuna_seg_all", stackLacuna)).saveAsTiffStack(OUT_LACUNA_ALL);
            IJ.log("Saved lacuna stack: " + OUT_LACUNA_ALL);
        }
        if (stackRaw != null) {
            new FileSaver(new ImagePlus("raw_seg_all_z1", stackRaw)).saveAsTiffStack(OUT_RAW_ALL);
            IJ.log("Saved raw_z1 stack: " + OUT_RAW_ALL);
        }
        IJ.log("=== Combined stacks created ===");
    }
}
