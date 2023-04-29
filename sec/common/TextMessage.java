package sec.common;

import java.io.Serializable;
import java.util.List;

public class TextMessage extends BasicMessage implements Serializable
{
    private final String txt;

    public List<Object> getList() {
        return values;
    }

    private List<Object> values;

    public TextMessage(String txt, MsgType type)
    {
        super(type);
        this.txt = txt;
        this.values = null;
    }
    public TextMessage(List<Object> value, MsgType type){
        super(type);
        this.values = value;
        this.txt = null;
    }
    public String getText()
    {
        return txt;
    }
}
