
// You can use this file as a starting point for your dictionary client
// The file contains the code for command line parsing and it also
// illustrates how to read and partially parse the input typed by the user. 
// Although your main class has to be in this file, there is no requirement that you
// use this template or hav all or your classes in this file.

import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.System;
import java.io.IOException;
import java.net.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.io.BufferedReader;

//
// This is an implementation of a simplified version of a command
// line dictionary client. The only argument the program takes is
// -d which turns on debugging output. 
//


public class CSdict {
    static final int MAX_LEN = 255;
    static Boolean debugOn = false;
    static Boolean openedConnect = false;
    static boolean notClosed = true;
    static String dictionary;

    private static final int PERMITTED_ARGUMENT_COUNT = 1;
    private static String command;
    private static String[] arguments;
    private static final int[] numArgs = new int[]{2, 0, 1, 1, 1, 1, 0, 0};

    static ArrayList<String> commands = new ArrayList<>();

    static Socket echoSocket;
    static PrintWriter out;
    static BufferedReader in;
    
    public static void main(String [] args) {
        commandsInit(commands);
        byte[] cmdString = new byte[MAX_LEN];
	    int len;
	    // Verify command line arguments

        if (args.length == PERMITTED_ARGUMENT_COUNT) {
            debugOn = args[0].equals("-d");
            if (! debugOn) {
                System.out.println("997 Invalid command line option - Only -d is allowed.");
                return;
            }
        } else if (args.length > PERMITTED_ARGUMENT_COUNT) {
            System.out.println("996 Too many command line options - Only -d is allowed.");
            return;
        }
	
        try {
            // loop to keep check for user input
            while (notClosed) {
                // refresh command input each cycle
                Arrays.fill(cmdString, (byte) 0);
                int command_num;
                int command_args;
                System.out.print("csdict> ");
                System.in.read(cmdString);

                // Convert the command string to ASII
                String inputString = new String(cmdString, "ASCII");

                // Split the string into words
                String[] inputs = inputString.trim().split("( |\t)+");
                // Set the command
                command = inputs[0].toLowerCase().trim();
                // Remainder of the inputs is the arguments.
                arguments = Arrays.copyOfRange(inputs, 1, inputs.length);
                // check valid command
                if (commands.contains(command)) {
                    command_num = commands.indexOf(command);
                    command_args = numArgs[command_num];
                } else {
                    System.out.println("900 Invalid command.");
                    continue;
                }
                // check if command is valid at the time
                if ((openedConnect ^ command_num == 0) || command_num == 7) {
                    len = arguments.length;
                    // check number of arguments
                    if (!(len == command_args || (command_num == 2 && len == 0))) {
                        System.out.println("901 Incorrect number of arguments.");
                        continue;
                    }
                    // check if arguments are valid
                    if (! validArgs(command_num, arguments)) {
                        System.out.println("902 Invalid argument.");
                        continue;
                    }
                // perform the actions of commands
                performActions(command_num, arguments);
                } else {
                    System.out.println("903 Supplied command not expected at this time.");
                }
            }
    	}
        catch (IOException exception) {
	        System.out.println("998 Input error while reading commands, terminating.");
            System.exit(-1);
	    }
    }

