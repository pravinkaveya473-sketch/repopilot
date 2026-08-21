package com.cjl.repopilot;

import com.cjl.repopilot.cli.RepoPilotCli;

/**
 * Independent RepoPilot command-line entry point.
 */
public final class RepoPilotApplication {

    private RepoPilotApplication() {}

    public static void main(String[] args) {
        RepoPilotCli cli = new RepoPilotCli();
        int code = cli.execute(args == null ? new String[0] : args);
        if (code != 0) System.exit(code);
    }
}
