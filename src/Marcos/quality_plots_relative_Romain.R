# LOAD PACKAGES ===================================================
source("1-code/0-quantity_quality_base_code/statistics_base_code.R")
source("1-code/0-quantity_quality_base_code/plot_size.R")
library("png")
library(grid)
library(broom)
library(lmtest)
library(ggpubr)   # for as.ggplot.
library(performance)
library(patchwork)
library(gridExtra)  # for arranging multiple plots
library(nlme)


# Créer les dossiers de sortie dans le répertoire de travail
output_dir <- file.path("2-export/final_light_quality/dumbell_plot")
# Créer les dossiers avec récursivité
if (!dir.exists(output_dir)) dir.create(output_dir, recursive = TRUE)
cat("Dossiers créés :\n")
cat("- Output:", output_dir, "\n")
# LOAD treatment COLORS -----------------------------------------------------
load("1-code/0-quantity_quality_base_code/treatment_color2.RData")

print(treatment_colors)
print(position_shapes)


# -----------------------------
veg_relative_data <- "2-export/final_DLI_analysis/statistic/vegetative/Tables/veg_light_quality_relative_diff.csv"
rep_relative_data <- "2-export/final_DLI_analysis/statistic/reproductive/Tables/rep_light_quality_relative_diff.csv"
# -----------------------------
veg_traits<-c("Stems number" ,"Leaves number", "TH (cm)","Leaves biomass (g)","TAGB (g)","FL-1 lenght (cm)","PLA (cm²)","SLA (cm²·g⁻¹)")
rep_traits<-c("Panicles number","TKW (g)")

veg<-data.table::fread(input = veg_relative_data) %>% 
  filter(trait%in%veg_traits)

rep<-data.table::fread(input = rep_relative_data) %>% 
  filter(trait%in%rep_traits)

all_traits<-rbind(veg,rep) %>% 
  mutate(trait=ifelse(trait=="Stems number","Tillers number",
                      ifelse(trait=="TH (cm)","Tiller height (cm)",
                             ifelse(trait=="FL-1 lenght (cm)","FL-1 length (cm)",trait))))

data_list <- lapply(1:nrow(all_traits), function(i) {
  row <- all_traits[i, ]
  list(
    Treatment = toupper(substr(row$treatment,1,1)) %>% paste0(substr(row$treatment,2,nchar(row$treatment))), # capitalize
    position     = toupper(substr(row$position,1,1)) %>% paste0(substr(row$position,2,nchar(row$position))),     # capitalize
    trait     = row$trait,
    Mean      = row$mean,
    Std       = row$se_variation
  )
})
# Inspect first 3 elements
data_list[1:3]

# "La plus grande différence FS/NFS doit être en haut, la plus petite doit etre en bas."

# Compute abs difference per trait (averaged across treatments, like the Python script)
trait_order <- all_traits %>%
  group_by(trait) %>%
  summarise(
    Avg_FS = abs(mean(mean[treatment == "AFS"], na.rm=TRUE)),
    Avg_NFS = abs(mean(mean[treatment == "NS"], na.rm=TRUE)),
    Diff = abs(Avg_FS - Avg_NFS)
  ) %>%
  arrange(-Diff) %>% # Smallest to Largest
  pull(trait)

# Print differences for verification
print("Computed differences (|Avg NS - Avg AFS|):")

all_traits %>%
  group_by(trait) %>%
  summarise(
    Avg_FS = abs(mean(mean[treatment == "AFS"], na.rm=TRUE)),
    Avg_NFS = abs(mean(mean[treatment == "NS"], na.rm=TRUE)),
    Diff = abs(Avg_FS - Avg_NFS)
  ) %>%
  arrange(-Diff) %>% # Smallest to Largest
  print()

# Set factor levels. 
# In ggplot Y axis, let's put Smallest at Bottom -> Largest at Top.
# So levels should be Ascending Diff.
all_traits$trait <- factor(all_traits$trait, levels = trait_order)

# Order Treatments: East, West, Central (as in Python script list order)
# Python script order: data = {East, West, Central}
# To make them appear top-to-bottom within the facet or Y-axis, we need to consider how ggplot plots.
# If Y is continuous/factor, default is bottom-up.
# We want "East (top), West, Central (bottom)" usually for reading?
# The Python script layout loops East, West, Central and adds `row_gap`.
# So East is at Y=0, West Y=1, Central Y=2.
# Then `invert_yaxis()` flips it. So East is Top (Y=0 -> Top).
# To replicate "East Top, Central Bottom" in ggplot y-axis, we need levels = c("Central", "West", "East").
all_traits$position <- factor(all_traits$position, levels = c("west", "central", "east"))

