/**
*******************************************************************************
* Copyright (C) 1996-2001, International Business Machines Corporation and    *
* others. All Rights Reserved.                                                *
*******************************************************************************
*
* $Source: /home/cvsroot/unicodetools/org/unicode/text/UCD/DiffPropertyLister.java,v $
* $Date: 2001-12-06 00:05:53 $
* $Revision: 1.5 $
*
*******************************************************************************
*/

package com.ibm.text.UCD;
import java.io.*;

class DiffPropertyLister extends PropertyLister {
    private UCD oldUCD;

    public DiffPropertyLister(String oldUCDName, String newUCDName, PrintWriter output) {
        this.output = output;
        this.ucdData = UCD.make(newUCDName);
        if (oldUCDName != null) this.oldUCD = UCD.make(oldUCDName);
        breakByCategory = false;
        useKenName = false;
    }

    public String valueName(int cp) {
        return major_minor_only(ucdData.getVersion());
    }

    /*
    public String optionalName(int cp) {
        if ((propMask & 0xFF00) == DECOMPOSITION_TYPE) {
            return Utility.hex(ucdData.getDecompositionMapping(cp));
        } else {
            return "";
        }
    }
    */


    public byte status(int cp) {
        /*if (cp == 0xFFFF) {
            System.out.println("# " + Utility.hex(cp));
        }
        */
        return ucdData.isAllocated(cp) && (oldUCD == null || !oldUCD.isAllocated(cp)) ? INCLUDE : EXCLUDE;
    }
    
    public String headerString() {
        String result;
        if (oldUCD != null) {
            result = "# Differences between " 
                + major_minor_only(ucdData.getVersion()) 
                + " and " 
                + major_minor_only(oldUCD.getVersion());
        } else {
            result = "# Designated as of " 
                + major_minor_only(ucdData.getVersion())
                + " [excluding removed Hangul Syllables]";
        }
        //System.out.println("hs: " + result);
        return result;
    }
    
    /*
    public int print() {
        String status;
        if (oldUCD != null) {
            status = "# Differences between " + ucdData.getVersion() + " and " + oldUCD.getVersion();
        } else {
            status = "# Allocated as of " + ucdData.getVersion();
        }
        output.println();
        output.println();
        output.println(status);
        output.println();
        System.out.println(status);
        int count = super.print();
        output.println();
        if (oldUCD != null) {
            output.println("# Total " + count + " new code points allocated in " + ucdData.getVersion());
        } else {
            output.println("# Total " + count + " code points allocated in " + ucdData.getVersion());
        }

        output.println();
        return count;
    }
    */
    
    private String major_minor_only(String s) {
        return s.substring(0, s.lastIndexOf('.'));
    }

}

