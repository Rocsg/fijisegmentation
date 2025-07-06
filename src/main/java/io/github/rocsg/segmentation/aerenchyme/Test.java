package io.github.rocsg.segmentation.aerenchyme;
import ij.IJ;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

public class Test {
	// Pieces of macros for RootCell treatments. This part 
	public static void main(String[]args) {
		IJ.showMessage("Define a new experiment.");
	
		IJ.showMessage("Choose the folder containing this script");
		//Get an input directory from the user
		String scriptFolder=IJ.getDirectory("Choose the script folder");
		
		IJ.showMessage("Choose the folder containing the tif images");
		String sourceFolder = IJ.getDirectory("Choose the source image folder");
	
		IJ.showMessage("Choose a parent folder to host the succesive parts of processing");
		String outputFolder = IJ.getDirectory("Choose the output folder");
	
	
		//Test if the source folder contains image. If not, abort
		if (!new File(sourceFolder).isDirectory()){
			IJ.showMessage("The provided input Dir is not a directory. Abort.");return;
		}
		String[]fileList = new File(sourceFolder).list();
		for (int i=0; i<fileList.length; i++) {
			if (!fileList[i].substring(fileList[i].length()-4).contains(".tif")) {
				IJ.showMessage("The file "+fileList[i]+" is not a tif image. Abort.");return;
		    }
		}
		if(fileList.length<1){
			IJ.showMessage("No images in it. Abort.");return;
		}
	
	
	
		//Test if the output folder is empty. If not, abort
		if (!new File(outputFolder).isDirectory()){
			IJ.showMessage("The provided output Dir is not a directory. Abort.");return;
		}
		fileList = new File(outputFolder).list();
		if(fileList.length>0){
			IJ.showMessage("Output dir is not empty. Abort.");return;
		}
	
	
		//Create 1_Source, 2_AreaRoi, 3_CellRoi, 4_LacunesIndices
		new File(outputFolder+"/"+"1_Source").mkdirs();
		new File(outputFolder+"/"+"2_AreaRoi").mkdirs();
		new File(outputFolder+"/"+"3_CellRoi").mkdirs();
		new File(outputFolder+"/"+"4_LacunesIndices").mkdirs();


	
		//Copy the images from raw to processing
		fileList = new File(sourceFolder).list();
		for (int i=0; i<fileList.length; i++) {
			IJ.log("Copying image "+i+" / "+fileList.length);
			try {
				Files.copy(Paths.get(sourceFolder+"/"+fileList[i]), Paths.get(outputFolder+"/1_Source/"+fileList[i]), StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	
		try {
			Files.write(Paths.get(scriptFolder+"/pathScripts.txt"), new String(scriptFolder+"path.txt").getBytes(), StandardOpenOption.CREATE);
		Files.write(Paths.get(scriptFolder+"/pathSource.txt"), new String(sourceFolder+"path.txt").getBytes(), StandardOpenOption.CREATE);
		Files.write(Paths.get(scriptFolder+"/pathProcessing.txt"), new String(outputFolder+"path.txt").getBytes(), StandardOpenOption.CREATE);
        
		Files.write(Paths.get(outputFolder+"/pathScripts.txt_savedformemory.txt"), new String(scriptFolder+"path.txt").getBytes(), StandardOpenOption.CREATE);
		Files.write(Paths.get(outputFolder+"/pathSource.txt_savedformemory.txt"), new String(sourceFolder+"path.txt").getBytes(), StandardOpenOption.CREATE);
		Files.write(Paths.get(outputFolder+"/pathProcessing.txt_savedformemory.txt"), new String(outputFolder+"path.txt").getBytes(), StandardOpenOption.CREATE);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}


	}
}
