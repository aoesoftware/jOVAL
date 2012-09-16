// Copyright (C) 2012 jOVAL.org.  All rights reserved.
// This software is licensed under the AGPL 3.0 license available at http://www.joval.org/agpl_v3.txt

package org.joval.os.windows.remote.powershell;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;

import org.joval.intf.system.IProcess;
import org.joval.os.windows.powershell.PowershellException;
import org.joval.util.StringTools;

/**
 * A Runspace implementation with a flush-optimized implementation of loadModule, for use with MS-WSMV.
 *
 * @author David A. Solin
 * @version %I% %G%
 */
class Runspace extends org.joval.os.windows.powershell.Runspace {
    private long timestamp;

    /**
     * Create a new Runspace, based on a process.
     */
    Runspace(String id, IProcess p) throws Exception {
	super(id, p);
    }

    // Implement IRunspace

    /**
     * For efficient use of WS-Transfer/Send, this method transmits the entire module in one envelope, then loops
     * through the resulting string of prompts.
     */
    @Override
    public synchronized void loadModule(InputStream in) throws IOException, PowershellException {
	try {
	    StringBuffer buffer = new StringBuffer();
	    String line = null;
	    int lines = 0;
	    BufferedReader reader = new BufferedReader(new InputStreamReader(in, StringTools.ASCII));
	    while((line = reader.readLine()) != null) {
		stdin.write(line.getBytes());
		stdin.write("\r\n".getBytes());
		lines++;
	    }
	    stdin.flush();
	    for (int i=0; i < lines; i++) {
		readLineInternal();
	    }
	    if (">> ".equals(getPrompt())) {
		invoke("");
	    }
	    timestamp = System.currentTimeMillis();
	} finally {
	    if (in != null) {
		try {
		    in.close();
		} catch (IOException e) {
		}
	    }
	}
	if (hasError()) {
	    throw new IOException(getError());
	}
    }

    @Override
    public synchronized String invoke(String command) throws IOException, PowershellException {
	String result = super.invoke(command);
	timestamp = System.currentTimeMillis();
	return result;
    }

    // Internal

    /**
     * Returns the last time this Runspace was used to perform an operation.  Used for keep-alive.
     */
    long lastOperation() {
	return timestamp;
    }

    // Private

    /**
     * Same as super.readLine(), except insensitive to stdout.available().  This makes it usable with input that
     * has built-up from a multi-line WS-Transfer/Send operation continaing input to the process.
     */
    private synchronized String readLineInternal() throws IOException {
	StringBuffer sb = new StringBuffer();
	boolean cr = false;
	int ch = -1;
	while((ch = stdout.read()) != -1) {
	    switch(ch) {
	      case '\r':
		cr = true;
		if (stdout.markSupported() && stdout.available() > 0) {
		    stdout.mark(1);
		    switch(stdout.read()) {
		      case '\n':
			return sb.toString();
		      default:
			stdout.reset();
			break;
		    }
		}
		break;

	      case '\n':
		return sb.toString();

	      default:
		if (cr) {
		    cr = false;
		    sb.append((char)('\r' & 0xFF));
		}
		sb.append((char)(ch & 0xFF));
	    }
	    if (isPrompt(sb.toString())) {
		    prompt = sb.toString();
		    return null;
	    }
	}
	return null;
    }
}
