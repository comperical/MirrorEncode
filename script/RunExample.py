#!/usr/bin/python

import re, os, sys, fileinput

from shutil import copyfile

import CompJava

if __name__ == "__main__":

	""" 
	Run a piece of example code
	""" 
	
	userargstr = " ".join(sys.argv[1:])
	fullargstr = userargstr + " installdir=" + CompJava.INSTALL_DIR
	
	javacall = "java -cp %s/jclass net.danburfoot.examp4enc.ExampleEntry %s" % (CompJava.INSTALL_DIR, fullargstr)
		
	print javacall
	
	os.system(javacall)

