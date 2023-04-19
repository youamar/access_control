package sec.common;

import java.io.Serializable;

public class TextMessage extends BasicMessage implements Serializable
{
    private final String txt;

    public TextMessage(String txt, MsgType type)
    {
        super(type);
        this.txt = txt;
    }

    public String getText()
    {
        return txt;
    }
}
