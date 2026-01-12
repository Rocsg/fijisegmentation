package io.github.rocsg.segmentation.test;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.process.ImageProcessor;
import inra.ijpb.binary.distmap.DistanceTransform3D;
import inra.ijpb.binary.BinaryImages;

import java.io.File;
import java.io.FileWriter;
import java.io.BufferedWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Calcule la distance de Hausdorff entre les contours mirrored_ellipsoid et ellipsoid_contour
 * Pour chaque point du contour mirrored_ellipsoid, calcule sa distance au contour ellipsoid_contour
 * Statistiques: proportion de pixels non compris (distance > 0) et distance moyenne
 */
public class Test_Hausdorff_Distance {
    
    private static final String BASE_DIR = "/home/rfernandez/Bureau/A_Test/Gargee/Hausdorff";
    
    public static void main(String[] args) {
        ImageJ ij = new ImageJ();
        
        IJ.log("\\Clear");
        IJ.log("=== Hausdorff Distance Analysis ===");
        IJ.log("Base directory: " + BASE_DIR);
        
        // Liste tous les fichiers mirrored_ellipsoid
        File baseFolder = new File(BASE_DIR);
        
        // Debug: vérifier si le dossier existe
        if (!baseFolder.exists()) {
            IJ.error("Directory does not exist: " + BASE_DIR);
            return;
        }
        
        IJ.log("Directory exists: " + baseFolder.getAbsolutePath());
        
        // Debug: lister tous les fichiers
        File[] allFiles = baseFolder.listFiles();
        if (allFiles == null) {
            IJ.error("Cannot list files in directory");
            return;
        }
        
        IJ.log("Total files in directory: " + allFiles.length);
        IJ.log("\nListing all .tif files:");
        for (File f : allFiles) {
            if (f.getName().toLowerCase().endsWith(".tif")) {
                IJ.log("  - " + f.getName());
            }
        }
        
        IJ.log("\nSearching for mirrored_ellipsoid files with pattern: Positive_Z_B_###_J141_mirrored_ellipsoid.tif");
        
        File[] files = baseFolder.listFiles((dir, name) -> 
            name.matches("Positive_Z_B_\\d{3}_J141_mirrored_ellipsoid\\.tif"));
        
        if (files == null || files.length == 0) {
            IJ.log("No files matched the pattern!");
            IJ.log("\nTrying alternative pattern search...");
            
            // Try to find any file with "mirrored_ellipsoid"
            File[] altFiles = baseFolder.listFiles((dir, name) -> 
                name.contains("mirrored_ellipsoid") && name.endsWith(".tif"));
            
            if (altFiles != null && altFiles.length > 0) {
                IJ.log("Found " + altFiles.length + " files containing 'mirrored_ellipsoid':");
                for (File f : altFiles) {
                    IJ.log("  - " + f.getName());
                }
            }
            
            IJ.error("No mirrored_ellipsoid files found matching the expected pattern");
            return;
        }
        
        IJ.log("Found " + files.length + " pairs to process\n");
        
        // Prépare le fichier CSV de résultats
        String csvPath = BASE_DIR + File.separator + "hausdorff_results.csv";
        List<String[]> results = new ArrayList<>();
        results.add(new String[]{"Sample", "Total_Points", "Points_Outside", "Proportion_Outside", 
                                  "Mean_Distance", "Max_Distance", "Mean_Distance_Outside"});
        
        // Traite chaque paire
        int pairIndex = 0;
        for (File mirroredFile : files) {
            pairIndex++;
            String filename = mirroredFile.getName();
            // Extrait le numéro (???) - Format: Positive_Z_B_217_J141_mirrored_ellipsoid.tif
            //                                   0123456789012345
            String number = filename.substring(13, 16); // Position 13-15 pour les 3 chiffres
            
            String contourFilename = "Positive_Z_B_" + number + "_J141_ellipsoid_contour.tif";
            File contourFile = new File(BASE_DIR, contourFilename);
            
            if (!contourFile.exists()) {
                IJ.log("WARNING: Contour file not found for " + number);
                IJ.log("  Expected: " + contourFilename);
                continue;
            }
            
            IJ.log("\n[" + pairIndex + "/" + files.length + "] Processing pair " + number + "...");
            
            // Charge les images
            ImagePlus mirroredImp = IJ.openImage(mirroredFile.getAbsolutePath());
            ImagePlus contourImp = IJ.openImage(contourFile.getAbsolutePath());
            
            if (mirroredImp == null || contourImp == null) {
                IJ.log("  ERROR: Could not open images");
                continue;
            }
            
            // Calcule les statistiques de distance
            double[] stats = computeDistanceStats(mirroredImp, contourImp, number);
            
            // stats = [totalPoints, pointsOutside, proportionOutside, meanDistance, maxDistance, meanDistanceOutside]
            results.add(new String[]{
                "B_" + number,
                String.format("%d", (int)stats[0]),
                String.format("%d", (int)stats[1]),
                String.format("%.6f", stats[2]),
                String.format("%.4f", stats[3]),
                String.format("%.4f", stats[4]),
                String.format("%.4f", stats[5])
            });
            
            mirroredImp.close();
            contourImp.close();
            
            IJ.log("  Total points in contour of mask : " + (int)stats[0]);
            IJ.log("  Points outside the thick contour of ellipsoid : " + (int)stats[1] + " (" + String.format("%.2f%%", stats[2]*100) + ")");
            IJ.log("  Mean distance between all mask contour points and ellipsoid contour: " + String.format("%.4f", stats[3]));
            IJ.log("  Max distance: " + String.format("%.4f", stats[4]));
            IJ.log("  Mean distance (outside only): " + String.format("%.4f", stats[5]) + "\n");
        }
        
        // Écrit le CSV
        try {
            writeCSV(csvPath, results);
            IJ.log("Results saved to: " + csvPath);
        } catch (IOException e) {
            IJ.error("Error writing CSV: " + e.getMessage());
        }
        
        IJ.log("\n=== Analysis Complete ===");
        IJ.showMessage("Hausdorff Analysis Complete", 
                      results.size() - 1 + " pairs processed.\nResults saved to:\n" + csvPath);
    }
    
