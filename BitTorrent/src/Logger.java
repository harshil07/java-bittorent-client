/*
 *  RUBTClient is a BitTorrent client written at Rutgers University for 
 *  instructional use.
 *  Copyright (C) 2008  Robert S. Moore II
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

import java.io.*;
import java.text.*;
import java.util.*;

/**
 * A semi-useful logging class.  Has 4 levels of output: errors, warnings,
 * information, debug.  Each level also includes the levels above it, so 
 * <code>LVL_INFO</code> will also include messages printed at level
 * <code>LVL_ERROR</code> and <code>LVL_WARNING</code>.
 * 
 * @author Robert S. Moore II
 *
 */
public class Logger
{
    /**
     * Log level for critical errors.
     */
    public static final int LVL_ERROR = 0;

    /**
     * Log level for non-critical warnings.
     */
    public static final int LVL_WARNING = 1;

    /**
     * Log level for informational messages useful for debugging.
     */
    public static final int LVL_INFO = 2;

    /**
     * Log level for debug messages. This generates a lot of output.
     */
    public static final int LVL_DEBUG = 3;

    /**
     * Strings that preface log messages.
     */
    public static final String[] DEBUG_PROMPTS =
    { "*ERR: ", " WRN: ", " INF: ", " DBG: " };

    /**
     * Where to write the log messages.
     */
    private final PrintWriter log;

    /**
     * The current log level.
     */
    private final int debug_level;

    /**
     * Date formatting for log messages.
     */
    private final DateFormat df = SimpleDateFormat.getDateTimeInstance();
    private RUBTClient rubt;
    
    /**
     * Creates a new Logger with the specified output and debug level.
     * @param log where to write the log messages.
     * @param debug_level the level of verbosity desired.
     * @throws Exception if <code>debug_level</code> is an invalid value.
     */
    public Logger(RUBTClient rubt, PrintWriter log, int debug_level) throws Exception
    {
    	this.rubt = rubt;
        if (debug_level > LVL_DEBUG || debug_level < LVL_ERROR)
            throw new Exception("Not a valid verbosity level: " + debug_level);
        this.debug_level = debug_level;
        this.log = log;
    }

    /**
     * Method for printing error messages.
     * @param s the error message to print.
     * @return this Logger object for chaining.
     */
    public synchronized Logger error(String s)
    {
        if (debug_level >= LVL_ERROR)
        {
            log.println("[" + df.format(new Date(System.currentTimeMillis()))
                    + "]" + DEBUG_PROMPTS[LVL_ERROR] + s);
            if(rubt!=null && rubt.gui!=null)
            rubt.gui.log_panel.addLogEntry(df.format(new Date(System.currentTimeMillis())), s);
            log.flush();
        }

        return this;
    }

    /**
     * Method for printing warning messages.
     * @param s the warning message to print.
     * @return this Logger object for chaining.
     */
    public synchronized Logger warning(String s)
    {

        if (debug_level >= LVL_WARNING)
        {
            log.println("[" + df.format(new Date(System.currentTimeMillis()))
                    + "]" + DEBUG_PROMPTS[LVL_WARNING] + s);
            if(rubt!=null && rubt.gui!=null)
            rubt.gui.log_panel.addLogEntry(df.format(new Date(System.currentTimeMillis())), s);
            log.flush();
        }
        return this;
    }

    /**
     * Method for printing informational messages.
     * @param s the informational message to print
     * @return this Logger object for chaining.
     */
    public synchronized Logger info(String s)
    {
        if (debug_level >= LVL_INFO)
        {
            log.println("[" + df.format(new Date(System.currentTimeMillis()))
                    + "]" + DEBUG_PROMPTS[LVL_INFO] + s);
            if(rubt!=null && rubt.gui!=null)
            rubt.gui.log_panel.addLogEntry(df.format(new Date(System.currentTimeMillis())), s);
            log.flush();
        }
        return this;
    }

    /**
     * Method for printing debug messages.
     * @param s the debug message to print.
     * @return this Logger object for chaining.
     */
    public synchronized Logger debug(String s)
    {
        if (debug_level >= LVL_DEBUG)
        {
            log.println("[" + df.format(new Date(System.currentTimeMillis()))
                    + "]" + DEBUG_PROMPTS[LVL_DEBUG] + s);
            if(rubt!=null && rubt.gui!=null)
            rubt.gui.log_panel.addLogEntry(df.format(new Date(System.currentTimeMillis())), s);
            log.flush();
        }
        return this;
    }

    /**
     * Just a test method.  This shouldn't be used for any real purposes.
     * @param args Ignored for now.
     * @throws Throwable as a safeguard.
     */
    public static void main(String[] args) throws Throwable
    {
        Logger log = new Logger(null,new PrintWriter(System.out), LVL_ERROR);
        System.out.println("Testing ERROR mode:");
        log.error("This is an error message").warning(
                "This is a warning message").info("This is an info message")
                .debug("This is a debug message");
        System.out.println();

        log = new Logger(null,new PrintWriter(System.out), LVL_WARNING);
        System.out.println("Testing WARNING mode: ");
        log.error("This is an error message").warning(
                "This is a warning message").info("This is an info message")
                .debug("This is a debug message");
        System.out.println();

        log = new Logger(null,new PrintWriter(System.out), LVL_INFO);
        System.out.println("Testing INFO mode: ");
        log.error("This is an error message").warning(
                "This is a warning message").info("This is an info message")
                .debug("This is a debug message");
        System.out.println();

        log = new Logger(null,new PrintWriter(System.out), LVL_DEBUG);
        System.out.println("Testing DEBUG mode: ");
        log.error("This is an error message").warning(
                "This is a warning message").info("This is an info message")
                .debug("This is a debug message");
        System.out.println();
    }
}