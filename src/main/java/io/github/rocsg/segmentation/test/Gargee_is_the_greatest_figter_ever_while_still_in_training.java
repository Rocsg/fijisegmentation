package io.github.rocsg.segmentation.test;

import com.aparapi.Config;

import ij.IJ;
import ij.ImagePlus;
import ij.plugin.Duplicator;
import io.github.rocsg.fijiyama.common.VitimageUtils;
import trainableSegmentation.WekaSegmentation;

public class Gargee_is_the_greatest_figter_ever_while_still_in_training {

    public static void main(String[] args) {
        String timestamps = "J141";
        String specimenName = "B_206";
        int frame = 4;
        String path =  "/home/rfernandez/Bureau/A_Test/Gargee/BahuBali_strikes_back/B_206_GeneralizedPolarTransform.tif";
        ImagePlus img = trainWekaToGetMask(path, frame);   
        System.out.println("done!");
        img.show();
    }
    
  
    public static ImagePlus trainWekaToGetMask(String path, int frame){
       ImagePlus hyperframe = IJ.openImage(path);
       System.out.println("image opened"+hyperframe);
        ImagePlus img = new Duplicator().run(hyperframe, 1, 1, 256, 768, frame, frame);
        WekaSegmentation weka = new WekaSegmentation(img);
        weka.loadClassifier( "/home/rfernandez/Bureau/A_Test/Gargee/BahuBali_strikes_back/All_Var_PCH_z512_545_610_tJ141.model");
        ImagePlus proba = weka.applyClassifier(img, 0, true);
        IJ.saveAsTiff(proba, "/home/rfernandez/Bureau/A_Test/Gargee/BahuBali_strikes_back/res.tif");
        ImagePlus imgMask=new Duplicator().run(proba,1,1,1,proba.getNSlices(),1,1);
        imgMask.setDisplayRange(0.5, 0.5);
        VitimageUtils.convertToGray8(imgMask);
        IJ.run(imgMask, "Invert", "stack");
        IJ.run(imgMask, "Fill Holes", "stack");
        IJ.run(imgMask, "Invert", "stack");
        IJ.run(imgMask, "Divide...", "value=255 stack");      
        imgMask.setDisplayRange(0, 1);
        IJ.run(imgMask,"Median...", "radius=1 stack");
        return imgMask;
    }

}
