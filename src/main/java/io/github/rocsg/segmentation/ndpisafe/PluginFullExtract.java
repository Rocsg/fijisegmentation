package io.github.rocsg.segmentation.ndpisafe;
import io.github.rocsg.fijiyama.common.VitiDialogs;

import ij.ImagePlus;
import ij.io.OpenDialog;
import ij.plugin.frame.PlugInFrame;

public class PluginFullExtract extends PlugInFrame{
	private static final long serialVersionUID = 1L;

	public PluginFullExtract() {		super("");	}
	
	public void run(String arg) {
		OpenDialog od=new OpenDialog("Choose a ndpi file");
		NDPI ndpi=new NDPI(od.getPath(),false);
		int level=VitiDialogs.getIntUI("Choose a resolution level (0=high-res, "+ndpi.N+"=low-res", ndpi.previewLevelN); 
		ImagePlus img=ndpi.getExtract(level);
		img.show();		
	}	
}
