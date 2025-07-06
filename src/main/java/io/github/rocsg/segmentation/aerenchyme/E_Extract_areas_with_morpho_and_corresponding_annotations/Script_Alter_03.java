package io.github.rocsg.segmentation.aerenchyme.E_Extract_areas_with_morpho_and_corresponding_annotations;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.io.FileSaver;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import ij.plugin.filter.EDM;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Script_Alter_03.java
 *
 * Applique un remplissage des trous (fill holes) sur chaque image binaire,
 * puis ne conserve qu'une seule composante connexe :
 * celle dont le barycentre est le plus proche du centre de l'image.
 */
public class Script_Alter_03 {
    private static final String BASE_PATH    = "/Donnees/DD_CIRS626_DATA/Data_Cube_HTP";
    private static final String IN_DIR       = BASE_PATH + "/slice_stacks_segmented_tissue_closed/raw";
    private static final String OUT_DIR      = BASE_PATH + "/slice_stacks_segmented_tissue_fill_holes/raw";
    private static final int    PATCH_SIZE   = 256;

    public static void main(String[] args) {
        File rawBase = new File(IN_DIR);
        for (File cubeDir : rawBase.listFiles(File::isDirectory)) {
            String cube = cubeDir.getName();

            for (File objDir : cubeDir.listFiles(File::isDirectory)) {
                String code = objDir.getName();
                if(!code.contains("Cube_05_L10_C1")) continue;
                File outObjDir = new File(OUT_DIR + "/" + cube + "/" + code);
                outObjDir.mkdirs();
                IJ.log("Processing fill+cc for cube="+cube+" code="+code);
                for (File binFile : objDir.listFiles((d,n)->n.endsWith(".tif"))) {
                    String name = binFile.getName();
                    IJ.log("  Filling and keeping central CC: " + name);
                    ImagePlus imp = IJ.openImage(binFile.getAbsolutePath());
                    if (imp == null) {
                        IJ.error("Cannot open binary image: " + binFile.getPath());
                        continue;
                    }
                    // fill holes using ImageJ EDM plugin
                    imp.getProcessor().invert();
                    IJ.run(imp, "Fill Holes", "");
                    imp.getProcessor().invert();
 
                    String outPath = new File(outObjDir, name).getAbsolutePath();
                    new FileSaver(imp).saveAsTiff(outPath);
                }
            }
        }
        IJ.log("=== Fill+central component filter completed ===");
    }
}