    /**
     * Calcule les statistiques de distance entre mirrored et contour
     * @return [totalPoints, pointsOutside, proportionOutside, meanDistance, maxDistance, meanDistanceOutside]
     */
    private static double[] computeDistanceStats(ImagePlus mirroredImp, ImagePlus contourImp, String sampleId) {
        int width = mirroredImp.getWidth();
        int height = mirroredImp.getHeight();
        int depth = mirroredImp.getStackSize();
        
        IJ.log("  Computing bounding boxes...");
        // Calcule la bounding box du contour épaissi
        int minXc = width, maxXc = 0, minYc = height, maxYc = 0, minZc = depth, maxZc = 0;
        for (int z = 1; z <= depth; z++) {
            ImageProcessor contourProc = contourImp.getStack().getProcessor(z);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    if (contourProc.get(x, y) == 255) {
                        if (x < minXc) minXc = x;
                        if (x > maxXc) maxXc = x;
                        if (y < minYc) minYc = y;
                        if (y > maxYc) maxYc = y;
                        if (z-1 < minZc) minZc = z-1;
                        if (z-1 > maxZc) maxZc = z-1;
                    }
                }
            }
        }
        
        // Calcule la bounding box du mirrored
        int minXm = width, maxXm = 0, minYm = height, maxYm = 0, minZm = depth, maxZm = 0;
        for (int z = 1; z <= depth; z++) {
            ImageProcessor mirroredProc = mirroredImp.getStack().getProcessor(z);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    if (mirroredProc.get(x, y) == 255) {
                        if (x < minXm) minXm = x;
                        if (x > maxXm) maxXm = x;
                        if (y < minYm) minYm = y;
                        if (y > maxYm) maxYm = y;
                        if (z-1 < minZm) minZm = z-1;
                        if (z-1 > maxZm) maxZm = z-1;
                    }
                }
            }
        }
        
        IJ.log("  Contour bbox: [" + minXc + "-" + maxXc + ", " + minYc + "-" + maxYc + ", " + minZc + "-" + maxZc + "]");
        IJ.log("  Mirrored bbox: [" + minXm + "-" + maxXm + ", " + minYm + "-" + maxYm + ", " + minZm + "-" + maxZm + "]");
        
        IJ.log("  Building list of contour points...");
        // Collecte tous les points du contour épaissi (dans sa bounding box)
        List<int[]> contourPoints = new ArrayList<>();
        for (int z = minZc + 1; z <= maxZc + 1; z++) {
            ImageProcessor contourProc = contourImp.getStack().getProcessor(z);
            for (int y = minYc; y <= maxYc; y++) {
                for (int x = minXc; x <= maxXc; x++) {
                    if (contourProc.get(x, y) == 255) {
                        contourPoints.add(new int[]{x, y, z-1}); // z-1 pour 0-based
                    }
                }
            }
        }
        
        IJ.log("  Found " + contourPoints.size() + " contour points");
        IJ.log("  Analyzing distances for mirrored points...");
        
        int totalPoints = 0;
        int pointsOutside = 0;
        double sumDistances = 0;
        double sumDistancesOutside = 0;
        double maxDistance = 0;
        
        // Pour chaque point du mirrored_ellipsoid (dans sa bounding box)
        for (int z = minZm + 1; z <= maxZm + 1; z++) {
            ImageProcessor mirroredProc = mirroredImp.getStack().getProcessor(z);
            
            for (int y = minYm; y <= maxYm; y++) {
                for (int x = minXm; x <= maxXm; x++) {
                    // Si ce pixel fait partie du contour mirrored
                    if (mirroredProc.get(x, y) == 255) {
                        totalPoints++;
                        
                        // Trouve la distance minimale au contour épaissi
                        double minDist = Double.MAX_VALUE;
                        
                        for (int[] contourPt : contourPoints) {
                            double dx = x - contourPt[0];
                            double dy = y - contourPt[1];
                            double dz = (z-1) - contourPt[2];
                            double dist = Math.sqrt(dx*dx + dy*dy + dz*dz);
                            
                            if (dist < minDist) {
                                minDist = dist;
                            }
                            
                            // Optimisation: si distance = 0, on peut arrêter
                            if (minDist == 0) break;
                        }
                        
                        sumDistances += minDist;
                        
                        if (minDist > maxDistance) {
                            maxDistance = minDist;
                        }
                        
                        if (minDist > 0) {
                            pointsOutside++;
                            sumDistancesOutside += minDist;
                        }
                    }
                }
            }
            
            // Log progress every 10 slices
           
        }
        
        double proportionOutside = totalPoints > 0 ? (double)pointsOutside / totalPoints : 0;
        double meanDistance = totalPoints > 0 ? sumDistances / totalPoints : 0;
        double meanDistanceOutside = pointsOutside > 0 ? sumDistancesOutside / pointsOutside : 0;
        
        return new double[]{totalPoints, pointsOutside, proportionOutside, 
                           meanDistance, maxDistance, meanDistanceOutside};
    }
    
    private static void writeCSV(String path, List<String[]> rows) throws IOException {
        BufferedWriter writer = new BufferedWriter(new FileWriter(path));
        
        for (String[] row : rows) {
            writer.write(String.join(",", row));
            writer.write("\n");
        }
        
        writer.close();
    }
}
