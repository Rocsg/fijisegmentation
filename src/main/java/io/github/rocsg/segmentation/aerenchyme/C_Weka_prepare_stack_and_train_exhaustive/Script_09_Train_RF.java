package io.github.rocsg.segmentation.aerenchyme.C_Weka_prepare_stack_and_train_exhaustive;

import ij.IJ;
import ij.ImagePlus;
import io.github.rocsg.segmentation.mlutils.SegmentationUtils;

public class Script_09_Train_RF {
    private static final String BASE_PATH         = "/Donnees/DD_CIRS626_DATA/Data_Cube_HTP";
    private static final String RAW_TRAIN_PATH    = BASE_PATH + "/RawTrain.tif";
    private static final String EXGAINE_TRAIN     = BASE_PATH + "/examplesGaineTrain.tif";
    private static final String EXLACUNA_TRAIN    = BASE_PATH + "/examplesLacunaTrain.tif";
    private static final String MODEL_GAINE_PATH  = BASE_PATH + "/trained_gaine.model";
    private static final String MODEL_LACUNA_PATH = BASE_PATH + "/trained_lacuna.model";

    private static final int RANDOM_SEED  = 42;
    private static final int DIVIDE       = 3;
    private static final int N_EXAMPLES   = 300000;

    public static void main(String[] args) {
        // Load raw training data
        IJ.log("Loading RAW training stack: " + RAW_TRAIN_PATH);
        ImagePlus imgRaw = IJ.openImage(RAW_TRAIN_PATH);
        if (imgRaw == null) {
            IJ.error("Cannot open raw training image: " + RAW_TRAIN_PATH);
            return;
        }

        // Train RandomForest for Gaine
        IJ.log("Loading Gaine objective stack: " + EXGAINE_TRAIN);
        ImagePlus imgGaine = IJ.openImage(EXGAINE_TRAIN);
        if (imgGaine == null) {
            IJ.error("Cannot open gaine training image: " + EXGAINE_TRAIN);
        } else {
            IJ.log("Starting training RF for Gaine...");
            int[] rfParams = SegmentationUtils.getStandardRandomForestParams(RANDOM_SEED);
            boolean[] feats = SegmentationUtils.getStandardRandomForestFeatures();
            SegmentationUtils.wekaTrainModelNary(
                imgRaw,
                imgGaine,
                rfParams,
                feats,
                MODEL_GAINE_PATH,
                false,
                DIVIDE,
                N_EXAMPLES
            );
            IJ.log("Gaine model saved to: " + MODEL_GAINE_PATH);
        }

        // Train RandomForest for Lacuna
        IJ.log("Loading Lacuna objective stack: " + EXLACUNA_TRAIN);
        ImagePlus imgLacuna = IJ.openImage(EXLACUNA_TRAIN);
        if (imgLacuna == null) {
            IJ.error("Cannot open lacuna training image: " + EXLACUNA_TRAIN);
        } else {
            IJ.log("Starting training RF for Lacuna...");
            int[] rfParamsL = SegmentationUtils.getStandardRandomForestParams(RANDOM_SEED);
            boolean[] featsL = SegmentationUtils.getStandardRandomForestFeatures();
            SegmentationUtils.wekaTrainModelNary(
                imgRaw,
                imgLacuna,
                rfParamsL,
                featsL,
                MODEL_LACUNA_PATH,
                false,
                DIVIDE,
                N_EXAMPLES
            );
            IJ.log("Lacuna model saved to: " + MODEL_LACUNA_PATH);
        }

        IJ.log("Both RandomForest trainings completed.");
    }
}