    // method for performing actions of different commands
    private static void performActions(int command_num, String[] arguments) throws IOException {
        try {
            // perform open command
            if (command_num == 0) {
                SocketAddress socketAddress = new InetSocketAddress(arguments[0], Integer.parseInt(arguments[1]));
                echoSocket = new Socket();
                // attempt to connect
                try {
                    echoSocket.connect(socketAddress, 30000);
                } catch (IOException e) {
                    System.out.println("920 Control connection to " + arguments[0] + " on port " + arguments[1] + " failed to open.");
                    return;
                }
                echoSocket.setSoTimeout(30000);
                out = new PrintWriter(echoSocket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(echoSocket.getInputStream()));
                openedConnect = true;
                String connection = in.readLine();
                if (debugOn) {
                    System.out.println("<-- " + connection);
                }
                dictionary = "*";
            }
            // perform dict command
            else if (command_num == 1) {
                out.println("SHOW DB");
                out.flush();
                String line = in.readLine();
                if (debugOn) {
                    System.out.println("> SHOW DB");
                    System.out.println("<--" + line);
                }
                // check if present database
                if (line.startsWith("554")) {
                    System.out.println("999 Processing error. No databases present");
                    return;
                }
                line = in.readLine();
                // loop to read and print all databases
                while (!line.equals("250 ok")) {
                    System.out.println(line);
                    line = in.readLine();
                }
                if (debugOn) {
                    System.out.println("<-- " + line);
                }
            }
            // perform set command
            else if (command_num == 2) {
                // set desired dictionary or default if no argument
                if (arguments.length == 1) {
                    dictionary = arguments[0];
                } else {
                    dictionary = "*";
                }
            }
            // perform define word command
            else if (command_num == 3) {
                out.println("DEFINE " + dictionary + " " + arguments[0]);
                String line = in.readLine();
                if (debugOn) {
                    System.out.println("> DEFINE " + dictionary + " " + arguments[0]);
                    System.out.println("<-- " + line);
                }
                // check if dictionary is valid
                if (line.startsWith("550")) {
                    System.out.println("999 Processing error. Invalid database");
                    return;
                }
                // check if definition found
                if (line.startsWith("552")) {
                    System.out.println("****No definition found****");
                    out.println("MATCH " + dictionary + " . " + arguments[0]);
                    if (debugOn) {
                        System.out.println("> MATCH " + dictionary + " . " + arguments[0]);
                    }
                    line = in.readLine();
                    // check if matches found
                    if (line.startsWith("552")) {
                        System.out.println("****No matches found****");
                        if (debugOn) {
                            System.out.println("<-- " + line);
                        }
                        return;
                    }
                    if (debugOn) {
                        System.out.println("<-- " + line);
                    }
                    // if not returned then start looping
                }
                line = in.readLine();
                // loop for printing define/match
                while (!line.startsWith("250 ok")) {
                    if (line.startsWith("150") && debugOn) {
                        System.out.println("<-- " + line);
                        line = in.readLine();
                        continue;
                    }
                    if (line.startsWith("151")) {
                        if (debugOn) {
                            System.out.println("<-- " + line);
                        }
                        System.out.println("@ " + line.substring(7 + arguments[0].length()));
                        line = in.readLine();
                        continue;
                    }
                    System.out.println(line.trim());
                    line = in.readLine();
                }
                if (debugOn) {
                    System.out.println("<-- " + line);
                }
            }
            // perform match word command
            else if (command_num == 4) {
                out.println("MATCH " + dictionary + " exact " + arguments[0]);
                if (debugOn) {
                    System.out.println("> MATCH " + dictionary + " exact " + arguments[0]);
                }
                makeMatch();
            }
            // perform prefix match word command
            else if (command_num == 5) {
                out.println("MATCH " + dictionary + " prefix " + arguments[0]);
                if (debugOn) {
                    System.out.println("> MATCH " + dictionary + " prefix " + arguments[0]);
                }
                makeMatch();
            }
            // perform close command
            else if (command_num == 6) {
                out.println("QUIT");
                if (debugOn) {
                    System.out.println("> QUIT");
                }
                out.flush();
                if (debugOn) {
                    System.out.println("<-- " + in.readLine());
                }
                echoSocket.close();
                openedConnect = false;
                dictionary = "*";
            }
            // perform quit command
            else {
                if (!openedConnect) {
                    System.exit(0);
                }
                out.println("QUIT");
                if (debugOn) {
                    System.out.println("> QUIT");
                }
                out.flush();
                if (debugOn) {
                    System.out.println("<-- " + in.readLine());
                }
                openedConnect = false;
                notClosed = false;
            }
        } catch (SocketTimeoutException e) {
            System.out.println("999 Processing error. Timed out while waiting for a response.");
            openedConnect = false;
            dictionary = "*";
        } catch (IOException e) {
            System.out.println("925 Control connection I/O error, closing control connection.");
            echoSocket.close();
            openedConnect = false;
            dictionary = "*";
        }

    }

    // function to perform match command
    private static void makeMatch() throws IOException {
        String line = in.readLine();
        if (debugOn) {
            System.out.println("<-- " + line);
        }
        if (line.startsWith("550")) {
            System.out.println("999 Processing error. Invalid database");
            return;
        }
        if (line.startsWith("552")) {
            System.out.println("****No matching word(s) found****");
            return;
        }
        line = in.readLine();
        while(! line.startsWith("250 ok")) {
            System.out.println(line);
            line = in.readLine();
        }
        if (debugOn) {
            System.out.println("<-- " + line);
        }
    }

    // function to check if arguments are valid
    private static boolean validArgs(int command_num, String[] arguments) {
        if (command_num == 0) {
            try {
                Integer.parseInt(arguments[1]);
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return true;
    }

    // add all valid commands to ArrayList
    private static void commandsInit(ArrayList<String> commands) {
        commands.add("open");
        commands.add("dict");
        commands.add("set");
        commands.add("define");
        commands.add("match");
        commands.add("prefixmatch");
        commands.add("close");
        commands.add("quit");
    }
}
    
    
