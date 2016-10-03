
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.lang.System;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;

//
// This is an implementation of a simplified version of a command 
// line ftp client. The program always takes two arguments
//


public class CSftp
{
    static final int MAX_LEN = 255;
    static final int ARG_CNT = 2;

    static Socket control;

    public static void main(String [] args) {
        byte cmdString[] = new byte[MAX_LEN];



        // Get command line arguments and connected to FTP
        // If the arguments are invalid or there aren't enough of them
            // then exit.

        if (args.length != ARG_CNT) {
            System.out.print("Usage: cmd ServerAddress ServerPort\n");
            return;
        }

        // initialize control connection
        try {
            control = new Socket();
            control.connect(new InetSocketAddress(args[0], Integer.parseInt(args[1])), 30000);
        } catch (IOException e) {
            printError(920);
        }

        try {
            for (int len = 1; len > 0;) {
                System.out.print("csftp> ");
                len = System.in.read(cmdString);
                if (len <= 0)
                    break;
                // Start processing the command here.

                System.out.println();
                String[] command = new String(cmdString, "ASCII").trim().split("\\s+");

                switch (command[0].toLowerCase()) {
                    case "user":
                        sendString("USER anonymous");
                        break;
                    case "pw":
                        break;
                    case "quit":
                        break;
                    case "get":
                        break;
                    case "cd":
                        break;
                    case "dir":
                        break;
                    default:
                        printError(900);
                        break;
                }
            }
        } catch (IOException exception) {
            printError(998);
        }
    }

    private static void sendString(String str) {
        try {
            DataOutputStream out = new DataOutputStream(control.getOutputStream());
            BufferedReader in = new BufferedReader(new InputStreamReader(control.getInputStream()));


            out.writeBytes(str + "\n");
            System.out.println(in.readLine());
        } catch (IOException e) {
            printError(920, control.getInetAddress().toString(), control.getPort());
        }
    }

    private static void printError(int code) {
        switch (code) {
            case 900:
                System.out.println("900 Invalid command.");
                break;
            case 901:
                System.out.println("901 Incorrect number of arguments.");
                break;
            case 910:
                break;
            case 925:
                System.out.println("925 Control connection I/O error, closing control connection.");
                break;
            case 935:
                System.out.println("935 Data transfer connection I/O error, closing data connection.");
                break;
            case 998:
                System.err.println("998 Input error while reading commands, terminating.");
                break;
            case 999:
                break;
        }
    }

    private static void printError(int code, String hostname, int port) {
        switch (code) {
            case 920:
                System.out.println(String.format("920 Control connection to %s on port %d failed to open", hostname, port));
                break;
            case 930:
                System.out.println(String.format("930 Data transfer connection to %s on port %d failed to open.", hostname, port));
                break;
        }
    }
}
