/*
 * Copyright (c) 2000 jPOS.org.  All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. The end-user documentation included with the redistribution,
 *    if any, must include the following acknowledgment:
 *    "This product includes software developed by the jPOS project 
 *    (http://www.jpos.org/)". Alternately, this acknowledgment may 
 *    appear in the software itself, if and wherever such third-party 
 *    acknowledgments normally appear.
 *
 * 4. The names "jPOS" and "jPOS.org" must not be used to endorse 
 *    or promote products derived from this software without prior 
 *    written permission. For written permission, please contact 
 *    license@jpos.org.
 *
 * 5. Products derived from this software may not be called "jPOS",
 *    nor may "jPOS" appear in their name, without prior written
 *    permission of the jPOS project.
 *
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.  
 * IN NO EVENT SHALL THE JPOS PROJECT OR ITS CONTRIBUTORS BE LIABLE FOR 
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL 
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS 
 * OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) 
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, 
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING 
 * IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE 
 * POSSIBILITY OF SUCH DAMAGE.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the jPOS Project.  For more
 * information please see <http://www.jpos.org/>.
 */

package org.jpos.util;

import java.io.IOException;

import org.jpos.iso.ISOUtil;

/**
 * Implements DialupModem
 *
 * @author <a href="mailto:apr@cs.com.uy">Alejandro P. Revilla</a>
 * @version $Revision$ $Date$
 */
public class SimpleDialupModem implements Modem {
    V24 v24;
    String dialPrefix = "DT";
    long lastInit = 0;
    public static final int REINIT_MODEM = 2*60*1000;

    public final String[] resultCodes = {
        "OK\r",
        "CONNECT\r",
        "RING\r",
        "NO CARRIER\r",
        "ERROR\r",
        "CONNECT",
        "NO DIALTONE\r",
        "BUSY\r",
        "NO ANSWER\r",
        "NO DIAL TONE\r",
    };
    
    public SimpleDialupModem (V24 v24) {
        super();
        this.v24 = v24;
    }
    public void setDialPrefix (String dialPrefix) {
        this.dialPrefix = dialPrefix;
    }
    private boolean checkAT() throws IOException {
        return v24.waitfor ("ATE1Q0V1H0\r", resultCodes, 10000) == 0;
    }
    private void reset() throws IOException {
        try {
            v24.send ("AT\r"); Thread.sleep (250);
            v24.flushAndLog ();
            if (v24.waitfor ("ATE1Q0V1H0\r", resultCodes, 1000) == 0) 
                return;
            v24.dtr (false);     Thread.sleep (1500);
            v24.dtr (true);      Thread.sleep (1500);
            v24.send ("+++"); Thread.sleep (1500);
        } catch (InterruptedException e) { 
        } 
        v24.flushAndLog();
        if (!checkAT())
            throw new IOException ("Unable to reset");
    }
    public void dial (String phoneNumber, long aproxTimeout) 
        throws IOException
    {
        int rc;
        long expire = System.currentTimeMillis() + aproxTimeout;

        while (System.currentTimeMillis() < expire) {
            try {
                reset();
                Thread.sleep (250);
                rc = v24.waitfor (
                    "ATB0M1X4L1"+dialPrefix+phoneNumber +"\r", 
                        resultCodes,45000
                );
                if ((rc == 1) || (rc == 5)) {
                    Thread.sleep (500); // CD debouncing/settlement time
                    break;
                }
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) { }
        }
    }
    public boolean isConnected() {
        return v24.isConnected();
    }
    public void initForAnswer () throws IOException {
        v24.setWatchCD (false);
        v24.setAutoFlushReceiver(false);
        if (!checkAT())
            throw new IOException ("unable to initialize modem (0)");
        v24.send ("ATB0E0Q1S0=1\r");
        v24.setAutoFlushReceiver(true);
        lastInit = System.currentTimeMillis();
    }
    public void answer () throws IOException {
        v24.dtr (true);
        if (!v24.isConnected()) 
            initForAnswer();
        while (!v24.isConnected()) {
            if ((System.currentTimeMillis() - lastInit ) > REINIT_MODEM)
                initForAnswer();
            try {
                Thread.sleep (1000);
            } catch (InterruptedException e) { }
        }
        v24.setWatchCD (true);
    }
    public void hangup () throws IOException {
        v24.dtr (false); ISOUtil.sleep (5000);
        v24.dtr (true);  ISOUtil.sleep (1500);
        if (v24.isConnected()) {
            v24.setWatchCD (false);
            reset ();
            ISOUtil.sleep (500);
        }
        if (v24.isConnected()) 
            throw new IOException ("Can't hangup");
    }
}
