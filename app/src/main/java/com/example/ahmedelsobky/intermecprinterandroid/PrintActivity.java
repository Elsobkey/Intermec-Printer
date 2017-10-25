package com.example.ahmedelsobky.intermecprinterandroid;

import android.app.Activity;
import android.content.res.AssetManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.ganesh.intermecarabic.Arabic864;
import com.honeywell.mobility.lineprint.sample.R;
import com.honeywell.mobility.print.LinePrinter;
import com.honeywell.mobility.print.LinePrinterException;
import com.honeywell.mobility.print.PrintProgressEvent;
import com.honeywell.mobility.print.PrintProgressListener;
import com.honeywell.mobility.print.PrinterException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;


/**
 * This sample demonstrates printing on an Android computer using the LinePrinter
 * class. You may enter or scan a Honeywell mobile printer's MAC address and
 * click the Print button to print. The MAC Address text should have the format
 * of "nn:nn:nn:nn:nn:nn" or "nnnnnnnnnnnn" where each n is a hex digit.
 * <p>
 * You may also capture a signature to print by clicking the Sign button. It
 * will display another screen for you to sign and save the signature. After
 * you save the signature, you will see a preview of the signature graphic
 * next to the Sign button.
 * <p>
 * The printing progress will be displayed in the Progress and Status text box.
 */
public class PrintActivity extends Activity {

