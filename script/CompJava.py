#!/usr/bin/python

import re, os, sys, fileinput

from shutil import copyfile

INSTALL_DIR = "/userdata/external/mirrenc"

def getSrcFileList():
	return ["EncoderUtil", "Symbol", "HighPrecCoder", "EventModeler", "ArithBitio", "ModelerTree"]
		
def copyCode2Dir():
	
	for onesrc in getSrcFileList():
		
		farrpath = "/userdata/crm/src/java/encoder/%s.java" % (onesrc)
		nearpath = "/userdata/external/mirrenc/java/encoder/%s.java" % (onesrc)
		
		print "Going to copy %s ---> %s" % (farrpath, nearpath)
		
		# Change file to writable
		chmodcall = "chmod 644 %s" % (nearpath)
		os.system(chmodcall)		
		
		copyfile(farrpath, nearpath)
		
		# Change file back to read-only
		chmodcall = "chmod 444 %s" % (nearpath)
		os.system(chmodcall)

def compileDir(dirname):

	jcall = "javac -cp %s/jclass -d %s/jclass %s/java/%s/*.java" % (INSTALL_DIR, INSTALL_DIR, INSTALL_DIR, dirname)
	
	print("%s" % (jcall))
	
	os.system(jcall)


if __name__ == "__main__":

	""" 
	1) Copy the code from the main trunk
	2) Compile it
	""" 
	
	# copyCode2Dir()

	compileDir('encoder')
	compileDir('examp4enc')

