package io.github.rocsg.segmentation.aerenchyme.E_Extract_areas_with_morpho_and_corresponding_annotations;

import ij.IJ;
import ij.ImagePlus;
import ij.io.FileSaver;
import ij.process.ByteProcessor;
import io.github.rocsg.segmentation.mlutils.MorphoUtils;

import java.io.File;

/**
 * Script_Alter_08.java
 *
 * Pour chaque slice de la composante centrale ouverte (openedmonoCC),
 * applique une érosion de rayon 1, puis effectue un ET logique
 * avec la segmentation initiale pour obtenir le masque des lacunes.
 * Enregistre les résultats dans slice_stacks_segmented_tissue_lacuna/raw.
 */
public class Script_Alter_08 {
    private static final String BASE_PATH        = "/Donnees/DD_CIRS626_DATA/Data_Cube_HTP";
    private static final String IN_MONOCC_DIR    = BASE_PATH + "/slice_stacks_segmented_tissue_ALT7_single/raw";
    private static final String IN_SEGTISSUE_DIR = BASE_PATH + "/slice_stacks_segmented_tissue/raw";
    private static final String OUT_DIR          = BASE_PATH + "/slice_stacks_segmented_tissue_ALT8_lacuna/raw";
    private static final int    RADIUS           = 1;

    public static void main(String[] args) {
        File base = new File(IN_MONOCC_DIR);
        for (File cubeDir : base.listFiles(File::isDirectory)) {
            String cube = cubeDir.getName();
//            if(!cube.contains("04")) continue;
            for (File objDir : cubeDir.listFiles(File::isDirectory)) {
                String code = objDir.getName();
                //if(!code.contains("Cube_05_L10_C1")) continue;
                File outObj = new File(OUT_DIR + "/" + cube + "/" + code);
                outObj.mkdirs();
                IJ.log("Processing lacuna extraction for cube=" + cube + " code=" + code);
                for (File monoFile : objDir.listFiles((d,n)->n.endsWith(".tif"))) {
                    String name = monoFile.getName();
                    IJ.log("  Extracting lacuna from " + name);
                    // load opened central CC
                    ImagePlus monoImp = IJ.openImage(monoFile.getAbsolutePath());
                    if (monoImp == null) {
                        IJ.error("Cannot open monoCC: " + monoFile.getPath());
                        continue;
                    }
                    // erosion of monoCC
                    ImagePlus eroded = MorphoUtils.erosionCircle2D(monoImp, RADIUS);
                    ByteProcessor bpE = (ByteProcessor) eroded.getProcessor();
                    
                    // load initial segmentation
                    String segPath = IN_SEGTISSUE_DIR + "/" + cube + "/" + code + "/" + name;
                    ImagePlus segImp = IJ.openImage(segPath);
                    if (segImp == null) {
                        IJ.error("Cannot open initial seg: " + segPath);
                        continue;
                    }
                    ByteProcessor bpS = (ByteProcessor) segImp.getProcessor();

                    int w = bpS.getWidth(), h = bpS.getHeight();
                    ByteProcessor bpL = new ByteProcessor(w, h);
                    // logical AND: lacuna where both eroded central CC and initial seg are white
                    for (int y = 0; y < h; y++) {
                        for (int x = 0; x < w; x++) {
                            if (bpE.get(x,y) > 0 && bpS.get(x,y) > 0) {
                                bpL.set(x,y,255);
                            }
                        }
                    }
                    // save lacuna mask
                    String outPath = new File(outObj, name).getAbsolutePath();
                    new FileSaver(new ImagePlus(name, bpL)).saveAsTiff(outPath);
                }
            }
        }
        IJ.log("=== Lacuna masks generated (Alter_08) ===");
    }
}
