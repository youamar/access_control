package sec.server;

import sec.common.BasicMessage;
import sec.common.MsgType;

import java.net.ServerSocket;
import java.net.Socket;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.io.IOException;
import java.io.EOFException;

public class BasicServer implements AutoCloseable
{
    private final ServerSocket serv_socket;

    private final ArrayList<BasicMessageHandler> handlers;

    public BasicServer(int listeningPort) throws IOException
    {
        serv_socket = new ServerSocket(listeningPort);
        this.handlers = new ArrayList<>();
    }

    public void registerHandler(
            MsgType msgType,
            BasicMessageHandler handler)
    {
        handlers.add((message, in, out) ->
        {
            try
            {
                if (message.getType() == msgType)
                {
                    handler.handle(message, in, out);
                }
            }
            catch (IOException ex)
            {
                ex.printStackTrace();
            }
        });
    }

    public void start() throws IOException
    {
        System.out.println(
                "Server starting on port " + serv_socket.getLocalPort());

        while (true)
        {
            Socket socket = serv_socket.accept();
            System.out.println("New connection from client");
            new BasicServerThread(socket).start();
        }
    }

    @Override
    public void close() throws Exception
    {
        serv_socket.close();
    }

    public interface BasicMessageHandler
    {
        void handle(
                BasicMessage m, ObjectInputStream in, ObjectOutputStream out)
        throws IOException;
    }

    private class BasicServerThread extends Thread
    {
        protected Socket toClientSocket;
        protected ObjectInputStream messageReader;
        protected ObjectOutputStream messageWriter;

        public BasicServerThread(Socket socket) throws IOException
        {
            this.toClientSocket = socket;
            this.messageReader =
                    new ObjectInputStream(socket.getInputStream());
            this.messageWriter =
                    new ObjectOutputStream(socket.getOutputStream());
        }

        @Override
        public void run()
        {
            boolean exit = false;
            while (!exit)
            {
                try
                {
                    BasicMessage m = (BasicMessage) messageReader.readObject();
                    System.out.println(
                            "Message of type " + m.getType() + " read");
                    if (m.getType() != MsgType.EXIT)
                    // messages
                    {
                        for (BasicMessageHandler h : handlers)
                        {
                            h.handle(m, messageReader, messageWriter);
                        }
                    }
                    else
                    {
                        exit = true;
                    }
                }
                catch (ClassCastException ex)
                {
                    System.err.println(
                            "Unexpected message format, closing session");
                    exit = true;
                }
                catch (EOFException ex)
                {
                    System.err.println(
                            "Connection brutally interrupted, closing session");
                    exit = true;
                }
                catch (Exception ex)
                {
                    System.err.println(
                            "Unknown connection error, closing session");
                    ex.printStackTrace();
                    exit = true;
                }
            }
            try
            {
                messageReader.close();
                messageWriter.close();
                toClientSocket.close();
                System.out.println("Session closed");
            }
            catch (IOException ex)
            {
                System.err.println("Could not close the socket correctly");
                ex.printStackTrace();
            }
        }
    }
}