import pandas as pd
import matplotlib.pyplot as plt

# Load CSV
csv_path = "/Donnees/DD_CIRS626_DATA/Data_Cube_HTP/stats_lacuna_gaine.csv"
df = pd.read_csv(csv_path)

# Scatter plot: x = median gaine, y = median lacuna
plt.figure()
plt.scatter(df['median_gaine'], df['median_lacuna'])
plt.xlabel('Median Gaine Surface (pixels)')
plt.ylabel('Median Lacuna Surface (pixels)')
plt.title('Scatterplot of Median Gaine vs. Median Lacuna Surface')
plt.grid(True)
plt.show()
