#!/usr/bin/python

import re, os, sys, fileinput

from shutil import copyfile


INSTALL_DIR = "/userdata/external/mirrenc"


if __name__ == "__main__":

	""" 
	Run a piece of example code
	""" 
	
	userargstr = " ".join(sys.argv[1:])
	fullargstr = userargstr + " installdir=" + INSTALL_DIR
	
	javacall = "java -cp ../jclass net.danburfoot.examp4enc.ExampleEntry %s" % (fullargstr)
		
	print javacall
	
	os.system(javacall)

