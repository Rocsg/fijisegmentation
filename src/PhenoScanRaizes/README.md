# PhenoScanRaizes Pipeline

This pipeline processes root scan images to extract and quantify vein and aerenchyma features using machine learning segmentation and morphological operations.

## Directory Structure

```
Project_Directory/
├── Raw/                              # Input RGB images
└── Processing/
    ├── 0_Grayscale/                  # Converted grayscale images
    ├── 1_Patches_coordinates/        # CSV files with click coordinates
    ├── 2_Extracted_patches/          # Extracted image patches
    ├── 3_Predicted_segmentation/     # ML segmentation results
    ├── 4_Segmentation/               # Extracted vein and aerenchyma masks
    ├── 5_Results/                    # Final measurements (results.csv)
    └── Model/
        └── trained_model.model       # Weka trained segmentation model
```

## Pipeline Scripts

### Script 0: Prepare Images
**File:** `Script_0_Prepare_Images.bsh`

Converts RGB images to grayscale for processing.
- **Input:** `Raw/*.{tif,jpg,png}`
- **Output:** `Processing/0_Grayscale/*.tif`

### Script 1: Collect Coordinates
**File:** `Script_1_Collect_Coordinates.bsh`

Interactive script to mark 3 points of interest on each image.
- **Input:** `Processing/0_Grayscale/*.tif`
- **Output:** `Processing/1_Patches_coordinates/*_1.csv, *_2.csv, *_3.csv`
- **User Action:** Click 3 points on each displayed image

### Script 2: Extract Patches
**File:** `Script_2_Extract_Patches.bsh`

Extracts 256x256 pixel patches around marked coordinates.
- **Input:** 
  - `Processing/1_Patches_coordinates/*.csv`
  - `Processing/0_Grayscale/*.tif`
- **Output:** `Processing/2_Extracted_patches/*.tif`
- **Parameters:** `PATCH_SIZE = 256` (modifiable in script)

### Script 3: Predict Segmentation
**File:** `Script_3_Predict_Segmentation.bsh`

Applies Weka Trainable Segmentation model to classify tissue types.
- **Input:** 
  - `Processing/2_Extracted_patches/*.tif`
  - `Processing/Model/trained_model.model`
- **Output:** `Processing/3_Predicted_segmentation/*.tif`
- **User Action:** Specify which class index to extract (e.g., 0, 1, 2)

### Script 4: Extract Vein and Aerenchyma
**File:** `Script_4_Extract_Vein_And_Aerenchyma.bsh`

Applies morphological operations to separate vein from aerenchyma.

**Processing steps:**
1. Fill holes in segmentation
2. Extract largest connected component (centered)
3. Morphological opening (erosion + dilation)
4. Extract vein (original - opened)
5. Extract aerenchyma (opened result)

- **Input:** `Processing/3_Predicted_segmentation/*.tif`
- **Output:** 
  - `Processing/4_Segmentation/*_vein.tif`
  - `Processing/4_Segmentation/*_aerenchyma.tif`
- **Parameters:** 
  - `OPENING_RADIUS = 8` (modifiable in script)

### Script 5: Compute Surface and Traits
**File:** `Script_5_Compute_Surface_And_Traits.bsh`

Measures areas and computes the aerenchyma/vein ratio.
- **Input:** `Processing/4_Segmentation/*_vein.tif, *_aerenchyma.tif`
- **Output:** `Processing/5_Results/results.csv`

**Output CSV columns:**
- `patch_name`: Identifier for each patch
- `vein_area_pixels`: Vein area in pixels
- `aerenchyma_area_pixels`: Aerenchyma area in pixels
- `aerenchyma_vein_ratio`: Ratio of aerenchyma to vein area

## How to Run

### Option 1: Run in Fiji (Recommended)

1. Open Fiji/ImageJ
2. Navigate to `Plugins > Macros > Run...`
3. Select the script (e.g., `Script_0_Prepare_Images.bsh`)
4. Follow on-screen prompts
5. Repeat for scripts 1-5 in sequence

### Option 2: Convert to Java Plugins

If you need to distribute as JAR plugins:
1. The scripts can be converted to Java classes
2. Compile with the project's Maven configuration
3. Install the resulting JAR in Fiji's plugins folder

## Prerequisites

### Fiji Plugins Required:
- **Trainable Weka Segmentation** (included in standard Fiji)
- ImageJ base installation

### Input Requirements:
1. RGB or grayscale images in `Raw/` directory
2. Trained Weka model in `Processing/Model/trained_model.model`
   - Train using Fiji's Trainable Weka Segmentation plugin
   - Save as `trained_model.model`

## Notes

- **Patch Size:** Default is 256x256 pixels. Modify `PATCH_SIZE` variable in Script 2 if needed.
- **Morphological Parameters:** Adjust `OPENING_RADIUS` in Script 4 for different tissue scales.
- **Class Selection:** In Script 3, choose the appropriate class index that corresponds to root tissue (not background).
- **Memory:** For large images or many patches, increase Fiji's memory allocation in `Edit > Options > Memory & Threads`.

## Troubleshooting

**Issue:** Script fails with "Model not found"
- **Solution:** Ensure `trained_model.model` exists in `Processing/Model/`

**Issue:** Patches appear empty or black
- **Solution:** Verify coordinates are within image bounds; check grayscale conversion

**Issue:** No segmentation output
- **Solution:** Verify correct class index; check that model is compatible with patch images

**Issue:** Morphological operations too aggressive/weak
- **Solution:** Adjust `OPENING_RADIUS` parameter in Script 4

## Contact

For issues specific to this pipeline, refer to the parent project documentation or contact the development team.

## References

Based on similar pipelines in:
- `aerenchyme/E_Extract_areas_with_morpho_and_corresponding_annotations/`
- `aerenchyme/B_Prepare_patches/`
- `aerenchyme/C_Weka_prepare_stack_and_train_exhaustive/`
