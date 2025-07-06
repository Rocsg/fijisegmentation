package io.github.rocsg.segmentation.aerenchyme.C_Weka_prepare_stack_and_train_exhaustive;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.io.FileSaver;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Script_08_Stack_Examples {
    private static final String BASE_PATH = "/Donnees/DD_CIRS626_DATA/Data_Cube_HTP";
    private static final String TRAIN_CSV = BASE_PATH + "/train_split.csv";
    private static final String TEST_CSV  = BASE_PATH + "/test_split.csv";

    private static final String RAW_DIR   = BASE_PATH + "/patches_128_aug/raw";
    private static final String GAINE_DIR = BASE_PATH + "/exemples_128_gaine_aug";
    private static final String LACUNA_DIR= BASE_PATH + "/exemples_128_lacuna_aug";

    public static void main(String[] args) {
        processSplit(TRAIN_CSV, "Train");
        processSplit(TEST_CSV,  "Test");
        IJ.log("Stacking terminé.");
    }

    private static void processSplit(String csvPath, String splitName) {
        List<String[]> entries = readCsv(csvPath);
        if (entries.isEmpty()) {
            IJ.error("Aucune entrée dans " + csvPath);
            return;
        }

        ImageStack rawStack    = new ImageStack();
        ImageStack gaineStack  = new ImageStack();
        ImageStack lacunaStack = new ImageStack();

        for (String[] entry : entries) {
            String cube = entry[0];
            String code = entry[1];
            // raw variants
            File rawFolder = new File(RAW_DIR, cube);
            File[] rawFiles = rawFolder.listFiles((d,n)->n.startsWith(code) && n.endsWith(".tif"));
            // gaine variants
            File gaineFolder = new File(GAINE_DIR, cube);
            File[] gaineFiles = gaineFolder.listFiles((d,n)->n.startsWith(code) && n.endsWith(".tif"));
            // lacuna variants
            File lacunaFolder = new File(LACUNA_DIR, cube);
            File[] lacunaFiles = lacunaFolder.listFiles((d,n)->n.startsWith(code) && n.endsWith(".tif"));

            // stack each modality
            stackFiles(rawFiles, rawStack);
            stackFiles(gaineFiles, gaineStack);
            stackFiles(lacunaFiles, lacunaStack);
        }

        // Save
        saveStack(rawStack, BASE_PATH + "/Raw" + splitName + ".tif",    "Raw" + splitName);
        saveStack(gaineStack, BASE_PATH + "/examplesGaine" + splitName + ".tif", "ExGaine" + splitName);
        saveStack(lacunaStack, BASE_PATH + "/examplesLacuna" + splitName + ".tif","ExLacuna"+ splitName);

        IJ.log(splitName + " stacks écrites.");
    }

    private static List<String[]> readCsv(String csvPath) {
        List<String[]> list = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(csvPath))) {
            String line = br.readLine(); // header
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length >= 2)
                    list.add(new String[]{parts[0], parts[1]});
            }
        } catch (IOException e) {
            IJ.error("Erreur lecture CSV " + csvPath + " : " + e.getMessage());
        }
        return list;
    }

    private static void stackFiles(File[] files, ImageStack stack) {
        if (files == null) return;
        for (File f : files) {
            ImagePlus imp = IJ.openImage(f.getAbsolutePath());
            if (imp == null) continue;
            ImageStack s = imp.getStack();
            for (int i = 1; i <= s.getSize(); i++) {
                stack.addSlice(f.getName() + ":" + s.getSliceLabel(i), s.getProcessor(i));
            }
        }
    }

    private static void saveStack(ImageStack stack, String path, String title) {
        ImagePlus out = new ImagePlus(title, stack);
        new FileSaver(out).saveAsTiffStack(path);
    }
}
