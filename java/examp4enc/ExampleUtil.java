
package net.danburfoot.examp4enc;

import java.io.*;
import java.util.*;


import net.danburfoot.shared.*;
import net.danburfoot.shared.Util.*;

import net.danburfoot.encoder.*;
import net.danburfoot.encoder.EncoderUtil.*;

public class ExampleUtil
{
	private static String _INSTALL_DIR;
	
	public enum EncodeDataType
	{
		image,
		text,
		statistical;
	}
	
	static void setInstallDir(String idir)
	{
		File installdir = new File(idir);
		
		Util.massert(installdir.exists(), "Installation directory %s does not exist", idir);
		Util.massert(installdir.isDirectory(), "Install dir path %s is not a director", idir);
		
		_INSTALL_DIR = installdir.getAbsolutePath();
	}
	
	static String getInstallDir()
	{
		Util.massert(_INSTALL_DIR != null,
			"Install Dir has not been set, you must call setInstallDir(..)");
		
		return _INSTALL_DIR;
	}

	
	public static String getDataFilePath(EncodeDataType dt, String subfile)
	{
		Util.massert(!subfile.contains("/"), 
			"Subfile path should be simple, found %s, don't include full directory path", subfile);
		
		return Util.sprintf("%s/data/%s/%s", getInstallDir(), dt, subfile);
	}
	
	public static File getDataFile(EncodeDataType dt, String subfile)
	{
		File datafile = new File(getDataFilePath(dt, subfile));
		
		// Util.massert(datafile.exists(), "Data File %s does not exist, did you spell correctly?", datafile.getAbsolutePath());
		
		return datafile;
	}
	
	public static File getImageFile(String bmppath)
	{
		Util.massert(bmppath.endsWith(".bmp"),
			"By convention, the BitMap file path should end with .bmp, found %s", bmppath);
		
		return getDataFile(EncodeDataType.image, bmppath);
	}
	
	public static File getBookFile(String txtfile)
	{
		Util.massert(txtfile.endsWith(".txt"),
			"By convention, the Book file path should end with .txt, found %s", txtfile);
		
		return getDataFile(EncodeDataType.text, txtfile);		
	}
	
}	
