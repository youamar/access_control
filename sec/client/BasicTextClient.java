package sec.client;

import sec.common.BasicMessage;
import sec.common.MsgType;
import sec.common.NonReplayableMessage;

import java.io.*;
import java.security.*;
import java.util.Base64;
import java.util.Scanner;
import java.net.Socket;

public class BasicTextClient implements Serializable {
    String ip;
    int port;
    String login;
    int randomValue;
    int yValue;

    public BasicTextClient(String ip, int port) {
        this.ip = ip;
        this.port = port;
        yValue = -1;
    }

    private static String encode(byte[] data) {
        return Base64.getEncoder().encodeToString(data);
    }

    public void start() {
        try (Socket socket = new Socket(ip, port);
             ObjectOutputStream out =
                     new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream in =
                     new ObjectInputStream(socket.getInputStream())) {

            Scanner scanner = new Scanner(System.in);
            String line;
            System.out.print("> ");
            while (scanner.hasNextLine() &&
                    !(line = scanner.nextLine()).equalsIgnoreCase("exit")) {
                try {
                    int sepIdx = line.indexOf(' ');
                    MsgType msgType = MsgType.valueOf(
                            line.substring(0, sepIdx).toUpperCase());
                    String textData = line.substring(sepIdx + 1);
                    SecureRandom secureRandom = new SecureRandom();
                    randomValue = secureRandom.nextInt(10);
                    NonReplayableMessage t = null;
                    t = new NonReplayableMessage(textData, msgType, randomValue, yValue,this);
                    out.writeObject(t);
                    out.flush();
                    String response = (String) in.readObject();
                    String[] splitResponse = response.split(":");
                    if (splitResponse.length == 1) {
                        System.out.println(splitResponse[0]);
                    } else {
                        if (splitResponse.length > 2) {
                            yValue = Integer.parseInt(splitResponse[2])+1;
                        }
                        if (Integer.parseInt(splitResponse[0]) - 1 != randomValue) {
                            System.out.println("There has been a failure REPLAY ATTACK");
                        } else {
                            System.out.println(splitResponse[1]);
                        }
                    }
                } catch (IllegalArgumentException | IndexOutOfBoundsException ex) {
                    System.out.println("Unknown command");
                }
                System.out.print("> ");
            }
            System.out.println("Graceful exit");
            out.writeObject(BasicMessage.EXIT_MSG);
        } catch (EOFException ex) {
            System.err.println("The server unexpectedly closed the connection");
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public void setLogin(String login) {
        this.login = login;
    }

    public String getLogin() {
        return login;
    }
}