# -----------------------------
# Reshape for Dumbbell Plotting
# -----------------------------
# We need FS and NFS in same row to draw segments
df_wide <- all_traits %>%
  pivot_wider(
    names_from = treatment,
    values_from = c(mean, se_variation)
  )

df_wide <- df_wide %>%
  mutate(trait_label = trait)  # just copy original name

# Colors
c_NS  <- "#5e3c99"
c_AFS <- "#b2abd2"

# -----------------------------
# Plot
# -----------------------------
# Capsule error bars need to be simulated with geom_segment and big size + rounded ends

# Create a long format data frame for means and SEs for NS and AFS
df_long <- df_wide %>%
  pivot_longer(
    cols = c(mean_NS, mean_AFS, se_variation_NS, se_variation_AFS),
    names_to = c(".value", "treatment"),
    names_pattern = "(mean|se_variation)_(NS|AFS)"
  )

# ## save plot to creat a PDF later
# ggsave(file.path(output_dir, paste0("dumbell_plot_all_traits",".png")), 
#        plot = p, width = 12, height = 8, dpi = 300)


# Convert position factor to numeric codes
df_wide <- df_wide %>%
  mutate(position_num = as.numeric(position))

df_long <- df_wide %>%
  pivot_longer(
    cols = c(mean_NS, mean_AFS, se_variation_NS, se_variation_AFS),
    names_to = c(".value", "treatment"),
    names_pattern = "(mean|se_variation)_(NS|AFS)"
  ) %>%
  mutate(position_num = as.numeric(position))


# Calculate max_x and buffer
max_x <- max(
  abs(df_wide$mean_NS + df_wide$se_variation_NS),
  abs(df_wide$mean_NS - df_wide$se_variation_NS),
  abs(df_wide$mean_AFS + df_wide$se_variation_AFS),
  abs(df_wide$mean_AFS - df_wide$se_variation_AFS)
)
buffer <- 0.05 * max_x

df_long$position <- factor(df_long$position, levels = c("east", "central", "west"))
df_long$treatment <- factor(df_long$treatment, levels = c("NS", "AFS"))



p2 <- ggplot(df_long, aes(y = position_num)) +
  geom_vline(xintercept = 0, color = "#e66101", linewidth = 1) +
  geom_segment(aes(x = mean - se_variation, xend = mean + se_variation, y = position_num, yend = position_num, color = treatment, alpha = treatment),
               linewidth = 5, lineend = "round", show.legend = FALSE) +
  scale_alpha_manual(values = c(NS = 0.2, AFS = 0.3)) +
  geom_segment(aes(x = mean_NS, xend = mean_AFS, y = position_num, yend = position_num),
               data = df_wide, inherit.aes = FALSE, color = "black", linewidth = 0.8, show.legend = FALSE) +
  geom_point(aes(x = mean, shape = position, fill = treatment), 
             color = "black",  # black border for shapes 21-25
             size = 5, stroke = 1) +
  scale_shape_manual(values = c(
    east = 24,    # triangle up with border
    central = 22, # square with border
    west = 3      # plus (no border control)
  )) +
  facet_grid(trait_label ~ ., switch = "y", labeller = label_value) +
  scale_x_continuous(limits = c(-max_x - buffer, max_x + buffer),
                     expand = expansion(mult = 0.05)) +
  scale_y_continuous(
    breaks = df_wide$position_num,
    labels = NULL,
    sec.axis = sec_axis(~ ., breaks = df_wide$position_num, labels = NULL, name = "")  # removed labels here
  ) +
  labs(x = "Variation relative to open field (%)", y = NULL, color = "", fill = "", shape = "Position") +
  coord_cartesian(clip = "off") +
  theme_ppt_readable_axes() +
  theme(
    legend.position = "bottom",
    panel.spacing.y = unit(0.6, "cm"),
    strip.text.y.left = element_text(angle = 0, size = 15, hjust = 1),
    axis.text.y.left = element_blank(),
    axis.ticks.y.left = element_blank(),
    axis.title.y.right = element_text(size = 14, face = "bold", color = "black")
  ) +
  scale_shape_manual(values = c(`incident PAR`=3,
    east = 24,    # triangle up with border
    central = 22, # square with border
    west = 21     # filled circle (instead of plus)
  ))+
  scale_color_manual(values =treatment_colors) +
  scale_fill_manual(values = treatment_colors) +
  guides(
    shape = guide_legend(
      order = 1,
      override.aes = list(
        fill = "black",
        color = "black",
        size = 3,      # <-- size for legend shapes here
        stroke = 1
      )
    ),
    fill = guide_legend(
      order = 2,
      override.aes = list(
        shape = 21,
        color = "black",
        size = 3.5,      # <-- size for legend fills here
        stroke = 1
      )
    ),
    color = "none"
  )


print(p2)

saveRDS(p2,file = file.path(output_dir, paste0("dumbell_plot_all_traits2",".rds")))
