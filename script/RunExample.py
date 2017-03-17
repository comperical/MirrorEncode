#!/usr/bin/python

import re, os, sys, fileinput

from shutil import copyfile




if __name__ == "__main__":

	""" 
	Run a piece of example code
	""" 
	
	javacall = "java -cp ../jclass net.danburfoot.examp4enc.ExampleEntry %s" % (" ".join(sys.argv[1:]))
		
	print javacall
	
	os.system(javacall)

