package io.github.rocsg.segmentation.aerenchyme.B_Prepare_patches;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.io.FileSaver;
import ij.process.ImageProcessor;
import io.github.rocsg.fijiyama.common.VitimageUtils;
import it.unimi.dsi.fastutil.Arrays;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;


public class Script_03_Patches256_Extractor {
    private static final String BASE_PATH = "/Donnees/DD_CIRS626_DATA/Data_Cube_HTP";
    private static final String LABEL_DIR = BASE_PATH + "/labels";
    private static final String RAW_DIR = BASE_PATH + "/cropimages";
    private static final String LACUNA_DIR = BASE_PATH + "/croplacuna/masks";
    private static final String LEAF_DIR = BASE_PATH + "/cropleafs/masks";
    private static final String OUT_DIR = BASE_PATH + "/patches_256";
    private static final int PATCH_SIZE = 256;
    private static final int HALF = PATCH_SIZE / 2;
    private static final int[] OFFSETS = {-15, -12, -9, -6, -3, 0, 3, 6, 9, 12, 15};

    private enum Modality {
        RAW("raw", RAW_DIR, "_crop.tif"),
        LACUNA("masklacuna", LACUNA_DIR, ".tif"),
        LEAF("maskleaf", LEAF_DIR, ".tif");

        final String name;
        final String srcDir;
        final String suffix;
        Modality(String name, String srcDir, String suffix) {
            this.name = name;
            this.srcDir = srcDir;
            this.suffix = suffix;
        }
    }

    public static void main(String[] args) {
        //boolean only04=true;
        File labelFolder = new File(LABEL_DIR);
        File[] csvFiles = labelFolder.listFiles((d, name) -> name.endsWith("_annotations.csv"));
        if (csvFiles == null || csvFiles.length == 0) {
            System.err.println("Aucun fichier CSV trouvé dans " + LABEL_DIR);
            return;
        }

        ArrayList<Modality> modalities = new ArrayList<Modality>(){};
        modalities.add(Modality.RAW);
        modalities.add(Modality.LACUNA);
        modalities.add(Modality.LEAF);

        ArrayList<String> names = new ArrayList<String>(){};
        names.add("Cube_01");
        names.add("Cube_02");
        names.add("Cube_03");

        for (File csvFile : csvFiles) {
            String fname = csvFile.getName();
            String cubeName = fname.replace("_annotations.csv", "");
            if(!cubeName.contains("Cube_05"))continue;
            System.out.println("Traitement de " + cubeName);

            for (Modality mod : modalities) {
                // Ne traiter LACUNA et LEAF que pour Cube_01, Cube_02, Cube_03
                if ((mod == Modality.LACUNA || mod == Modality.LEAF)
                        && !names.contains(cubeName))
                    continue;

                String srcPath = mod.srcDir + File.separator + cubeName + mod.suffix;
                if (!new File(srcPath).exists()) {
                    System.out.println("Fichier introuvable pour " + mod.name + ": " + srcPath);
                    continue;
                }
                //Afficher le path de l'image
                System.out.println(srcPath);
                ImagePlus imp = IJ.openImage(srcPath);
                VitimageUtils.printImageResume(imp);
                if (imp == null) {
                    System.err.println("Impossible d'ouvrir : " + srcPath);
                    continue;
                }

                File outCube = new File(OUT_DIR + File.separator + mod.name, cubeName);
                if (!outCube.exists() && !outCube.mkdirs()) {
                    System.err.println("Impossible de créer le dossier : " + outCube.getAbsolutePath());
                    continue;
                }

                try (BufferedReader br = new BufferedReader(new FileReader(csvFile))) {
                    String line = br.readLine();
                    while ((line = br.readLine()) != null) {
                        String[] parts = line.split(",");
                        if (parts.length < 5) continue;
                        String codeObj = parts[1];
                        if(!codeObj.contains("Cube_05_L10_C1"))continue;
                        int x = Integer.parseInt(parts[2]);
                        int y = Integer.parseInt(parts[3]);
                        int z = Integer.parseInt(parts[4]);
                        boolean isFrameStack=imp.getNSlices()==1;
                        System.out.println("IsFrameStack: "+isFrameStack);
                        ImageStack stack = null;
                        if(false)stack = new ImageStack(PATCH_SIZE, PATCH_SIZE/2+(imp.getHeight()-y));
                        else stack = new ImageStack(PATCH_SIZE, PATCH_SIZE);
                        for (int offset : OFFSETS) {
                            int zSlice = z + offset;
                            //if (zSlice < 1 || zSlice > imp.getNSlices()) continue;
                            if(isFrameStack)imp.setT(zSlice);
                            else imp.setSlice(zSlice);
                            ImageProcessor ip = imp.getProcessor();

                            int x0 = Math.max(0, x - HALF);
                            int y0 = Math.max(0, y - HALF);
                            x0 = Math.min(x0, ip.getWidth() - PATCH_SIZE);
                            y0 = Math.min(y0, ip.getHeight() - PATCH_SIZE);

                            if(false){
                                 x0=x-PATCH_SIZE/2;
                                y0=y-PATCH_SIZE/2;
                            }
                            System.out.println("X0: "+x0+", Y0: "+y0);
                            System.out.println(PATCH_SIZE);
                            System.out.println(PATCH_SIZE/2+(imp.getHeight()-y));
                            VitimageUtils.printImageResume(imp);
                            if(false){
                                ip.setRoi(x0, y0, PATCH_SIZE, PATCH_SIZE/2+(imp.getHeight()-y));
                            }
                            else ip.setRoi(x0, y0, PATCH_SIZE, PATCH_SIZE);
                            ImageProcessor patch = ip.crop();
                            stack.addSlice(String.valueOf(zSlice), patch);
                        }

                        ImagePlus patchImp = new ImagePlus(codeObj, stack);
                        VitimageUtils.printImageResume(patchImp);

                        int deltay=PATCH_SIZE-patchImp.getHeight();
                        if(false) patchImp=VitimageUtils.uncropImageByte(patchImp, 0, 0, 0, PATCH_SIZE, PATCH_SIZE, patchImp.getStackSize());
                        VitimageUtils.printImageResume(patchImp);
                        String outPath = outCube.getAbsolutePath() + File.separator + codeObj + ".tif";
                        new FileSaver(patchImp).saveAsTiffStack(outPath);
                        System.out.println("[" + mod.name + "] Patch enregistré: " + codeObj);
                    }
                } catch (IOException e) {
                    System.err.println("Erreur lecture CSV " + fname + " : " + e.getMessage());
                }
            }
        }

        System.out.println("Extraction terminée pour toutes modalités.");
    }
}


