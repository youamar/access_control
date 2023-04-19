package sec.common;

import java.io.Serializable;

public class BasicMessage implements Serializable
{
    private final MsgType type;

    private static int id_count = 0;
    private final int id;

    public static final BasicMessage EXIT_MSG =
            new BasicMessage(MsgType.EXIT);

    public BasicMessage(MsgType type)
    {
        this.type = type;

        this.id = id_count;
        BasicMessage.id_count++;
    }

    public int getId()
    {
        return id;
    }

    public MsgType getType()
    {
        return type;
    }
}
