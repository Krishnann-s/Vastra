package com.vastra.util;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.WString;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;

import java.util.Arrays;
import java.util.List;

/**
 * Sends a raw byte stream straight to a Windows print queue via
 * winspool.drv, the same mechanism BarTender/Seagull-style label tools use
 * for ZPL/TSPL/EPL commands. This deliberately skips GDI page rendering:
 * once a job's datatype is "RAW", the spooler does not scale, re-margin, or
 * re-paginate anything - it just streams the bytes to the printer's data
 * channel as-is, so the printer's own TSPL interpreter (SIZE/GAP/BITMAP/
 * PRINT) is the only thing deciding label size and positioning. That is
 * what a driver-based javafx.print.PrinterJob can never guarantee, since
 * the driver is free to reinterpret page layout/orientation/margins.
 */
final class RawPrinterHelper {

    private interface WinSpool extends StdCallLibrary {
        WinSpool INSTANCE = Native.load("winspool.drv", WinSpool.class, W32APIOptions.UNICODE_OPTIONS);

        boolean OpenPrinterW(WString pPrinterName, PointerByReference phPrinter, Pointer pDefault);
        boolean ClosePrinter(Pointer hPrinter);
        int StartDocPrinterW(Pointer hPrinter, int level, DOC_INFO_1 pDocInfo);
        boolean StartPagePrinter(Pointer hPrinter);
        boolean WritePrinter(Pointer hPrinter, Pointer pData, int cbBuf, IntByReference pcWritten);
        boolean EndPagePrinter(Pointer hPrinter);
        boolean EndDocPrinter(Pointer hPrinter);
    }

    public static class DOC_INFO_1 extends Structure {
        public WString pDocName;
        public WString pOutputFile;
        public WString pDatatype;

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("pDocName", "pOutputFile", "pDatatype");
        }
    }

    /**
     * Opens {@code printerName} (must match the exact Windows print queue
     * name, e.g. as shown in "Devices and Printers" / {@code Printer.getName()})
     * and writes {@code data} to it as a single RAW job.
     */
    static void sendBytesToPrinter(String printerName, byte[] data) throws Exception {
        PointerByReference phPrinter = new PointerByReference();
        if (!WinSpool.INSTANCE.OpenPrinterW(new WString(printerName), phPrinter, null)) {
            throw new Exception("Could not open printer '" + printerName +
                    "'. Check it's powered on, connected, and shows up in Windows' printer list.");
        }
        Pointer hPrinter = phPrinter.getValue();
        try {
            DOC_INFO_1 docInfo = new DOC_INFO_1();
            docInfo.pDocName = new WString("Vastra Label Print");
            docInfo.pOutputFile = null;
            docInfo.pDatatype = new WString("RAW");

            if (WinSpool.INSTANCE.StartDocPrinterW(hPrinter, 1, docInfo) == 0) {
                throw new Exception("StartDocPrinter failed for '" + printerName + "'");
            }
            try {
                if (!WinSpool.INSTANCE.StartPagePrinter(hPrinter)) {
                    throw new Exception("StartPagePrinter failed for '" + printerName + "'");
                }
                try {
                    Pointer pData = new Memory(data.length);
                    pData.write(0, data, 0, data.length);
                    IntByReference written = new IntByReference(0);
                    if (!WinSpool.INSTANCE.WritePrinter(hPrinter, pData, data.length, written)) {
                        throw new Exception("WritePrinter failed for '" + printerName + "'");
                    }
                    if (written.getValue() != data.length) {
                        throw new Exception("Only wrote " + written.getValue() + " of " +
                                data.length + " bytes to '" + printerName + "'");
                    }
                } finally {
                    WinSpool.INSTANCE.EndPagePrinter(hPrinter);
                }
            } finally {
                WinSpool.INSTANCE.EndDocPrinter(hPrinter);
            }
        } finally {
            WinSpool.INSTANCE.ClosePrinter(hPrinter);
        }
    }

    private RawPrinterHelper() {}
}
