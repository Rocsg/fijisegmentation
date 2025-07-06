package io.github.rocsg.segmentation.cperinrootpheno;

//CURRENTLY IN USE FOR WORK MAIL STUFF WITH G MENARD AND C PERIN


import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.Binarizer;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;

import io.github.rocsg.fijiyama.common.VitiDialogs;
import io.github.rocsg.fijiyama.common.VitimageUtils;
import io.github.rocsg.fijiyama.rsml.Root;
import io.github.rocsg.fijiyama.rsml.RootModel;
import io.github.rocsg.segmentation.mlutils.MorphoUtils;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.process.ImageProcessor;

public class ProcessTifAndRSML {
	public static String dirData;

	public static double pixSize=0.0057471056;
	
	public static void main(String[]args) {
		ImageJ ij=new ImageJ();
		exp_03();
	}
	
	
	public static void exp_02_1_ReadBarcodes() {
		dirData="/home/rfernandez/Bureau/A_Test/Perin_root_2/Source_data/Root";
		String[]listTif=new File(dirData).list();
		String globChain="";
		int count=0;
		int countGood=0;
		int countNoGood=0;
		int totImg=0;
		for(String strImg : listTif) {
			if(strImg.contains(".rsml"))continue;
			totImg++;
		}
		
		String[][]tabCsv=new String[totImg][3];	
		for(String strImg : listTif) {
			if(strImg.contains(".rsml"))continue;
			String rsmlName=strImg.substring(0,strImg.length()-3)+"rsml";
			count++;
			
		
			ImagePlus img=IJ.openImage(dirData+"/"+strImg);
			//img.show();
			IJ.run(img, "Invert", "");
			ImagePlus barImg=extractAreaBarCode(img);
			System.out.println(readBarCode(barImg));
			int valG= Integer.parseInt (readBarCode(barImg));
			if(valG==0) {
				countNoGood++;
				valG=askBarCode(barImg);
//				storeImage(temp,dir+"G",img,false);
				System.out.println("NOOOOOOO");
			}
			else {
				countGood++;
				
			}
			tabCsv[count-1]=new String[] {strImg,rsmlName,""+valG+""};
		}
		System.out.println("Total processed = "+count+"  ok="+countGood);
		String csvOut="/home/rfernandez/Bureau/A_Test/Perin_root_2/Processing/barCodeExport.csv";
		VitimageUtils.writeStringTabInCsv(tabCsv, csvOut);
	}
	

	
	
