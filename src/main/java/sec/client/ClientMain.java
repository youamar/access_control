package sec.client;

public class ClientMain
{
    public static void main(String[] args)
    {
        new BasicTextClient("localhost", 42000).start();
    }
}
