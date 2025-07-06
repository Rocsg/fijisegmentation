package io.github.rocsg.segmentation.aerenchyme.C_Weka_prepare_stack_and_train_exhaustive;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import io.github.rocsg.fijiyama.common.VitimageUtils;
import io.github.rocsg.segmentation.mlutils.SegmentationUtils;
import trainableSegmentation.WekaSegmentation;


public class CubeModelTrainer {

    /**
     * Entraîne un unique modèle Weka à partir de deux volumes de formation,
     * puis l’applique sur un troisième.
     *
     * @param baseDir      chemin racine vers Data_Small
     * @param cube1        identifiant (xx) du premier cube d’entraînement
     * @param cube2        identifiant (xx) du second cube d’entraînement
     * @param cubeTest     identifiant (xx) du cube de test
     * @throws Exception
     */

    static String path="/Donnees/DD_CIRS626_DATA/Test_Hani_HTP/";
    /* Fuses an array of stacks by concatenating them along the Z axis.
     * @param imgTab array of stacks to fuse
     * @return a single stack with all the slices of the input stacks
     */
    public static ImagePlus fuseStacks(ImagePlus []imgTab){
        ImagePlus [][]tabOut2=new ImagePlus[imgTab.length][];
        int n=0;
        for (int i=0;i<imgTab.length;i++) {
            System.out.println("Traitement "+i);
            System.out.println("de taille "+imgTab[i].getStackSize());
            VitimageUtils.printImageResume(imgTab[i]);
            tabOut2[i]=VitimageUtils.stackToSlices(imgTab[i]);
            n+=tabOut2[i].length;
            System.out.println("n="+n);
        }
        ImagePlus []tabOut=new ImagePlus[n];
        int m=0;
        for (int i=0;i<imgTab.length;i++) {
            for(int j=m;j<m+tabOut2[i].length;j++){
                tabOut[j]=tabOut2[i][j-m];
            }
            System.out.println(m);
            m+=tabOut2[i].length;
            System.out.println(m);
        }
        return VitimageUtils.slicesToStack(tabOut);
    }
    public static void main(String []args){
            ImageJ ij=new ImageJ();
            //runTrainSmall();
            runTestSmall();
    }


 


    public static void runTrainSmall(){
        ImagePlus imgRaw=IJ.openImage(path+"Test_very_short/RawTrain.tif");
        ImagePlus imgObjective=IJ.openImage(path+"Test_very_short/ObjTrain.tif");
        int[]    rfParams = SegmentationUtils.getStandardRandomForestParams(42);
        boolean[] feats   = SegmentationUtils.getStandardRandomForestFeatures();
        String modelPath = path+ "/trained_cube.model";
        int DIVIDE=100; //on commence gentil
        int N_EXAMPLES=10000;//on commence gentil
        SegmentationUtils.wekaTrainModelNary(imgRaw, imgObjective, rfParams, feats, modelPath, false,DIVIDE,N_EXAMPLES);

    }



    public static void runTestSmall(){
        ImagePlus imgRaw=IJ.openImage(path+"Test_very_short/RawTest.tif");
        ImagePlus imgObjective=IJ.openImage(path+"Test_very_short/ObjTest.tif");
        int[]    rfParams = SegmentationUtils.getStandardRandomForestParams(42);
        boolean[] feats   = SegmentationUtils.getStandardRandomForestFeatures();
        String modelPath = path+ "/trained_cube.model";
        ImagePlus result= SegmentationUtils.wekaApplyModelSlicePerSlice(imgRaw, rfParams, feats, modelPath);
        
        result.show();
        imgObjective.show();
    }

    public static void run(String baseDir, String cube1, String cube2, String cubeTest) throws Exception {
        // 1. Chargement brut et masques d’entraînement
        String raw1    = baseDir + "/Raw/Cube_" + cube1 + "_Raw.tif";
        String mask1   = baseDir + "/Gaine/Cube_" + cube1 + "_lacunes_annotations.tif";
        String raw2    = baseDir + "/Raw/Cube_" + cube2 + "_Raw.tif";
        String mask2   = baseDir + "/Gaine/Cube_" + cube2 + "_lacunes_annotations.tif";
        ImagePlus img1 = IJ.openImage(raw1);
        ImagePlus lbl1 = IJ.openImage(mask1);
        ImagePlus img2 = IJ.openImage(raw2);
        ImagePlus lbl2 = IJ.openImage(mask2);

        // 2. Concaténation axial des stacks pour un seul jeu de données
        ImagePlus[] slicesImg1 = VitimageUtils.stackToSlices(img1);
        ImagePlus[] slicesImg2 = VitimageUtils.stackToSlices(img2);
//        ImagePlus[] slicesTot=new ImagePlus
        ImagePlus   trainRaw   = null;//VitimageUtils.slicesToStack(
                                   //VitimageUtils.concatenateStacks(slicesImg1, slicesImg2)
                                 //);
        ImagePlus[] slicesLbl1 = VitimageUtils.stackToSlices(lbl1);
        ImagePlus[] slicesLbl2 = VitimageUtils.stackToSlices(lbl2);
        ImagePlus   trainLbl   = null;//VitimageUtils.slicesToStack(
                                   //VitimageUtils.concatenateStacks(slicesLbl1, slicesLbl2)
                                 //);

        // 3. Paramètres du RF et features
        int[]    rfParams = SegmentationUtils.getStandardRandomForestParams(42);
        boolean[] feats   = SegmentationUtils.getStandardRandomForestFeatures();

        // 4. Entraînement multiclasses (0=fond,1=gaine,2=non-gaine)
        String modelPath = baseDir + "/trained_cube.model";
        SegmentationUtils.wekaTrainModelNary(trainRaw, trainLbl, rfParams, feats, modelPath, false,1,10);

        // 5. Application du modèle sur le cube de test
        String rawTest    = baseDir + "/Raw/Cube_" + cubeTest + "_Raw.tif";
        ImagePlus imgTest = IJ.openImage(rawTest);
        WekaSegmentation seg = new WekaSegmentation(imgTest);
        seg.loadClassifier(modelPath);
        ImagePlus proba = seg.applyClassifier(imgTest, 0, true);
        proba.setTitle("Probabilities_Cube_" + cubeTest);
        proba.show();
        IJ.saveAsTiff(proba, baseDir + "/Proba_Cube_" + cubeTest + ".tif");
 
    }

}
