package io.github.rocsg.segmentation.aerenchyme.fijiplugin_htp_cubes;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.plugin.filter.EDM;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import io.github.rocsg.fijiyama.common.VitiDialogs;
import io.github.rocsg.fijiyama.common.VitimageUtils;
import io.github.rocsg.segmentation.mlutils.MorphoUtils;
import trainableSegmentation.WekaSegmentation;
import ij.io.FileSaver;
import ij.plugin.Duplicator;
import ij.plugin.MontageMaker;
import ij.gui.TextRoi;
import java.awt.Font;
import java.awt.image.VolatileImage;
import java.awt.Color;
import ij.io.Opener;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class FastProcessDirOfAerenchymeImages {


    public static void main(String[] args) {
        ImageJ ij=new ImageJ();
        String inputDir="/home/rfernandez/Bureau/A_Test/Aerenchyme/TestTemp/P02_organs";
        String outputDir="/home/rfernandez/Bureau/A_Test/Aerenchyme/TestTemp/P02_organs_Results";
        new FastProcessDirOfAerenchymeImages("").run(inputDir,outputDir);
    }

    public FastProcessDirOfAerenchymeImages(String s){
    }

    public FastProcessDirOfAerenchymeImages(){
    }


    public void run(String inputDir,String outputDir) {
        String mainDir=outputDir;
        new File(mainDir+"/").mkdirs();
        final String BASE_PATH     = mainDir;
        final String INPUT_DIR     = inputDir;
        final String MODEL_PATH    = BASE_PATH + "/Model/classifier_bare2.model";
    
        // Intermediate output dirs
        final String DIR1 = BASE_PATH + "/1_segmentation_tissue";
        final String DIR2 = BASE_PATH + "/2_segmentation_closed";
        final String DIR3 = BASE_PATH + "/3_filled";
        final String DIR4 = BASE_PATH + "/4_single_cc";
        final String DIR5 = BASE_PATH + "/5_opened";
        final String DIR6 = BASE_PATH + "/6_result_segmentation_midrib";
        final String DIR7 = BASE_PATH + "/7_draft_lacuna";
        final String DIR8 = BASE_PATH + "/8_result_segmentation_lacuna";
        final String DIR9 = BASE_PATH + "/Seg_patches_128x128xN";
    
    


        // 0. Génère tous les dossiers de sortie
        new File(DIR1).mkdirs();
        new File(DIR2).mkdirs();
        new File(DIR3).mkdirs();
        new File(DIR4).mkdirs();
        new File(DIR5).mkdirs();
        new File(DIR6).mkdirs();
        new File(DIR7).mkdirs();
        new File(DIR8).mkdirs();
        new File(DIR9).mkdirs();

        // 1. Charger le modèle Weka une seule fois
        WekaSegmentation weka = new WekaSegmentation();
        weka.loadClassifier(MODEL_PATH);
        System.out.println("Weka loaded");
        File input = new File(INPUT_DIR);
        System.out.println("input loaded");
        File[] files = input.listFiles((dir, name) -> name.toLowerCase().endsWith(".tif"));
        if (files == null) {
            System.err.println("No input files found in " + INPUT_DIR);
            return;
        }

        for (File file : files) {
            String code = file.getName().replaceAll("\\.tif", "");
            System.out.print("\nProcessing: " + code+" ... ");

            // --- 1. Segmentation tissue via Weka, seuil binaire
            System.out.print("1 ");
            ImagePlus raw = IJ.openImage(file.getAbsolutePath());
            if (raw == null) { System.err.println("Cannot open: "+file); continue; }
            ImagePlus proba = weka.applyClassifier(raw, 0, true);
            // On suppose classe 1 = tissu (adapter si besoin)
            proba.setC(1); // proba classe 1 (1-based index)
            ImagePlus imgMask=new Duplicator().run(proba,1,1,1,proba.getNSlices(),1,1);
            imgMask.setDisplayRange(0.5, 0.5);
            IJ.run(imgMask, "8-bit", "stack");
            IJ.run(imgMask, "Invert", "stack");
            new FileSaver(imgMask).saveAsTiff(DIR1 + "/" + code + ".tif");

            // --- 2. Fermeture morphologique (dilate puis erode, rayon=2)
            System.out.print("2 ");
            ImagePlus imgClosed=maskCircular(imgMask,true);
            
            //ImagePlus imgClosed = opening(imgMask, 5);
            new FileSaver(imgClosed).saveAsTiff(DIR2 + "/" + code + ".tif");

            // --- 3. Fill holes (invert, fill, invert)
            System.out.print("3 ");
            ImagePlus imgFill = imgClosed.duplicate();
            IJ.run(imgFill, "Fill Holes", "stack");
            new FileSaver(imgFill).saveAsTiff(DIR3 + "/" + code + ".tif");

            // --- 4. Central connected component
            System.out.print("4 ");
            ImagePlus imgCentral = imgFill.duplicate();
            IJ.run(imgCentral, "Invert", "stack");
            imgCentral = keepCentralCC(imgCentral);
            IJ.run(imgCentral, "Invert", "stack");
            new FileSaver(imgCentral).saveAsTiff(DIR4 + "/" + code + ".tif");

            // --- 5. Ouverture morphologique (erode puis dilate, rayon=8)
            System.out.print("5 ");
            ImagePlus imgOpened = closing (imgCentral, 11);
            IJ.run(imgOpened, "Invert", "stack");
            new FileSaver(imgOpened).saveAsTiff(DIR5 + "/" + code + ".tif");

            // --- 6. De nouveau central CC (monoCC)
            System.out.print("6 ");
            ImagePlus imgMonoCC = keepCentralCC(imgOpened);
            new FileSaver(imgMonoCC).saveAsTiff(DIR6 + "/" + code + ".tif");

            // --- 7. Erosion, AND logique avec maskTissue (draft lacuna)
            System.out.print("7 ");
            ImagePlus imgEroded = MorphoUtils.erosionCircle2D(imgMonoCC, 1);
            ImagePlus imgDraftLacuna = and(imgEroded, imgMask);
            new FileSaver(imgDraftLacuna).saveAsTiff(DIR7 + "/" + code + ".tif");

            // --- 8. Supprimer petites composantes (<25px)
            System.out.print("8 ");
            ImagePlus imgLacunaClean = removeSmallCC(imgDraftLacuna, 25);
            new FileSaver(imgLacunaClean).saveAsTiff(DIR8 + "/" + code + ".tif");

            // --- 9. Faire jolie segmentation
            System.out.print("9 ");
            IJ.run(imgLacunaClean, "Multiply...", "value=0.33 stack");
            IJ.run(imgMonoCC,  "Multiply...", "value=0.33 stack");

            ImagePlus nullImage = VitimageUtils.nullImage(raw);
            ImagePlus rgb=VitimageUtils.compositeRGBLByte(imgMonoCC, imgLacunaClean, nullImage, raw, 0.9,1.2,1,1);
            IJ.run(rgb, "RGB Color", "slices keep");
            System.out.println(DIR9 + "/" + code + ".tif");
            new FileSaver(rgb).saveAsTiff(DIR9 + "/" + code + ".tif");

        }
        System.out.println();
        //Calcul des traits
        /*computeAndSaveLeafStats(
            BASE_PATH,
            DIR6,    // gaine: dossier résultat après monoCC
            DIR8,    // lacuna: dossier lacunes nettoyées
            BASE_PATH + "/stats_lacuna_gaine.csv"
        );


        // --- 9. Assemblage panel/restauration à faire ici, avec ton code maison.
    // Construction du panel d'investigation
        assembleAndShowPanel(
            BASE_PATH,
            BASE_PATH + "/stats_lacuna_gaine.csv",                  
            BASE_PATH + "/raw_investigation",
            BASE_PATH + "/seg_investigation",
            BASE_PATH + "/panel_raw.tif",
            BASE_PATH + "/panel_seg.tif"
        );
        */
        System.out.println("Pipeline terminé.");
    }

    public static ImagePlus maskCircular(ImagePlus impIn, boolean value) {
        ImagePlus imp = impIn.duplicate();
        int w = imp.getWidth();
        int h = imp.getHeight();
        double cx = w / 2.0;
        double cy = h / 2.0;
        double r = Math.min(w, h) / 2.0;
        double rCut = 0.97 * r;
        double rCutStart = rCut * rCut;
        double rCutEnd= rCutStart/2;
        int nSlices = imp.getStackSize();
        for (int z = 1; z <= nSlices; z++) {
            ImageProcessor ip = imp.getStack().getProcessor(z);
            if (!(ip instanceof ByteProcessor)) continue;
            double factor=(z-1)*1.0/(nSlices-1);
            double rCutInterp=rCutStart*(1-factor)+(rCutEnd)*(factor);
            System.out.println("z="+z+" , rCutInterp="+rCutInterp);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    double dx = x - cx;
                    double dy = y - cy;

                    //Interpoler rCut entre rCutStart et rCutEnd en fonction de z

                    if (dx*dx + dy*dy > rCutInterp) {

                        ip.set(x, y, value ? 255 : 0);
                    }
                }
            }
        }
        imp.updateAndDraw();
        return imp;
    }




    private static ImagePlus closing(ImagePlus imp, int radius) {
        imp=MorphoUtils.dilationCircle2D(imp, radius);
        imp=MorphoUtils.erosionCircle2D(imp, radius);
        return imp;
    }

    private static ImagePlus opening(ImagePlus imp, int radius) {
        imp=MorphoUtils.erosionCircle2D(imp, radius);
        imp=MorphoUtils.dilationCircle2D(imp, radius);
        return imp;
    }

    // FERMETURE (dilate puis erode)
    private static ByteProcessor closing(ByteProcessor bp, int radius) {
        ImagePlus imp = new ImagePlus("tmp", bp.duplicate());
        imp=MorphoUtils.dilationCircle2D(imp, radius);
        imp=MorphoUtils.erosionCircle2D(imp, radius);
        return imp.getProcessor().convertToByteProcessor();
    }
    // OUVERTURE (erode puis dilate)
    private static ByteProcessor opening(ByteProcessor bp, int radius) {
        ImagePlus imp = new ImagePlus("tmp", bp.duplicate());
        imp=MorphoUtils.erosionCircle2D(imp, radius);
        imp=MorphoUtils.dilationCircle2D(imp, radius);
        return imp.getProcessor().convertToByteProcessor();
    }
    // EROSION
    private static ByteProcessor erosion(ByteProcessor bp, int radius) {
        ImagePlus imp = new ImagePlus("tmp", bp.duplicate());
        imp=MorphoUtils.erosionCircle2D(imp, radius);
        return imp.getProcessor().convertToByteProcessor();
    }

    private static ImagePlus and(ImagePlus img1, ImagePlus img2) {
        ImagePlus img=img1.duplicate();
        int n=img.getStackSize();
        for (int i=1; i<=n; i++) {
            ByteProcessor bp1 = (ByteProcessor) img.getStack().getProcessor(i);
            ByteProcessor bp2 = (ByteProcessor) img2.getStack().getProcessor(i);
            bp1 = and(bp1, bp2);
            img.getStack().setProcessor(bp1, i);
        }
        return img;
    }

     // AND logique
    private static ByteProcessor and(ByteProcessor bp1, ByteProcessor bp2) {
        ByteProcessor out = (ByteProcessor) bp1.duplicate();
        int w = out.getWidth(), h = out.getHeight();
        for (int y=0; y<h; y++) for (int x=0; x<w; x++)
            out.set(x,y, (bp1.get(x,y)>0 && bp2.get(x,y)>0) ? 255 : 0);
        return out;
    }

    private static ImagePlus keepCentralCC(ImagePlus imgIn) {
        ImagePlus img=imgIn.duplicate();
        int n=img.getStackSize();
        for (int i=1; i<=n; i++) {
            ByteProcessor bp = (ByteProcessor) img.getStack().getProcessor(i);
            bp = keepCentralCC(bp);
            img.getStack().setProcessor(bp, i);
        }
        return img;
    }

    // Garde la composante connexe dont le barycentre est le plus proche du centre
    private static ByteProcessor keepCentralCC(ByteProcessor bp) {
        int w = bp.getWidth(), h = bp.getHeight();
        int[][] labels = new int[h][w];
        int lbl = 0;
        int[] count = new int[1000]; // max 999 CC
        double[] cx = new double[1000], cy = new double[1000];
        for (int y=0; y<h; y++) for (int x=0; x<w; x++) labels[y][x]=0;

        // BFS
        for (int y=0; y<h; y++) {
            for (int x=0; x<w; x++) {
                if (bp.get(x,y)==255 && labels[y][x]==0) {
                    lbl++;
                    int pix = 0;
                    double sx=0, sy=0;
                    int[] qx=new int[w*h], qy=new int[w*h];
                    int qf=0, qb=0;
                    qx[qb]=x; qy[qb]=y; qb++;
                    labels[y][x]=lbl;
                    while(qf<qb) {
                        int px=qx[qf], py=qy[qf]; qf++;
                        pix++; sx+=px; sy+=py;
                        for (int[] d : new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
                            int nx=px+d[0], ny=py+d[1];
                            if(nx>=0&&nx<w&&ny>=0&&ny<h && bp.get(nx,ny)==255 && labels[ny][nx]==0){
                                labels[ny][nx]=lbl; qx[qb]=nx; qy[qb]=ny; qb++;
                            }
                        }
                    }
                    count[lbl]=pix; cx[lbl]=sx; cy[lbl]=sy;
                }
            }
        }
        // Cherche la CC la plus centrale
        double cx0=w/2.0, cy0=h/2.0, best=1e9; int bestLbl=0;
        for (int l=1; l<=lbl; l++) {
            double dx=(cx[l]/count[l])-cx0, dy=(cy[l]/count[l])-cy0, dist2=dx*dx+dy*dy;
            if (dist2<best) { best=dist2; bestLbl=l; }
        }
        ByteProcessor out = new ByteProcessor(w,h);
        for (int y=0; y<h; y++) for (int x=0; x<w; x++)
            if (labels[y][x]==bestLbl) out.set(x,y,255);
        return out;
    }
    // Supprime CC < minSize

    private static ImagePlus removeSmallCC(ImagePlus imgIn, int minSize) {
        ImagePlus img=imgIn.duplicate();
        int n=img.getStackSize();
        for (int i=1; i<=n; i++) {
            ByteProcessor bp = (ByteProcessor) img.getStack().getProcessor(i);
            bp = removeSmallCC(bp, minSize);
            img.getStack().setProcessor(bp, i);
        }
        return img;
    }

    private static ByteProcessor removeSmallCC(ByteProcessor bp, int minSize) {
        int w = bp.getWidth(), h = bp.getHeight();
        int[][] labels = new int[h][w];
        int lbl = 0;
        int[] count = new int[w*h/10+1]; // estimation
        for (int y=0; y<h; y++) for (int x=0; x<w; x++) labels[y][x]=0;
        // BFS
        for (int y=0; y<h; y++) {
            for (int x=0; x<w; x++) {
                if (bp.get(x,y)==255 && labels[y][x]==0) {
                    lbl++; int pix=0;
                    int[] qx=new int[w*h], qy=new int[w*h];
                    int qf=0, qb=0;
                    qx[qb]=x; qy[qb]=y; qb++;
                    labels[y][x]=lbl;
                    while(qf<qb) {
                        int px=qx[qf], py=qy[qf]; qf++;
                        pix++;
                        for (int[] d : new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
                            int nx=px+d[0], ny=py+d[1];
                            if(nx>=0&&nx<w&&ny>=0&&ny<h && bp.get(nx,ny)==255 && labels[ny][nx]==0){
                                labels[ny][nx]=lbl; qx[qb]=nx; qy[qb]=ny; qb++;
                            }
                        }
                    }
                    count[lbl]=pix;
                }
            }
        }
        ByteProcessor out = new ByteProcessor(w,h);
        for (int y=0; y<h; y++) for (int x=0; x<w; x++)
            if (labels[y][x]!=0 && count[labels[y][x]]>=minSize) out.set(x,y,255);
        return out;
    }

    private static void computeAndSaveLeafStats(String basePath, String gaineDir, String lacunaDir, String outCSV) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outCSV))) {
            writer.write("Code;CodeCube;CodeSpec;Ratio_of_median;Median_of_ratio;mean_lacuna;median_lacuna;std_lacuna;min_lacuna;max_lacuna;mean_gaine;median_gaine;std_gaine;min_gaine;max_gaine\n");

            

            for (String codeF : new File(gaineDir).list()) {
                String code = codeF.replace(".tif", "");
                String codeCube=code.split("__")[0];
                String codeExpe=code.split("__")[1];

                String gainePath = new File(gaineDir, code+".tif").getAbsolutePath();
                String lacunaPath = new File(lacunaDir, code+".tif").getAbsolutePath();

                // Gather per-slice areas

                ImagePlus impG = IJ.openImage(gainePath);
                ImagePlus impL = IJ.openImage(lacunaPath);
                int slices = impG.getStackSize();
                List<Integer> areasL = new ArrayList<>();
                List<Integer> areasG = new ArrayList<>();
                List<Double> ratios = new ArrayList<>();
                System.out.println(code);
                System.out.println("slices="+impL.getStackSize());
                for (int s=1; s<=slices; s++) {
                    ByteProcessor bpG = (ByteProcessor)impG.getStack().getProcessor(s);
                    int countG = countWhite(bpG);
                    areasG.add(countG);
                    ByteProcessor bpL = (ByteProcessor)impL.getStack().getProcessor(s);
                    int countL = countWhite(bpL);
                    areasL.add(countL);
                    ratios.add(countL*1.0/(Math.max(1,countG)));
                }
                System.out.println(areasL);

                // Compute stats
                double meanL = mean(areasL);
                int medL  = (int) median(areasL);
                double stdL  = std(areasL, meanL);
                int minL     = Collections.min(areasL);
                int maxL     = Collections.max(areasL);

                double meanG = mean(areasG);
                int medG  = (int) median(areasG);
                double stdG  = std(areasG, meanG);
                int minG     = Collections.min(areasG);
                int maxG     = Collections.max(areasG);

                double ratio_of_median = medL *1.0 / medG;
                double median_of_ratio = medianD(ratios);
                writer.write(String.format(java.util.Locale.US, "%s;%s;%s;%.4f;%.4f;%.2f;%d;%.2f;%d;%d;%.2f;%d;%.2f;%d;%d\n",
                                            code, codeCube, codeExpe, ratio_of_median, median_of_ratio, meanL, medL, stdL, minL, maxL, meanG, medG, stdG, minG, maxG));
            }
            IJ.log("=== Stats CSV saved to " + outCSV + " ===");
        } catch (IOException e) {
            IJ.error("Error writing CSV: " + e.getMessage());
        }
    }

    // Fonctions utilitaires
    private static int countWhite(ByteProcessor bp) {
        int w = bp.getWidth(), h = bp.getHeight(), cnt = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (bp.get(x,y) > 0) cnt++;
            }
        }
        return cnt;
    }
    private static double mean(List<Integer> vals) {
        double sum=0;
        for (int v: vals) sum += v;
        return sum/vals.size();
    }
    private static double median(List<Integer> vals) {
        Collections.sort(vals);
        int n = vals.size();
        if (n%2==1) return vals.get(n/2);
        else return (vals.get(n/2-1) + vals.get(n/2)) / 2.0;
    }
    private static double medianD(List<Double> vals) {
        Collections.sort(vals);
        int n = vals.size();
        if (n%2==1) return vals.get(n/2);
        else return (vals.get(n/2-1) + vals.get(n/2)) / 2.0;
    }
    private static double std(List<Integer> vals, double mean) {
        double sum=0;
        for (int v: vals) sum += (v-mean)*(v-mean);
        return Math.sqrt(sum/vals.size());
    }



    // ...
     
    public static void assembleAndShowPanel(String basePath, String traitCsv, String rawDir, String segDir, String outRaw, String outSeg) {
        

            //Read the csv written at previous step, and extract list of file names and ratios

            // 1. Lire la liste de codes et codes cubes
            List<String> codesOld = new ArrayList<>();
            List<Double> ratiosOld = new ArrayList<>();
            try (BufferedReader br = new BufferedReader(new FileReader(traitCsv))) {
                String header = br.readLine();
                String line;
                while ((line=br.readLine())!=null) {
                    String[] p = line.split(";");
                    codesOld.add(p[0]);
                    ratiosOld.add(Double.parseDouble(p[3]));
                }
            }
            catch (IOException e) {
                
            }
            int n = codesOld.size();
            if (n==0) { IJ.error("Aucune entrée dans "+traitCsv); return; }


            //Sort in ascending order of ratios values. Apply the same sorting on both ratios and codes
            List<String> sortedCodes = new ArrayList<>(codesOld);
            List<Double> sortedRatios = new ArrayList<>(ratiosOld);
            Collections.sort(sortedRatios);
            Collections.sort(sortedCodes, new Comparator<String>() {
                @Override
                public int compare(String o1, String o2) {
                    return sortedRatios.indexOf(ratiosOld.get(codesOld.indexOf(o1))) - sortedRatios.indexOf(ratiosOld.get(codesOld.indexOf(o2)));
                }
            });
            List<String> codes = new ArrayList<>(sortedCodes);
            List<Double> ratios = new ArrayList<>(sortedRatios);

            //Verify if the sorting went properly
            System.out.println("Before");
            for(int i=0; i<n; i++){
                System.out.println("i="+i + " " + ratiosOld.get(i) + " " + codesOld.get(i));
            }
            System.out.println("After");
            for(int i=0; i<n; i++){
                System.out.println("i="+i + " " + sortedRatios.get(i) + " " + sortedCodes.get(i));
            }

            // 3. Préparer 11 stacks pour chaque slice
            //Detect the number of slices
            ImagePlus imgTest=IJ.openImage(basePath+"/Raw_patches_128x128xN"+"/"+codes.get(0)+".tif");
            
            int Nz=imgTest.getStackSize();
            ImagePlus[] imgTotSeg = new ImagePlus[Nz];
            ImagePlus[] imgTotRaw = new ImagePlus[Nz];
    
            for(int sl=1; sl<=Nz; sl++){
                ImageStack stackRaw = null, stackSeg = null;
                int w=0, h=0;
                Opener opener = new Opener();
                for (int i=0;i<n;i++) {
                    String code = codes.get(i);
                    String codeCube = code.split("__")[0];
                    String codeExpe = code.split("__")[1];
                    double ratio = ratios.get(i);

                    // raw
                    String rawPath = basePath+"/Raw_patches_128x128xN"+"/"+codes.get(i)+".tif";
                    ImagePlus impRaw = opener.openImage(rawPath);
                    if (impRaw==null) continue;
                    impRaw.setSlice(sl);
                    ImageProcessor ipRaw = impRaw.getProcessor().duplicate();
                    ipRaw.setFont(new Font("SansSerif", Font.BOLD, 11));
                    ipRaw.setColor(Color.yellow);
                    ipRaw.drawString(codeCube.replace("Cube_", ""), 5, 15);
                    ipRaw.drawString(codeExpe, 5, 30);
                    ipRaw.drawString(""+ratio, 80, 110);
                    // init stack
                    if (stackRaw==null) { w=ipRaw.getWidth(); h=ipRaw.getHeight(); stackRaw=new ImageStack(w,h); }
                    stackRaw.addSlice(codeCube, ipRaw);
                    // seg
                    String segPath = basePath+"/Seg_patches_128x128xN"+"/"+codes.get(i)+".tif";
                    ImagePlus impSeg = opener.openImage(segPath);
                    if (impSeg==null) continue;
                    impSeg.setSlice(sl);
                    ImageProcessor ipSeg = impSeg.getProcessor().duplicate();
                    ipSeg.setFont(new Font("SansSerif", Font.BOLD, 11));
                    ipSeg.setColor(Color.yellow);
                    ipSeg.drawString(codeCube.replace("Cube_", ""), 5, 15);
                    ipSeg.drawString(codeExpe, 5, 30);
                    ipSeg.setColor(Color.pink);
                    ipSeg.drawString(""+ratio, 80, 120);
                    if (stackSeg==null) { stackSeg=new ImageStack(w,h); }
                    stackSeg.addSlice(codeCube, ipSeg);
                }

                // Montage
                double ratioScreen = 16.0/9.0;
                int cols = (int)Math.ceil(Math.sqrt(n*ratioScreen));
                int rows = (int)Math.ceil((double)n/cols);
                MontageMaker mm = new MontageMaker();
                if (stackRaw!=null) {
                    ImagePlus montRaw = new ImagePlus("Montage Raw", stackRaw);
                    mm.makeMontage(montRaw, cols, rows, 1.0, 1, n, 1, 5, false);
                    imgTotRaw[sl-1] = IJ.getImage();
                }
                if (stackSeg!=null) {
                    ImagePlus montSeg = new ImagePlus("Montage Seg", stackSeg);
                    mm.makeMontage(montSeg, cols, rows, 1.0, 1, n, 1, 5, false);
                    imgTotSeg[sl-1] = IJ.getImage();
                }
            }
            // Stack final
            ImageStack stackSeg = new ImageStack(imgTotSeg[0].getWidth(), imgTotSeg[0].getHeight());
            ImageStack stackRaw = new ImageStack(imgTotRaw[0].getWidth(), imgTotRaw[0].getHeight());
            for (int i = 0; i < Nz; i++) {
                stackSeg.addSlice(String.valueOf(i), imgTotSeg[i].getProcessor().duplicate());
                stackRaw.addSlice(String.valueOf(i), imgTotRaw[i].getProcessor().duplicate());
            }
            ImagePlus montageSeg = new ImagePlus("Montage Seg", stackSeg);
            ImagePlus montageRaw = new ImagePlus("Montage Raw", stackRaw);
            for(int i=0;i<Nz;i++){
                if(imgTotRaw[i]!=null) imgTotRaw[i].close();
                if(imgTotSeg[i]!=null) imgTotSeg[i].close();
            }
            IJ.saveAsTiff(montageSeg, outSeg);
            IJ.saveAsTiff(montageRaw, outRaw);
            IJ.log("=== assembleAndShowPanel done ===");
       
    }
    



}
