
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.OutputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.System;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

//
// This is an implementation of a simplified version of a command 
// line ftp client. The program always takes two arguments
//


public class CSftp
{
    static final int MAX_LEN = 255;
    static final int ARG_CNT = 2;

    static Socket control;
    static DataOutputStream control_out;
    static BufferedReader control_in;

    static Socket data;
    static DataOutputStream data_out;
    static BufferedReader data_in;

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

            control_out = new DataOutputStream(control.getOutputStream());
            control_in = new BufferedReader(new InputStreamReader(control.getInputStream()));

            ctrlNext();

        } catch (IOException e) {
            System.out.println("error");
            printError(920, args[0], args[1]);
        }

        try {
            for (int len = 1; len > 0;) {
                System.out.print("csftp> ");
                len = System.in.read(cmdString);
                if (len <= 0)
                    break;
                // Start processing the command here.

                String[] command = new String(cmdString, "ASCII").trim().split("#")[0].trim().split("\\s+");
                cmdString = new byte[MAX_LEN];
                switch (command[0].toLowerCase()) {
                    case "user":
                        if (command.length != 2) {
                            printError(901);
                            break;
                        }
                        user(command[1]);
                        break;
                    case "pw":
                        if (command.length != 2) {
                            printError(901);
                            break;
                        }
                        pw(command[1]);
                        break;
                    case "quit":
                        if (command.length != 1) {
                            printError(901);
                            break;
                        }
                        quit();
                        break;
                    case "get":
                        if (command.length != 2) {
                            printError(901);
                            break;
                        }
                        get(command[1]);
                        break;
                    case "cd":
                        if (command.length != 2) {
                            printError(901);
                            break;
                        }
                        cd(command[1]);
                        break;
                    case "dir":
                        if (command.length != 1) {
                            printError(901);
                            break;
                        }
                        dir();
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

    private static void user(String user) {
        sendString(String.format("USER %s", user));

        Response resp = ctrlNext();
        switch (resp.code) {
            case 230:
                break;
            case 331:
            case 332:
                break;
            case 530:
                break;
        }
    }

    private static void pw(String pw) {
        sendString(String.format("PASS %s", pw));

        Response resp = ctrlNext();
        switch (resp.code) {
            case 230:
                break;
            case 202:
                break;
            case 332:
                break;
            case 503:
                break;
            case 530:
                break;
        }
    }

    private static void quit() {
        sendString("QUIT");
        ctrlNext();
        System.exit(0);
    }

    private static void get(String filename) {
        if (pasv()) {
            sendString("TYPE I");
            Response typeResp = ctrlNext();

            sendString(String.format("RETR %s", filename));
            Response resp = ctrlNext();    

            OutputStream fileOut;
            try {
                fileOut = new FileOutputStream(filename);
            } catch (IOException e) {
                printError(910, filename);
                return;
            }

            byte[] bytes = new byte[16*1024];

            int count;
            try {
                while ((count = data.getInputStream().read(bytes)) > 0) {
                    fileOut.write(bytes, 0, count);
                }

                fileOut.close();
            } catch (IOException e) {
                printError(910, filename);
                return;
            }


            ctrlNext();
        } else {
            // TODO: handle not conencted case
        }
    }

    private static void cd(String dir) {
        sendString(String.format("CWD %s", dir));

        Response resp = ctrlNext();
        switch(resp.code) {
            case 200:
            case 250:
                break;
            case 550:
                break;
        }
    }

    private static void dir() {
        if (pasv()) {
            sendString("LIST");
            Response listResp = ctrlNext();

            dataNext();

            Response afterList = ctrlNext();
        } else {
            // TODO: handle not connected
        }
    }

    // returns true if data connection is established
    // false otherwise
    private static boolean pasv() {
        sendString("PASV");

        Response resp = ctrlNext();
        switch (resp.code) {
            case 227:
                Matcher matcher = Pattern.compile("[(](.*?)[)]").matcher(resp.message);
                // TODO: fix
                String[] ip_port = {"0", "0", "0", "0", "0", "1"};
                if (matcher.find())
                {
                    ip_port = matcher.group(1).split(",");
                }

                String hostname = ip_port[0] + "." + ip_port[1] + "." + ip_port[2] + "." + ip_port[3];

                int port = Integer.parseInt(ip_port[4]) * 256 + Integer.parseInt(ip_port[5]);

                data = new Socket();

                try {
                    data.connect(new InetSocketAddress(hostname, port), 30000);
                    data_out = new DataOutputStream(data.getOutputStream());
                    data_in = new BufferedReader(new InputStreamReader(data.getInputStream()));
                    return true;
                } catch (IOException e) {
                    // TODO: fix this
                    printError(930, hostname, Integer.toString(port));
                    return false;
                }
        }
        return false;
    }

    private static void sendString(String str) {
        try {
            printOut(str);
            control_out.writeBytes(str + "\r\n");
        } catch (IOException e) {
            printError(925);
            try {
                control.close();    
            } catch (IOException closeException) {
                printError(999, "Failed to close socket");
            }
            System.exit(1);
        }
    }

    private static Response ctrlNext() {
        try {
            // TODO: fix this
            Response next = parseResponse(control_in.readLine());
            printIn(next.toString());
            return next;
        } catch (IOException e) {
            printError(925);
            try {
                control.close();    
            } catch (IOException closeException) {
                printError(999, "Failed to close socket");
            }
            System.exit(1);
            return null;
        }
    }

    private static String dataNext() {
        try {
            // TODO: fix this
            String next;

            while ((next = data_in.readLine()) != null) {
                System.out.println(next);
            }
            return next;
        } catch (IOException e) {
            printError(935);
            try {
                data.close();    
            } catch (IOException closeException) {
                printError(999, "Failed to close socket");
            }
            System.exit(1);
            return null;
        }
    }


    private static Response parseResponse(String str) {
        String[] resp = str.split(" ", 2);
        return new Response(Integer.parseInt(resp[0]), resp[1]);            
    }

    private static void printIn(String str) {
        System.out.println(String.format("<-- %s", str));
    }

    private static void printOut(String str) {
        System.out.println(String.format("--> %s", str));
    }

    private static void printError(int code) {
        switch (code) {
            case 900:
                System.out.println("900 Invalid command.");
                break;
            case 901:
                System.out.println("901 Incorrect number of arguments.");
                break;
            case 925:
                System.out.println("925 Control connection I/O error, closing control connection.");
                break;
            case 935:
                System.out.println("935 Data transfer connection I/O error, closing data connection.");
                break;
            case 998:
                System.out.println("998 Input error while reading commands, terminating.");
                break;
        }
    }

    private static void printError(int code, String msg) {
        switch (code) {
            case 910:
                System.out.println(String.format("910 Access to local file %s denied.", msg));
                break;
            case 999:
                System.out.println(String.format("999 Processing error. %s.", msg));
                break;
        }
    }

    private static void printError(int code, String hostname, String port) {
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