	public static void exp_03() {
		dirData="/media/rfernandez/DATA_RO_A/Perin_GWAS/Perin_root_3/";
		String dirInput=dirData+"Source/UNZIP/";
		String dirOutput=dirData+"Processing/";
		
		FilenameFilter textFilter = new FilenameFilter() {			public boolean accept(File dir, String name) { return (name.endsWith(".rsml"));}		};
		io.github.rocsg.fijiyama.rsml.FSR sr= new io.github.rocsg.fijiyama.rsml.FSR();
		sr.initialize();
		double fivemm=0.5/pixSize;	
		File f=new File(dirInput);
		String[]dirsLev1=f.list();
		Arrays.sort(dirsLev1);
		String[][]dirsLev2=new String[dirsLev1.length][];		
		
		int N=0;
		for(int d1=0;d1<dirsLev1.length;d1++) {
			dirsLev2[d1]=new File(dirInput,dirsLev1[d1]).list(textFilter);
			Arrays.sort(dirsLev2[d1]);
			N+=dirsLev2[d1].length;
			for(int i=0;i<dirsLev2[d1].length;i++) {
				if(new File(dirsLev1[d1]+"/Root").exists() && !(new File(dirsLev1[d1]+"/Root/"+dirsLev2[d1][i]).exists())) {
					IJ.showMessage("WAAAAAAAARNING");
				}
			}
		}

		String[][]outMedianCSV=new String[N+1][12];
		outMedianCSV[0]=new String[] {"Codebar","CodeExp","RsmlName","#Specimen included","#Specimen excluded","Median length (cm)","Mean length","Std length","Median tip diam. (cm)","Mean tip diam.","Std tip diam."};
		String[][]outFullCSV=new String[10*N+1][6];
		outFullCSV[0]=new String[] {"Codebar","CodeExp","RsmlName","Specimen index","Length (cm)","Median tip diam. (cm)"};
		String excludeList="";
		
		int incr=0;
		int incrFull=0;
		for(int d1=0;d1<dirsLev1.length;d1++) {
			System.out.println("\n\nProcessing RSML "+d1+"="+dirsLev1[d1]);
			
			for(int d2=0;d2<dirsLev2[d1].length;d2++) {
				incr++;

				//Open the corresponding rsml 
				String rsmlName=dirInput+dirsLev1[d1]+"/"+dirsLev2[d1][d2];
				String codeBar= (dirsLev2[d1][d2].split("_root")[0]);
				System.out.println("Processing RSML "+d1+" - "+d2+" code " +codeBar+" ----> "+rsmlName);
				RootModel model = new RootModel(rsmlName);
				pixSize=model.pixelSize;
				int nRoots=model.getNRoot();
//				System.out.println(" processing "+nRoots+" roots");
				ArrayList<Double>listDiameters=new ArrayList<Double>();
				ArrayList<Double>listLength=new ArrayList<Double>();
			
				//Get stats (median, mu, sigma) of primaries diameters and lengths in this box
				int nRootsReal=0;
				for(int nr=0;nr<nRoots;nr++) {
					Root r=model.getRoot(nr);
					double median=r.getAVGMedianDiameterInRange( r.computeRootLength()-3*fivemm,r.computeRootLength()-1*fivemm); 
					//System.out.println("Root length="+r.computeRootLength()+" = "+(r.computeRootLength()*pixSize));
					//VitimageUtils.waitFor(5000);
					if(r.getRootLength()>2/pixSize)	{
						nRootsReal++;
						listDiameters.add(new Double(median*pixSize));
						listLength.add(new Double(r.computeRootLength()*pixSize));
						incrFull++;
						outFullCSV[0]=new String[] {"Codebar","CodeExp","RsmlName",
													"Specimen index","Length (cm)","Median tip diam. (cm)"};
						outFullCSV[incrFull]=new String[] {
														""+codeBar,dirsLev1[d1],dirsLev2[d1][d2],
														""+(nr+1),""+IJ.d2s(new Double(r.computeRootLength()*pixSize),5),""+IJ.d2s(new Double(median*pixSize),5)};
					}
					else {
						excludeList+="Excluded : "+rsmlName+" - root number "+nr+" with length="+(r.computeRootLength()*pixSize)+",\n";
					}
				}
				double[]madEDiam=VitimageUtils.MADeStatsDoubleSided(listDiameters);
				double[]muSigDiam=VitimageUtils.statistics1D(listDiameters);
				double[]madELen=VitimageUtils.MADeStatsDoubleSided(listLength);
				double[]muSigLen=VitimageUtils.statistics1D(listLength);
				if(madEDiam==null) {
					//System.out.println("1 null cause");
					for(double diam:listDiameters)System.out.println(diam);
				}
				if(madELen==null) {
					//System.out.println("2 null cause");
					for(double len:listLength)System.out.println(len);
				}
				if(muSigDiam==null)System.out.println("3 null");
				if(muSigLen==null)System.out.println("4 null");
				outMedianCSV[0]=new String[] {"Codebar","CodeExp","RsmlName","#Specimen included","#Specimen excluded",
												"Median length (cm)","Mean length","Std length",
												"Median tip diam. (cm)","Mean tip diam.","Std tip diam."};
				outMedianCSV[incr]=new String[] {
												""+codeBar,dirsLev1[d1],dirsLev2[d1][d2],""+nRootsReal,""+(nRoots-nRootsReal),
												nRootsReal<2 ? "NA" : (""+IJ.d2s(madELen[0],5)),nRootsReal<2 ? "NA" : (""+IJ.d2s(muSigLen[0],5)),nRootsReal<2 ? "NA" : (""+IJ.d2s(muSigLen[1],5)),
												nRootsReal<2 ? "NA" : (""+IJ.d2s(madEDiam[0],5)),nRootsReal<2 ? "NA" : (""+IJ.d2s(muSigDiam[0],5)),nRootsReal<2 ? "NA" : (""+IJ.d2s(muSigDiam[1],5)),
				};
			}
		}
		String[][]outFullCSV2=new String[incrFull+1][outFullCSV[0].length];
		for(int i=0;i<incrFull+1;i++)for(int j=0;j<outFullCSV[0].length;j++)outFullCSV2[i][j]=outFullCSV[i][j];
		VitimageUtils.writeStringInFile(excludeList,  dirOutput+"excludeList.csv");
		VitimageUtils.writeStringTabInCsv(outMedianCSV, dirOutput+"resultsStatsOverBoxes.csv");		
		VitimageUtils.writeStringTabInCsv(outFullCSV, dirOutput+"resultsDataOfAllSpecimen.csv");		
	}

	
	
	
	
	
	
	
	public static void exp_02_2_ReadBarcodes() {
		dirData="/home/rfernandez/Bureau/A_Test/Perin_root_2/Source_data/Root";
		//Open CSV1
		String barCodeRead="/home/rfernandez/Bureau/A_Test/Perin_root_2/Source_data/RRES_CIRAD_BARCODE-1.csv";
		String[][]tabCorrBarCode=VitimageUtils.readStringTabFromCsv(barCodeRead);
	  //Take the final part, from -1.5 to -0.5 cm from the tip
	  double fivemm=0.5/pixSize;	
	  io.github.rocsg.fijiyama.rsml.FSR sr= new io.github.rocsg.fijiyama.rsml.FSR();
	  sr.initialize();
	 String csvOut="/home/rfernandez/Bureau/A_Test/Perin_root_2/Processing/barCodeExport.csv";
		String[][]csvTab=VitimageUtils.readStringTabFromCsv(csvOut);
		int N=csvTab.length;
		String[][]outCSV=new String[N+1][10];
		for(int i=0;i<N;i++) {
			String imgName=csvTab[i][0].replace(" ","");
			String codeBar=csvTab[i][2].replace(" ","");
			String rsmlName=csvTab[i][1].replace(" ","");
			System.out.println("Processing RSML "+i+" / "+N+" : "+imgName+"-"+rsmlName);
			new ImagePlus(dirData+"/"+imgName).show();
			System.out.println(dirData+"/"+rsmlName);
			//Ouvrir rsml et image
			RootModel model = new RootModel(dirData+"/"+rsmlName);
			int nRoots=model.getNRoot();
			System.out.println("Processing "+nRoots+" roots");
			ArrayList<Double>tabMed=new ArrayList<Double>();
			ArrayList<Double>tabMedLen=new ArrayList<Double>();

			int lineBar=0;
			int Ncodes=tabCorrBarCode.length;
			for(int ii=1;ii<Ncodes;ii++) {
				if (tabCorrBarCode[ii][1].equals(codeBar))lineBar=ii;
			}
			if(lineBar==0)System.out.println("WAAAAAAAARNING");

			for(int nr=0;nr<nRoots;nr++) {
		//		System.out.println("\nProcessing root nb "+nr);
				Root r=model.getRoot(nr);
				System.out.println("Longueur="+r.getRootLength()+" xStart="+r.getXMin()+" mindiam="+r.getAVGDiameter());
				
				double median=r.getAVGMedianDiameterInRange( r.getRootLength()-3*fivemm,r.getRootLength()-1*fivemm); 
				
				if(r.getRootLength()>2/pixSize)	{
					//System.out.println("Median ="+median*pixSize);
					tabMed.add(new Double(median*pixSize));
					tabMedLen.add(new Double(r.getRootLength()*pixSize));
				}
//				else System.out.println("Bogus root");
			}
	//		System.out.println(tabMed.size());
			double[]tab=VitimageUtils.MADeStatsDoubleSided(tabMed);
			double[]tabMu=VitimageUtils.statistics1D(tabMed);
			System.out.println("For this plant,\n Tip diameter, median and quartiles are ["+tab[1]+" - "+tab[0]+" - "+tab[2]+"], mean value="+tabMu[0]+"+-"+tabMu[1]+" over Nsamples="+tabMed.size());
			double[]tabLen=VitimageUtils.MADeStatsDoubleSided(tabMedLen);
			double[]tabMuLen=VitimageUtils.statistics1D(tabMedLen);
			System.out.println("Length, median and quartiles are ["+tabLen[1]+" - "+tabLen[0]+" - "+tabLen[2]+"], mean value="+tabMuLen[0]+"+-"+tabMuLen[1]+" over Nsamples="+tabMed.size());
			
			//for all plants get infos
			outCSV[1+i]=new String[] { imgName,rsmlName,codeBar,""+IJ.d2s(tabLen[0],4),""+IJ.d2s(tabMuLen[0],4),""+IJ.d2s(tabMuLen[1],4),""+IJ.d2s(tab[0],4),""+IJ.d2s(tabMu[0],4),""+IJ.d2s(tabMu[1],4),
					tabCorrBarCode[lineBar][0],tabCorrBarCode[lineBar][2],tabCorrBarCode[lineBar][3],tabCorrBarCode[lineBar][4],tabCorrBarCode[lineBar][0],tabCorrBarCode[lineBar][5]};

			outCSV[0]=new String[] {"ImgName","RsmlName","Codebar","Median length (cm)","Mean length","Std length","Median tip diam. (cm)","Mean tip diam.","Std tip diam.",
					"RRES_LineID","IRIS_ID","LineDesignation","SUBPOPULATION","COUNTRY"};
		}
		VitimageUtils.writeStringTabInCsv(outCSV, "/home/rfernandez/Bureau/A_Test/Perin_root_2/Processing/dataExport.csv");
		
	}
	
	
	
	
	
	
	
	
	
	
	
	

