package io.github.rocsg.segmentation.aerenchyme.E_Extract_areas_with_morpho_and_corresponding_annotations;

import ij.IJ;
import ij.ImagePlus;
import ij.io.FileSaver;
import io.github.rocsg.segmentation.mlutils.MorphoUtils;

import java.io.File;

/**
 * Script_Alter_02.java
 *
 * Applique une fermeture morphologique (fermeture = dilation puis erosion)
 * de rayon 2 sur chaque segmentation binaire issue de Script_Alter_01.
 * Utilise MorphoUtils.dilationCircle2D() et MorphoUtils.erosionCircle2D().
 */
public class Script_Alter_02 {
    private static final String BASE_PATH    = "/Donnees/DD_CIRS626_DATA/Data_Cube_HTP";
    private static final String IN_DIR       = BASE_PATH + "/slice_stacks_segmented_tissue/raw";
    private static final String OUT_DIR      = BASE_PATH + "/slice_stacks_segmented_tissue_closed/raw";
    private static final int    RADIUS       = 2;

    public static void main(String[] args) {
        File rawBase = new File(IN_DIR);
        for (File aa : rawBase.listFiles(File::isDirectory)) {
            File cubeDir = aa;
            String cube = cubeDir.getName();
            for (File objDir : cubeDir.listFiles(File::isDirectory)) {
                String code = objDir.getName();
                File outObjDir = new File(OUT_DIR + "/" + cube + "/" + code);
                outObjDir.mkdirs();
                IJ.log("Processing morphological closing for cube="+cube+" code="+code);
                for (File segFile : objDir.listFiles((d,n)->n.endsWith(".tif"))) {
                    String name = segFile.getName();
                    IJ.log("  Closing " + name);
                    ImagePlus segImp = IJ.openImage(segFile.getAbsolutePath());
                    segImp.getProcessor().invert();
                    //Inverser l'image
                    if (segImp == null) {
                        IJ.error("Cannot open segmentation: " + segFile.getPath());
                        continue;
                    }
                    // dilation then erosion
                    ImagePlus dilated = MorphoUtils.dilationCircle2D(segImp, RADIUS);
                    ImagePlus closed  = MorphoUtils.erosionCircle2D(dilated, RADIUS);

                    // save result
                    String outPath = new File(outObjDir, name).getAbsolutePath();
                    new FileSaver(closed).saveAsTiff(outPath);
                }
            }
        }
        IJ.log("=== Morphological closing completed (radius="+RADIUS+") ===");
    }
}
