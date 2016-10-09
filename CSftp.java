import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.OutputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.lang.System;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Arrays;
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

            Response resp = ctrlNext();

            switch (resp.code) {
                case 220: // Service ready for new user.
                    // all good!
                    break;
                case 120: // Service ready in nnn minutes.
                case 421: // Service not available, closing control connection.
                    handleError(920, args[0], args[1]);
                    break;
            }
        } catch (IOException e) {
            handleError(920, args[0], args[1]);
        }

        try {
            for (int len = 1; len > 0;) {
                System.out.print("csftp> ");
                len = System.in.read(cmdString);
                if (len <= 0)
                    break;
                // Start processing the command here.

                String[] command = new String(cmdString, "ASCII").trim().split("#")[0].trim().split("\\s+", 2);
                cmdString = new byte[MAX_LEN];
                switch (command[0].toLowerCase()) {
                    case "user":
                        if (command.length != 2) {
                            handleError(901);
                            break;
                        }
                        user(command[1]);
                        break;
                    case "pw":
                        if (command.length != 2) {
                            handleError(901);
                            break;
                        }
                        pw(command[1]);
                        break;
                    case "quit":
                        if (command.length != 1) {
                            handleError(901);
                            break;
                        }
                        quit();
                        break;
                    case "get":
                        if (command.length != 2) {
                            handleError(901);
                            break;
                        }
                        get(command[1]);
                        break;
                    case "cd":
                        if (command.length != 2) {
                            handleError(901);
                            break;
                        }
                        cd(command[1]);
                        break;
                    case "dir":
                        if (command.length != 1) {
                            handleError(901);
                            break;
                        }
                        dir();
                        break;
                    default:
                        handleError(900);
                        break;
                }
            }
        } catch (IOException exception) {
            handleError(998);
        }
    }

    // sends username.
    private static void user(String user) {
        sendString(String.format("USER %s", user));

        Response resp = ctrlNext();
        switch (resp.code) {
            case 230: // User logged in, proceed.
            case 530: // Not logged in.
            case 331: // User name okay, need password.
            case 332: // Need account for login.
                // OK to not do anything. User should send next command.
                break;
            default: // 500, 501, 421
                handleCommonResponse(resp.code);
                break;
        }
    }

    // sends password.
    // probably shouldn't use this with your actual password.
    private static void pw(String pw) {
        sendString(String.format("PASS %s", pw));

        Response resp = ctrlNext();
        switch (resp.code) {
            case 230: // User logged in, proceed.
            case 202: // Command not implemented, superfluous at this site.
            case 530: // Not logged in.
            case 332: // Need account for login.
                // OK to not do anything. User should send next command.
                break;
            default: // 500, 501, 503, 421
                handleCommonResponse(resp.code);
                break;
        }
    }

    // exits the program.
    private static void quit() {
        sendString("QUIT");
        ctrlNext();
        System.exit(0);
    }

    // set data type to binary
    private static boolean type() {
        sendString("TYPE I");
        Response resp = ctrlNext();
        switch (resp.code) {
            case 200: // Command okay.
                // all good!
                return true;
            default: // 500, 501, 504, 421, 530
                handleCommonResponse(resp.code);
                return false;
        }
    }

    // saves a file to local disk
    // will save the file to the exact same name as the remote
    // file. eg. remote.pdf will be saved to ./remote.pdf, ./dir/a.pdf will
    // similarly create a folder locally
    private static void get(String filename) {
        if (pasv()) {
            if (type()) {
                sendString(String.format("RETR %s", filename));
                Response resp = ctrlNext();

                switch (resp.code) {
                    case 125: // Data connection already open; transfer starting.
                    case 150: // File status okay; about to open data connection.
                        saveFile(filename);
                        Response afterDataResp = ctrlNext();
                        switch (afterDataResp.code) {
                            case 226: // Closing data connection. Requested file action successful (for example, file transfer or file abort).
                            case 250: // Requested file action okay, completed.
                                // all good!
                                break;
                            case 425: // Can't open data connection.
                            case 426: // Connection closed; transfer aborted.
                            case 451: // Requested action aborted. Local error in processing.
                                handleError(935);
                                break;
                        }
                        break;
                    case 450: // Requested file action not taken. File unavailable (e.g., file busy).
                    case 550: // Requested action not taken. File unavailable (e.g., file not found, no access).
                        handleError(935);
                        break;
                    default: // 500, 501, 421, 530
                        handleCommonResponse(resp.code);
                        break;
                }
            } else {
                // binary type set failed
                handleError(935);
            }
        }
    }

    // saves data on existing data socket to filename
    private static void saveFile(String filename) {
        OutputStream fileOut;
        try {
            fileOut = new FileOutputStream(filename);
        } catch (IOException e) {
            handleError(910, filename);
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
            handleError(910, filename);
            return;
        }
    }


    // change directory
    private static void cd(String dir) {
        sendString(String.format("CWD %s", dir));

        Response resp = ctrlNext();
        switch(resp.code) {
            case 200: // Command okay.
            case 250: // Requested file action okay, completed.
                // all good!
                break;
            default: // 500, 501, 502, 421, 530, 550
                handleCommonResponse(resp.code);
                break;
        }
    }

    // get directory listing
    private static void dir() {
        if (pasv()) {
            sendString("LIST");
            Response listResp = ctrlNext();

            switch (listResp.code) {
                case 125: // Data connection already open; Transfer starting.
                case 150: // File status okay; about to open data connection
                    // proceed to printing data
                    dataPrint();

                    Response afterList = ctrlNext();
                    switch (afterList.code) {
                        case 226: // Closing data connection. Requested file action successful (for example, file transfer or file abort).
                        case 250: // Requested file action okay, completed.
                            // all good!
                            break;
                        case 425: // Can't open data connection.
                        case 426: // Connection closed; transfer aborted.
                        case 451: // Requested action aborted. Local error in processing.
                            handleError(935);
                            break;
                    }

                    break;
                case 450: // Requested file action not taken. File unavailable (e.g., file busy).
                    handleError(935);
                    break;
                default: // 500, 501, 502, 421, 530
                    handleCommonResponse(listResp.code);
                    break;
            }


        } else {
            // TODO: handle not connected
        }
    }

    // enter passive mode and establish data connection.
    // returns true if data connection is established
    // false otherwise
    private static boolean pasv() {
        sendString("PASV");

        Response resp = ctrlNext();
        switch (resp.code) {
            case 227: // Entering Passive Mode (h1,h2,h3,h4,p1,p2).
                Matcher matcher = Pattern.compile("[(](.*?)[)]").matcher(resp.message);
                String[] ip_port;
                if (matcher.find())
                {
                    ip_port = matcher.group(1).split(",");
                } else {
                    handleError(999, "Could not detect IP address and port for data connection.");
                    return false;
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
                    handleError(930, hostname, Integer.toString(port));
                    return false;
                }

            default: // 500, 501, 502, 421, 530
                handleCommonResponse(resp.code);
                break;
        }
        handleError(930);
        return false;
    }

    // sends a string over the control socket.
    private static void sendString(String str) {
        try {
            printOut(str);
            control_out.writeBytes(str + "\r\n");
        } catch (IOException e) {
            handleError(925);
        }
    }

    // get next valid response from control socket.
    // Will also print out the response, including a multiline response.
    private static Response ctrlNext() {
        try {
            String s = control_in.readLine();

            // if a "-" follows the code, it is multiline response
            // The response ends when the same status code appears,
            // with s apace after it.
            if (s.substring(3, 4).equals("-")) {
                printIn(s);

                String code = s.substring(0,3);
                s = control_in.readLine();
                // keep printing lines until end of multiline
                // response is reached
                while (!(s.substring(0,3).equals(code) &&
                        s.substring(3,4).equals(" "))) {
                    printIn(s);
                    s = control_in.readLine();
                }
            }
            printIn(s);
            return parseResponse(s);
        } catch (IOException e) {
            handleError(925);
            return null;
        }
    }

    // print out everything on data socket to stdout
    // until the connection is closed
    private static String dataPrint() {
        try {
            String next;

            while ((next = data_in.readLine()) != null) {
                printIn(next);
            }
            return next;
        } catch (IOException e) {
            handleError(935);
            try {
                data.close();    
            } catch (IOException closeException) {
                handleError(999, "Failed to close socket");
            }
            return null;
        }
    }


    private static Response parseResponse(String str) {
        String[] resp = str.trim().split(" ", 2);

        // dirty hack to account for responses with no message
        // eg. 220 on ftp.swfwmd.state.fl.us
        if (resp.length != 2) {
            resp = Arrays.copyOf(resp, resp.length + 1);
            resp[1] = "";
        }
        return new Response(Integer.parseInt(resp[0]), resp[1]);            
    }

    private static void handleCommonResponse(int code) {
        switch (code) {
            case 500: // Syntax error, command unrecognized. This may include errors such as command line too long.
                break;
            case 501: // Syntax error in parameters or arguments.
                break;
            case 503: // Bad sequence of commands.
                break;
            case 530: // Not logged in.
                break;
            case 550: // Requested action not taken. File unavailable (e.g., file not found, no access).
                break;
            case 421: // Service not available, closing control connection. This may be a reply to any command if the service knows it must shut down.
                handleError(925);
                break;
        }
    }

    private static void printIn(String str) {
        System.out.println(String.format("<-- %s", str));
    }

    private static void printOut(String str) {
        System.out.println(String.format("--> %s", str));
    }

    private static void handleError(int code) {
        switch (code) {
            case 900:
                System.out.println("900 Invalid command.");
                break;
            case 901:
                System.out.println("901 Incorrect number of arguments.");
                break;
            case 925:
                System.out.println("925 Control connection I/O error, closing control connection.");
                try {
                    control.close();
                } catch (IOException closeException) {
                    handleError(999, "Failed to close control socket");
                }
                System.exit(1);
                break;
            case 935:
                System.out.println("935 Data transfer connection I/O error, closing data connection.");
                break;
            case 998:
                System.out.println("998 Input error while reading commands, terminating.");
                break;
        }
    }

    private static void handleError(int code, String msg) {
        switch (code) {
            case 910:
                System.out.println(String.format("910 Access to local file %s denied.", msg));
                break;
            case 999:
                System.out.println(String.format("999 Processing error. %s.", msg));
                break;
        }
    }

    private static void handleError(int code, String hostname, String port) {
        switch (code) {
            case 920:
                System.out.println(String.format("920 Control connection to %s on port %s failed to open", hostname, port));
                System.exit(1);
                break;
            case 930:
                System.out.println(String.format("930 Data transfer connection to %s on port %s failed to open.", hostname, port));
                break;
        }
    }
}
