package io.github.rocsg.segmentation.aerenchyme.B_Prepare_patches;

import ij.IJ;
import ij.ImagePlus;
import ij.process.ImageProcessor;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

public class Script_04_Verif_Annot {
    private static final String BASE_PATH = "/Donnees/DD_CIRS626_DATA/Data_Cube_HTP";
    private static final String PATCH_DIR = BASE_PATH + "/patches_256";
    private static final int PATCH_SIZE = 256;
    private static final int CHECK_SIZE = 30;
    private static final int HALF_CHECK = CHECK_SIZE / 2;
    private static final String OUTPUT_CSV = BASE_PATH + "/verified_patches.csv";

    private enum Modality {
        LACUNA("masklacuna"),
        GAINE("maskleaf");

        final String name;
        Modality(String name) { this.name = name; }
    }

    public static void main(String[] args) {
        Set<String> okLacuna = new HashSet<>();
        Set<String> okGaine  = new HashSet<>();

        List<Modality> modalities = Arrays.asList(Modality.LACUNA, Modality.GAINE);
        File base = new File(PATCH_DIR);

        for (Modality mod : modalities) {
            File modDir = new File(base, mod.name);
            if (!modDir.isDirectory()) {
                IJ.log("Modality directory not found: " + modDir.getAbsolutePath());
                continue;
            }
            for (File cubeFolder : Objects.requireNonNull(modDir.listFiles(File::isDirectory))) {
                String cubeName = cubeFolder.getName();
                for (File patchFile : Objects.requireNonNull(cubeFolder.listFiles((d,n) -> n.endsWith(".tif")))) {
                    String codeObj = patchFile.getName().replace(".tif", "");
                    ImagePlus imp = IJ.openImage(patchFile.getAbsolutePath());
                    if (imp == null) {
                        IJ.log("Cannot open patch: " + patchFile);
                        continue;
                    }
                    boolean okAll = true;
                    for (int z = 1; z <= imp.getNSlices(); z++) {
                        imp.setSlice(z);
                        ImageProcessor ip = imp.getProcessor();
                        int cx = PATCH_SIZE/2;
                        int cy = PATCH_SIZE/2;
                        int x0 = Math.max(0, cx - HALF_CHECK);
                        int y0 = Math.max(0, cy - HALF_CHECK);
                        int x1 = Math.min(PATCH_SIZE - 1, cx + HALF_CHECK - 1);
                        int y1 = Math.min(PATCH_SIZE - 1, cy + HALF_CHECK - 1);
                        boolean found = false;
                        for (int yy = y0; yy <= y1 && !found; yy++) {
                            for (int xx = x0; xx <= x1; xx++) {
                                if (ip.getPixel(xx, yy) > 0) { found = true; break; }
                            }
                        }
                        if (!found) {
                            IJ.log(String.format("[MISSING] %s %s %s slice %d: no white pixel in %dx%d around center",
                                    mod.name, cubeName, codeObj, z, CHECK_SIZE, CHECK_SIZE));
                            okAll = false;
                        }
                    }
                    if (okAll) {
                        if (mod == Modality.LACUNA) okLacuna.add(cubeName + "," + codeObj);
                        else if (mod == Modality.GAINE) okGaine.add(cubeName + "," + codeObj);
                        IJ.log(String.format("[OK] %s %s %s", mod.name, cubeName, codeObj));
                    } else {
                        IJ.log(String.format("[NOT OK] %s %s %s: missing values", mod.name, cubeName, codeObj));
                    }
                }
            }
        }

        // Intersection: patches valid for both modalities
        try (PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(OUTPUT_CSV, false)))) {
            pw.println("CubeName,CodeObj");
            for (String entry : okLacuna) {
                if (okGaine.contains(entry)) {
                    pw.println(entry);
                }
            }
            IJ.log("Written verified list to " + OUTPUT_CSV);
        } catch (IOException e) {
            IJ.log("Error writing CSV " + OUTPUT_CSV + ": " + e.getMessage());
        }

        IJ.log("Vérification terminée.");
    }
}