	public static ImagePlus extractAreaBarCode(ImagePlus img) {
		ImagePlus img2=MorphoUtils.dilationLine2D(img, 8, true);
		img2=MorphoUtils.erosionLine2D(img2, 4, false);
		img2=VitimageUtils.thresholdImage(img2, 240, 256);
		img2=VitimageUtils.connexeBinaryEasierParamsConnexitySelectvol(img2, 4, 1);
		IJ.run(img2,"8-bit","");
		img2=MorphoUtils.dilationLine2D(img2, 4, false);
		img2=MorphoUtils.erosionLine2D(img2, 8, true);
		ImagePlus maskOut=VitimageUtils.invertBinaryMask(img2);
		IJ.run(maskOut, "Fill Holes", "");
		img2=VitimageUtils.invertBinaryMask(maskOut);
		img2=VitimageUtils.getBinaryMask(img2, 0.5);
		img2=VitimageUtils.makeOperationBetweenTwoImages(img, img2, 2,true);
		IJ.run(img2,"8-bit","");
		img2=VitimageUtils.makeOperationBetweenTwoImages(img2, maskOut,1,true);
		return img2;
	}

	public static String readBarCode(ImagePlus temp) {
		ImageProcessor ip=temp.getProcessor();		
		MultiFormatReader multiFormatReader = new MultiFormatReader();
	    Rectangle r = ip.getRoi();
	    BufferedImage myimg = ip.crop().getBufferedImage();
	    BufferedImageLuminanceSource bufferedImageLuminanceSource = new BufferedImageLuminanceSource(myimg);
	    BinaryBitmap bitmap = new BinaryBitmap((Binarizer)new HybridBinarizer((LuminanceSource)bufferedImageLuminanceSource));
	    String resultText = null;
	    BarcodeFormat bf = null;
	    Map<DecodeHintType, Object> hints = null;
	    hints = new EnumMap<DecodeHintType, Object>(DecodeHintType.class);
	    ArrayList<BarcodeFormat>ar=new ArrayList<BarcodeFormat>();
	    ar.add(BarcodeFormat.CODE_39);
	    hints.put(DecodeHintType.POSSIBLE_FORMATS, ar);
	    hints.put(DecodeHintType.TRY_HARDER,Boolean.TRUE);
	    try {
	      Result result = multiFormatReader.decode(bitmap, hints);
	      resultText = result.getText();
	      bf = result.getBarcodeFormat();
	    } catch (NotFoundException e) {
	      IJ.log("Error : No corresponding encoder found (" + e.getMessage() + ")"); return "0";
	    } 
	    if (resultText != null) {
	    	//IJ.showMessage(resultText);
	    	return resultText;
	    } 
	    //IJ.showMessage("0");
	    return "0";
	}
	
	public static int askBarCode(ImagePlus temp) {
		temp.show();
		int ret=VitiDialogs.getIntUI("Please enter barcode", 0);
		temp.hide();
		return ret;
	}

}
