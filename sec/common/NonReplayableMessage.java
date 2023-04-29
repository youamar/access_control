package sec.common;

import sec.client.BasicTextClient;

import javax.crypto.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.Serializable;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.nio.file.Path;
import java.nio.file.Paths;

public class NonReplayableMessage extends BasicMessage implements Serializable {
    private String text;
    private SealedObject seal;
    private PublicKey publicKey;
    public BasicTextClient getClient() {
        return client;
    }

    private BasicTextClient client;

    public SealedObject getSeal() {
        return seal;
    }

    private PublicKey getPublicKey() throws IOException, GeneralSecurityException {
        Path currentRelativePath = Paths.get("");
        String path = currentRelativePath.toAbsolutePath().toString();
        File filePublicKey = new File(path + "/public.key");
        FileInputStream fis = new FileInputStream(path + "/public.key");
        byte[] encodedPublicKey = new byte[(int) filePublicKey.length()];
        fis.read(encodedPublicKey);
        fis.close();
        KeyFactory key = KeyFactory.getInstance("RSA");
        X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(
                encodedPublicKey);
        return key.generatePublic(publicKeySpec);
    }
    public NonReplayableMessage(String txt, MsgType type, int randomNumber, int yValue, BasicTextClient client) throws IllegalBlockSizeException, NoSuchPaddingException, BadPaddingException, NoSuchAlgorithmException, IOException, InvalidKeyException, ClassNotFoundException {
        super(type);
        this.client = client;
        this.text = randomNumber + ":" + txt;
        if (yValue != -1)
            text += ":" + yValue;
        try {
            this.publicKey = getPublicKey();
        } catch (GeneralSecurityException e) {
            e.printStackTrace();
        }
        RSA(text);
    }

    public String getText() {
        return text;
    }

    public void RSA(String message) throws IllegalBlockSizeException, BadPaddingException, NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException, IOException, ClassNotFoundException {
        Cipher encryptionCipher = Cipher.getInstance("RSA");
        encryptionCipher.init(Cipher.ENCRYPT_MODE, publicKey);
        SealedObject seal = new SealedObject(message, encryptionCipher);
        this.seal = seal;
    }

    public void setText(String text) {
        SecureRandom secureRandom = new SecureRandom();
        text += ":" + secureRandom.nextInt(10) + ":";
        this.text = text;
    }
}
