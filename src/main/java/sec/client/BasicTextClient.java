package sec.client;

import sec.common.BasicMessage;
import sec.common.MsgType;
import sec.common.TextMessage;

import java.io.EOFException;
import java.util.Scanner;
import java.io.ObjectOutputStream;
import java.io.ObjectInputStream;
import java.net.Socket;

public class BasicTextClient
{
    String ip;
    int port;

    public BasicTextClient(String ip, int port)
    {
        this.ip = ip;
        this.port = port;
    }

    public void start()
    {
        try (Socket socket = new Socket(ip, port);
             ObjectOutputStream out =
                     new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream in =
                     new ObjectInputStream(socket.getInputStream()))
        {

            Scanner scanner = new Scanner(System.in);
            String line;
            System.out.print("> ");
            while (scanner.hasNextLine() &&
                   !(line = scanner.nextLine()).equalsIgnoreCase("exit"))
            {
                try
                {
                    int sepIdx = line.indexOf(' ');
                    MsgType msgType = MsgType.valueOf(
                            line.substring(0, sepIdx).toUpperCase());
                    String textData = line.substring(sepIdx + 1);
                    out.writeObject(new TextMessage(textData, msgType));
                    out.flush();
                    System.out.println((String) in.readObject());
                }
                catch (IllegalArgumentException | IndexOutOfBoundsException ex)
                {
                    System.out.println("Unknown command");
                }
                System.out.print("> ");
            }
            System.out.println("Graceful exit");
            out.writeObject(BasicMessage.EXIT_MSG);
        }
        catch (EOFException ex)
        {
            System.err.println("The server unexpectedly closed the connection");
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
        }
    }
}