    private static final String TAG = PrintActivity.class.getName();
    private Button mPrintBtn;
    private TextView mTextMessageTV;
    private EditText mPrinterIdET;
    private EditText mPrinterMacAddressET;
    private LinePrinter mLinePrinter = null;
    private String mJsonCmdAttributeStr = null;
    final String PRINTER_TYPE = "PR3"; // printer type here ex : pr3 , pb31...
    final String PRINTER_MAC_ADDRESS = "00:1D:DF:57:06:59"; // printer type here ex : pr3 , pb31...

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.activity_print);
        init();
        printerConfig();

        mPrintBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                //Executes PrintTask with the specified parameter which is passed
                new PrintTask().execute(mPrinterIdET.getText().toString(), mPrinterMacAddressET.
                        getText().toString());
            }
        });
    }

    private void printerConfig() {
        mPrinterIdET.setText(PRINTER_TYPE); // Set a default Printer ID.
        mPrinterMacAddressET.setText(PRINTER_MAC_ADDRESS); // Set a default Mac Address
    }

    private void init() {
        mTextMessageTV = (TextView) findViewById(R.id.textMsg);
        mPrinterIdET = (EditText) findViewById(R.id.editPrinterID);
        mPrinterMacAddressET = (EditText) findViewById(R.id.editMacAddr);
        mPrintBtn = (Button) findViewById(R.id.buttonPrint);
        readAssetFiles();
    }

    private void readAssetFiles() {
        InputStream input = null;
        ByteArrayOutputStream output = null;
        AssetManager assetManager = PrintActivity.this.getAssets();
        String fileName = "printer_profiles.JSON";
        int fileIndex = 0, initialBufferSize;

        try {
            input = assetManager.open(fileName);
            initialBufferSize = 8000;
            output = new ByteArrayOutputStream(initialBufferSize);

            byte[] buf = new byte[1024];
            int len;
            while ((len = input.read(buf)) > 0) {
                output.write(buf, 0, len);
            }
            input.close();
            input = null;

            output.flush();
            output.close();
            switch (fileIndex) {
                case 0:
                    mJsonCmdAttributeStr = output.toString();
                    break;
            }
            output = null;
        } catch (Exception ex) {
            Log.v(TAG, ex.getMessage());
        } finally {
            try {
                if (input != null) {
                    input.close();
                    //input = null;
                }
                if (output != null) {
                    output.close();
                    //output = null;
                }
            } catch (IOException ex) {
                Log.v(TAG, ex.getMessage());
            }
        }
    }

    /**
     * This class demonstrates printing in a background thread and updates
     * the UI in the UI thread.
     */
    private class PrintTask extends AsyncTask<String, Integer, String> {
        private static final String PROGRESS_CANCEL_MSG = "Printing cancelled\n";
        private static final String PROGRESS_COMPLETE_MSG = "Printing completed\n";
        private static final String PROGRESS_END_DOC_MSG = "End of document\n";
        private static final String PROGRESS_FINISHED_MSG = "Printer connection closed\n";
        private static final String PROGRESS_NONE_MSG = "Unknown progress message\n";
        private static final String PROGRESS_START_DOC_MSG = "Start printing document\n";


        /**
         * Runs on the UI thread before doInBackground(Params...).
         */
        @Override
        protected void onPreExecute() {
            // Clears the Progress and Status text box.
            mTextMessageTV.setText("");
            // Disables the Print button.
            mPrintBtn.setEnabled(false);

            // Shows a progress icon on the title bar to indicate
            setProgressBarIndeterminateVisibility(true);
        }

        /**
         * This method runs on a background thread. The specified parameters
         * are the parameters passed to the execute method by the caller of
         * this task. This method can call publishProgress to publish updates
         * on the UI thread.
         */
        @Override
        protected String doInBackground(String... args) {
            String sResult = null, sPrinterID = args[0], sMacAddress = args[1];

            if (!sMacAddress.contains(":") && sMacAddress.length() == 12) {
                char[] addressChars = new char[17];
                for (int i = 0, j = 0; i < 12; i += 2) {
                    sMacAddress.getChars(i, i + 2, addressChars, j);
                    j += 2;
                    if (j < 17) {
                        addressChars[j++] = ':';
                    }
                }
                sMacAddress = new String(addressChars);
            }
            String sPrinterURI = "bt://" + sMacAddress;
            LinePrinter.ExtraSettings exSettings = new LinePrinter.ExtraSettings();
            exSettings.setContext(PrintActivity.this);

            PrintProgressListener progressListener =
                    new PrintProgressListener() {
                        @Override
                        public void receivedStatus(PrintProgressEvent aEvent) {
                            publishProgress(aEvent.getMessageType());// Publishes updates on the UI thread.
                        }
                    };

            try {
                mLinePrinter = new LinePrinter(mJsonCmdAttributeStr, sPrinterID, sPrinterURI, exSettings);
                mLinePrinter.addPrintProgressListener(progressListener); //registers to listen for the print progress events.

                //A retry sequence in case the bluetooth socket is temporarily not ready
                int triesNum = 0, maxRetry = 2;
                while (triesNum < maxRetry) {
                    try {
                        mLinePrinter.connect();  // Connects to the printer
                        break;
                    } catch (LinePrinterException ex) {
                        triesNum++;
                        Thread.sleep(1000);
                    }
                }
                if (triesNum == maxRetry) //Final retry
                    mLinePrinter.connect();

                // config arabic characters for Intermec printer(must if you print arabic)...
                byte[] arabicFont = new byte[]{0x1b, 0x77, 0x46};
                mLinePrinter.write(arabicFont);

                // Check the state of the printer and abort printing if there are
                // any critical errors detected.
                int[] results = mLinePrinter.getStatus();
                if (results != null) {
                    for (int result : results) {
                        if (result == 223) {
                            // Paper out.
                            throw new Exception("Paper out");
                        } else if (result == 227) {
                            // Lid open.
                            throw new Exception("Printer lid open");
                        }
                    }
                }
                intermecPrint();
                sResult = "Number of bytes sent to printer: " + mLinePrinter.getBytesWritten();
            } catch (Exception ex) {
                if (mLinePrinter != null)
                    mLinePrinter.removePrintProgressListener(progressListener);// Stop listening for printer events.
                sResult = "Unexpected exception: " + ex.getMessage();
            } finally {
                try {
                    if (mLinePrinter != null)
                        mLinePrinter.disconnect();
                } catch (PrinterException e) {
                    e.printStackTrace();
                }
            }
            return sResult;// The result string will be passed to the onPostExecute method
        }

        /**
         * Runs on the UI thread after publishProgress is invoked. The
         * specified values are the values passed to publishProgress.
         */
        @Override
        protected void onProgressUpdate(Integer... values) {
            // Access the values array.
            int progress = values[0];

            switch (progress) {
                case PrintProgressEvent.MessageTypes.CANCEL:
                    mTextMessageTV.append(PROGRESS_CANCEL_MSG);
                    break;
                case PrintProgressEvent.MessageTypes.COMPLETE:
                    mTextMessageTV.append(PROGRESS_COMPLETE_MSG);
                    break;
                case PrintProgressEvent.MessageTypes.ENDDOC:
                    mTextMessageTV.append(PROGRESS_END_DOC_MSG);
                    break;
                case PrintProgressEvent.MessageTypes.FINISHED:
                    mTextMessageTV.append(PROGRESS_FINISHED_MSG);
                    break;
                case PrintProgressEvent.MessageTypes.STARTDOC:
                    mTextMessageTV.append(PROGRESS_START_DOC_MSG);
                    break;
                default:
                    mTextMessageTV.append(PROGRESS_NONE_MSG);
                    break;
            }
        }

        /**
         * Runs on the UI thread after doInBackground method. The specified
         * result parameter is the value returned by doInBackground.
         */
        @Override
        protected void onPostExecute(String result) {
            // Displays the result (number of bytes sent to the printer or
            // exception message) in the Progress and Status text box.
            if (result != null) {
                mTextMessageTV.append(result);
            }

            // Dismisses the progress icon on the title bar.
            setProgressBarIndeterminateVisibility(false);

            // Enables the Print button.
            mPrintBtn.setEnabled(true);
        }
    } //end of class PrintTask

    /**
     * Print text to intermec printer....
     */
    private void intermecPrint() {
        try {
            mLinePrinter.write("WELCOME TO INTERMEC");
            mLinePrinter.newLine(1);

            // print arabic text from intermec pr3...
            Arabic864 arabic864 = new Arabic864();
            byte[] arabicTXT = arabic864.Convert("بسم الله الرحمن الرحيم", false);
            mLinePrinter.write(arabicTXT);
            mLinePrinter.newLine(1);

            mLinePrinter.writeLine("Intermec Printer Test");

            mLinePrinter.write("Print to intermec Pr3 [1]");
            mLinePrinter.newLine(1);

            mLinePrinter.write("Print to intermec Pr3 [2]");
            mLinePrinter.newLine(1);

            mLinePrinter.write("Print to intermec Pr3 [3]");
            mLinePrinter.newLine(1);

            mLinePrinter.write("Print to intermec Pr3 [4]");
            mLinePrinter.newLine(1);

            mLinePrinter.write("Print to intermec Pr3 [5]");
            mLinePrinter.newLine(1);

            mLinePrinter.writeLine("Finally, Good Bye.");
            mLinePrinter.newLine(1);

        } catch (Exception ex) {
            Log.v(TAG, ex.getMessage());
        }
    }

    private class BadPrinterStateException extends Exception {
        static final long serialVersionUID = 1;

        BadPrinterStateException(String message) {
            super(message);
        }
    }

}
