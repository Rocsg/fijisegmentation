package io.github.rocsg.segmentation.aerenchyme.C_Weka_prepare_stack_and_train_exhaustive;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.io.FileSaver;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;

import java.io.File;

public class Script_06_Generate_Examples {
    private static final String BASE_PATH = "/Donnees/DD_CIRS626_DATA/Data_Cube_HTP";
    private static final String IN_DIR = BASE_PATH + "/patches_128_aug";
    private static final String OUT_GAINE = BASE_PATH + "/exemples_128_gaine_aug";
    private static final String OUT_LACUNA = BASE_PATH + "/exemples_128_lacuna_aug";
    private static final int EROSION_RADIUS = 15;  // 10 pixels

    public static void main(String[] args) {
        // Traitement pour les patches augmentés de maskleaf (gaine) et masklacuna (lacuna)
        String[] cubes = new File(IN_DIR + "/maskleaf").list((d, name) -> new File(d, name).isDirectory());
        if (cubes == null) {
            IJ.error("Pas de dossiers maskleaf trouvés dans " + IN_DIR + "/maskleaf");
            return;
        }
        for (String cube : cubes) {
            File leafDir   = new File(IN_DIR + "/maskleaf/"   + cube);
            File lacunaDir = new File(IN_DIR + "/masklacuna/" + cube);
            File outGaineDir  = new File(OUT_GAINE,   cube);
            File outLacunaDir = new File(OUT_LACUNA,  cube);
            outGaineDir.mkdirs();
            outLacunaDir.mkdirs();

            // Pour chaque patch augmenté (ex: CODEOBJ_rotXXX.tif)
            for (String fname : leafDir.list((d, name) -> name.endsWith(".tif"))) {
                String code = fname.replace(".tif", "");
                File leafFile   = new File(leafDir,   fname);
                File lacunaFile = new File(lacunaDir, fname);
                if (!lacunaFile.exists()) {
                    IJ.log("Pas de patch lacuna correspondant à " + code);
                    continue;
                }

                // Ouverture des stacks
                ImagePlus leafImp   = IJ.openImage(leafFile.getAbsolutePath());
                ImagePlus lacunaImp = IJ.openImage(lacunaFile.getAbsolutePath());
                int n = leafImp.getNSlices();
                int w = leafImp.getWidth(), h = leafImp.getHeight();

                ImageStack stackGaine  = new ImageStack(w, h);
                ImageStack stackLacuna = new ImageStack(w, h);

                for (int z = 1; z <= n; z++) {
                    leafImp.setSlice(z);
                    lacunaImp.setSlice(z);
                    ImageProcessor leafIp   = leafImp.getProcessor();
                    ImageProcessor lacunaIp = lacunaImp.getProcessor();

                    // Erosion du masque leaf pour obtenir gaine_fat
                    ImageProcessor fatIp = leafIp.duplicate();
                    for (int e = 0; e < EROSION_RADIUS; e++) fatIp.erode();

                    // Création des exemples
                    ByteProcessor eg = new ByteProcessor(w, h); // gaine
                    ByteProcessor el = new ByteProcessor(w, h); // lacuna

                    for (int yy = 0; yy < h; yy++) {
                        for (int xx = 0; xx < w; xx++) {
                            boolean inFat    = fatIp.getPixel(xx, yy)   > 0;
                            boolean inLeaf   = leafIp.getPixel(xx, yy)  > 0;
                            boolean inLacuna = lacunaIp.getPixel(xx, yy)> 0;

                            // exemples_gaines: 1 in gaine_fat, 2 in leaf-only
                            if (inFat) eg.set(xx, yy, 1);
                            if (inLeaf) eg.set(xx, yy, 2);

                            // exemples_lacuna: 1 in leaf-non-lacuna, 2 in lacuna
                            if (inLacuna) el.set(xx, yy, 2);
                            else if (inLeaf) el.set(xx, yy, 1);
                        }
                    }

                    // Ajout à la stack
                    String label = leafImp.getStack().getSliceLabel(z);
                    stackGaine.addSlice(label, eg);
                    stackLacuna.addSlice(label, el);
                }

                // Sauvegarde des stacks exemples
                String outGainePath  = new File(outGaineDir,  code + ".tif").getAbsolutePath();
                String outLacunaPath = new File(outLacunaDir, code + ".tif").getAbsolutePath();
                new FileSaver(new ImagePlus(code + "_ex_gaine",  stackGaine)).saveAsTiffStack(outGainePath);
                new FileSaver(new ImagePlus(code + "_ex_lacuna", stackLacuna)).saveAsTiffStack(outLacunaPath);

                IJ.log("Exemples générés pour " + code);
            }
        }
        IJ.log("Génération des exemples augmentés terminée.");
    }
}
