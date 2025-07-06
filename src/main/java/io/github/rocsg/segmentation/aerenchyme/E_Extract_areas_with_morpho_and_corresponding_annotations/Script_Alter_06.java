package io.github.rocsg.segmentation.aerenchyme.E_Extract_areas_with_morpho_and_corresponding_annotations;

import ij.IJ;
import ij.ImagePlus;
import ij.io.FileSaver;
import io.github.rocsg.segmentation.mlutils.MorphoUtils;

import java.io.File;

/**
 * Script_Alter_06.java
 *
 * Applique une ouverture morphologique (érosion puis dilation) de rayon 8
 * sur les images issues de Script_Alter_04 (slice_stacks_segmented_tissue_final/raw).
 * Sauvegarde les résultats dans slice_stacks_segmented_tissue_opened/raw.
 */
public class Script_Alter_06 {
    private static final String BASE_PATH   = "/Donnees/DD_CIRS626_DATA/Data_Cube_HTP";
    private static final String IN_DIR      = BASE_PATH + "/slice_stacks_segmented_tissue_ALT4_single/raw";
    private static final String OUT_DIR     = BASE_PATH + "/slice_stacks_segmented_tissue_ALT6_closure/raw";
    private static final int    RADIUS      = 11;

    public static void main(String[] args) {
        File root = new File(IN_DIR);
        for (File cubeDir : root.listFiles(File::isDirectory)) {
            String cube = cubeDir.getName();
            
            for (File objDir : cubeDir.listFiles(File::isDirectory)) {
                String code = objDir.getName();
//                if(!code.contains("Cube_05_L10_C1")) continue;
                File outObj = new File(OUT_DIR + "/" + cube + "/" + code);
                outObj.mkdirs();
                IJ.log("Applying opening for cube=" + cube + " code=" + code);
                for (File file : objDir.listFiles((d,n) -> n.endsWith(".tif"))) {
                    String name = file.getName();
                    IJ.log("  Opening " + name);
                    ImagePlus imp = IJ.openImage(file.getAbsolutePath());
                    if (imp == null) {
                        IJ.error("Cannot open: " + file.getPath());
                        continue;
                    }
                    // erosion then dilation
                    ImagePlus eroded = MorphoUtils.erosionCircle2D(imp, RADIUS);
                    ImagePlus opened = MorphoUtils.dilationCircle2D(eroded, RADIUS);
                    // save
                    String outPath = new File(outObj, name).getAbsolutePath();
                    new FileSaver(opened).saveAsTiff(outPath);
                }
            }
        }
        IJ.log("=== Morphological opening (Alter_06) completed ===");
    }
}
