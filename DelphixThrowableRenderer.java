/**
 * Copyright (c) 2012 by Delphix.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * The views and conclusions contained in the software and documentation are those
 * of the authors and should not be interpreted as representing official policies,
 * either expressed or implied, of the FreeBSD Project.
 */

package com.delphix.appliance.logger;

import org.apache.log4j.spi.ThrowableRenderer;

import java.io.*;
import java.util.*;

/**
 * The DelphixThrowableRenderer prunes out extraneous stack frames from the stack traces that are logged. The objective
 * here is to make the logged stack traces terser by only printing the frames that are useful for debugging. The list of
 * allowed keywords can be tailored to fit your needs. Pruning can be disabled by adding a .properties
 * file (e.g. logging.properties) in resources/ and setting the property "stacktrace.pruning.enabled" to false.
 *
 * In order to use this class, you'll need to specify the custom throwable renderer in your log4j.xml file. Example:
 *
 * 	<throwableRenderer class="com.delphix.appliance.logger.DelphixThrowableRenderer"/>
 *
 * This implementation requires log4j version 1.2.16 or newer.
 */
public class DelphixThrowableRenderer implements ThrowableRenderer {

    private static final String LOGGING_PROPERTIES = "logging";
    private static final String STACKTRACE_PRUNING_DISABLED = "stacktrace.pruning.enabled";
    private static final String ELLIPSES = "\t...";
    private static final String TAB = "\t";

    private static final String DELPHIX = "com.delphix";
    private static final String PROXY = "$Proxy";

    /*
     * Only the stack trace elements beginning with one of the following keywords are printed.
     */
    private static final List<String> allowedKeywords = Collections.unmodifiableList(Arrays.asList(DELPHIX, PROXY));

    @Override
    public String[] doRender(Throwable throwable) {

        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);

        throwable.printStackTrace(printWriter);
        printWriter.flush();

        BufferedReader reader = new BufferedReader(new StringReader(stringWriter.toString()));
        LinkedList<String> lines = new LinkedList<String>();

        try {
	    /*
	     * The preserveFrames flag ensures that we don't start pruning until we've seen the first occurence of
	     * one of the allowed keywords. This allows us to have access to the third party stack frames that lead up
	     * to our code.
	     */
            boolean preserveFrames = true;
            boolean printThisLine = false;
            int framesOmitted = 0;

            String line;
            if (isPruningDisabled()) {
                while ((line = reader.readLine()) != null)
                    lines.add(line);

                return lines.toArray(new String[0]);
            }

            while ((line = reader.readLine()) != null) {
                if (!line.startsWith(TAB)) {
                    preserveFrames = true;
                    printThisLine = true;
                } else {
                    printThisLine = false;

                    for (String keyword : allowedKeywords) {
                        if (line.contains(keyword)) {
                            preserveFrames = false;
                            printThisLine = true;
                            break;
                        }
                    }
                }

                if (printThisLine || preserveFrames) {
                    lines.add(line);
                    framesOmitted = 0;
                } else {
                    framesOmitted++;

                    if (lines.getLast().contains(ELLIPSES))
                        lines.removeLast();

                    lines.add(String.format("%s %d more", ELLIPSES, framesOmitted));
                }
            }
        } catch (IOException ex) {
            if (ex instanceof InterruptedIOException)
                Thread.currentThread().interrupt();

            lines.add(ex.toString());
        }

        return lines.toArray(new String[0]);
    }

    private boolean isPruningDisabled() {

        ResourceBundle bundle = null;
        try {
            bundle = ResourceBundle.getBundle(LOGGING_PROPERTIES);
        } catch (MissingResourceException e) {
            return false;
        }
        return !bundle.getString(STACKTRACE_PRUNING_DISABLED).equals("true");
    }
}
