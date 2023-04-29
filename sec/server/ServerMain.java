package sec.server;

import sec.client.BasicTextClient;
import sec.common.MsgType;
import sec.common.NonReplayableMessage;

import javax.crypto.*;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.crypto.Cipher;
import javax.crypto.spec.PBEKeySpec;

public class ServerMain extends BasicServer {

    private final UserDB userDB;
    List<BasicTextClient> allClientConnected;
    List<Integer> randomValues;
    private PrivateKey privateKey = null;

    public ServerMain(int listeningPort, String filePath) throws IOException {
        super(listeningPort);
        userDB = new UserDB(filePath);
        allClientConnected = new ArrayList<>();
        randomValues = new ArrayList<>();
        registerHandlers();
    }

    public void initKey() throws FileNotFoundException, NoSuchAlgorithmException {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        SecureRandom secureRandom = new SecureRandom();
        keyPairGenerator.initialize(2048, secureRandom);
        KeyPair pair = keyPairGenerator.generateKeyPair();
        PublicKey publicKey = pair.getPublic();
        X509EncodedKeySpec x509EncodedKeySpec = new X509EncodedKeySpec(
                publicKey.getEncoded());
        this.privateKey = pair.getPrivate();
        Path currentRelativePath = Paths.get("");
        String path = currentRelativePath.toAbsolutePath().toString();
        OutputStream out = new FileOutputStream(path + "/public.key");
        try {
            out.write(x509EncodedKeySpec.getEncoded());
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void addClient(BasicTextClient client) {
        if (!allClientConnected.contains(client)) {
            allClientConnected.add(client);
        }
    }

    @Override
    public void start() throws IOException, NoSuchAlgorithmException {
        // Example usage of src.main.java.sec.server.UserDB -->
        // Create an user.
        byte[] password = "password".getBytes();
        byte[] otherData = { 0, 42, 21 };
        UserDB.User admin = new UserDB.User("admin", password, otherData);

        System.out.println("admin user in DB: " + userDB.isRegistered("admin"));

        // Add (if not already present) the user to the database.
        if (userDB.add(admin)) {
            System.out.println("Added dummy admin user");
        }

        // Fetch it back and display data.
        UserDB.User user = userDB.get("admin");
        String readPassword = new String(user.getField(0));
        System.out.println(
                "User " + user + " has password "
                        + readPassword + " and otherData: " +
                        Arrays.toString(user.getField(1)));
        // <-- Example

        initKey();
        super.start();
    }

    private String replyAttack(String messageReceive, String messageToSend, NonReplayableMessage msg)
            throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException, IllegalBlockSizeException,
            IOException, BadPaddingException, ClassNotFoundException {
        addClient(msg.getClient());
        Cipher desc = Cipher.getInstance("RSA");
        desc.init(Cipher.DECRYPT_MODE, privateKey);
        String dec = (String) msg.getSeal().getObject(desc);
        String[] messageValues = dec.split(":");
        boolean canSendBackMessage = true;
        if (messageValues.length > 2) {
            int nonceReturn = Integer.parseInt(messageValues[2]);
            int indiceOfValues = allClientConnected.indexOf(msg.getClient());
            if (nonceReturn - 1 != randomValues.get(indiceOfValues)) {
                canSendBackMessage = false;
                System.out.println("Error operation can't be realized");
            }
        }
        if (canSendBackMessage) {
            int number = Integer.parseInt(messageValues[0]) + 1;
            System.out.println(messageValues[1]);
            SecureRandom random = new SecureRandom();
            int randomValue = random.nextInt(10);
            try {
                randomValues.remove(allClientConnected.indexOf(msg.getClient()));
            } catch (Exception ignore) {

            }
            randomValues.add(allClientConnected.indexOf(msg.getClient()), randomValue);
            if (messageValues[1]
                    .equalsIgnoreCase(messageReceive)) {
                msg.setText(number + ":" + messageToSend + ":" + randomValue);
                return msg.getText();
            } else {
                return number + ":Whatever:" + randomValue;
            }
        } else {
            return "Error operation can't be realized";
        }
    }

    private boolean isConnected(BasicTextClient client) {
        if (allClientConnected.size() > 0) {
            if (allClientConnected.get(allClientConnected.indexOf(client)).getLogin() != null)
                return true;
        }

        return false;
    }

    private void registerHandlers() {
        // handler 1
        registerHandler(MsgType.FATHER, (message, in, out) -> {
            NonReplayableMessage msg = (NonReplayableMessage) message;
            if (isConnected(msg.getClient())) {
                out.writeObject(replyAttack("You killed my father", "No, I am your father", msg));
            } else {
                out.writeObject("Unauthorized");
            }
        });

        // handler 2
        registerHandler(MsgType.HELLO, (message, in, out) -> {
            NonReplayableMessage msg = (NonReplayableMessage) message;
            out.writeObject(replyAttack("Hello there", "General Kenobi!", msg));
        });

        registerHandler(MsgType.REGISTER, (message, in, out) -> {

            NonReplayableMessage msg = (NonReplayableMessage) message;
            addClient(msg.getClient());
            SealedObject seal = msg.getSeal();
            Cipher desc = Cipher.getInstance("RSA");
            desc.init(Cipher.DECRYPT_MODE, privateKey);
            String dec = (String) seal.getObject(desc);
            String[] messageValues = dec.split(":");
            int number = Integer.parseInt(messageValues[0]) + 1;
            boolean canSendBackMessage = true;
            if (messageValues.length > 2) {
                int nonceReturn = Integer.parseInt(messageValues[2]);
                int indiceOfValues = allClientConnected.indexOf(msg.getClient());
                if (nonceReturn - 1 != randomValues.get(indiceOfValues)) {
                    canSendBackMessage = false;
                    System.out.println("Error operation can't be realized");
                }
            }
            if (canSendBackMessage) {
                String[] login = messageValues[1].split(" ");
                try {
                    UserDB.User.validateLogin(login[0]);
                    String passwordUser = login[1];
                    String passwordHashed = hashPBKDF2(passwordUser);
                    if (login.length == 2) {
                        byte[] password = hexStringToByteArray(passwordHashed);
                        UserDB.User newUser = new UserDB.User(login[0], password);
                        System.out.println("user in DB: " + userDB.isRegistered(login[0]));
                        // Add (if not already present) the user to the database.

                        boolean success = userDB.add(newUser);
                        if (success) {
                            System.out.println("Added dummy " + login[0] + " user");
                        }
                        // Fetch it back and display data.
                        SecureRandom random = new SecureRandom();
                        int randomValue = random.nextInt(10);
                        try {
                            randomValues.remove(allClientConnected.indexOf(msg.getClient()));
                        } catch (Exception ignore) {

                        }
                        randomValues.add(allClientConnected.indexOf(msg.getClient()), randomValue);
                        if (success)
                            msg.setText(number + ":" + "Register Success" + ":" + randomValue);
                        else
                            msg.setText(number + ":" + "Login already used" + ":" + randomValue);
                        out.writeObject(msg.getText());
                    } else {
                        out.writeObject("Whatever");
                    }

                } catch (Exception ignored) {
                    out.writeObject("Your login must be in lower case");
                }
            } else {
                out.writeObject("Error operation can't be realized");
            }
        });
        registerHandler(MsgType.LOGIN, (message, in, out) -> {
            NonReplayableMessage msg = (NonReplayableMessage) message;
            addClient(msg.getClient());
            SealedObject seal = msg.getSeal();
            Cipher desc = Cipher.getInstance("RSA");
            desc.init(Cipher.DECRYPT_MODE, privateKey);
            String dec = (String) seal.getObject(desc);
            String[] messageValues = dec.split(":");
            int number = Integer.parseInt(messageValues[0]) + 1;
            boolean canSendBackMessage = true;
            if (messageValues.length > 2) {
                int nonceReturn = Integer.parseInt(messageValues[2]);
                int indiceOfValues = allClientConnected.indexOf(msg.getClient());
                if (nonceReturn - 1 != randomValues.get(indiceOfValues)) {
                    canSendBackMessage = false;
                    System.out.println("Error operation can't be realized");
                }
            }
            if (canSendBackMessage) {
                String[] login = messageValues[1].split(" ");
                try {
                    if (login.length == 2) {
                        String logPseudo = login[0];
                        UserDB.User.validateLogin(logPseudo);
                        if (this.userDB.isRegistered(logPseudo)) {
                            UserDB.User user = this.userDB.get(logPseudo);
                            String passwordHashUser = toHex(user.getField(0));
                            String saltPassword = passwordHashUser.substring(0, 48);
                            String passwordHashSelected = hashPBKDF2(login[1], saltPassword);
                            if (passwordHashSelected.equals(passwordHashUser)) {
                                msg.getClient().setLogin(logPseudo);
                                SecureRandom random = new SecureRandom();
                                int randomValue = random.nextInt(10);
                                try {
                                    randomValues.remove(allClientConnected.indexOf(msg.getClient()));
                                } catch (Exception ignore) {

                                }
                                randomValues.add(allClientConnected.indexOf(msg.getClient()), randomValue);
                                msg.setText(number + ":" + "Login Successful" + ":" + randomValue);
                                out.writeObject(msg.getText());
                            } else {
                                out.writeObject("Attempt Login failed");
                            }
                        } else {
                            out.writeObject("Attempt Login failed");
                        }
                    } else {
                        out.writeObject("Attempt Login failed");
                    }
                } catch (Exception ignored) {
                    out.writeObject("Your login must be in lowercase alphabetic");
                }
            }
        });
        registerHandler(MsgType.DISCONNECT, (message, in, out) -> {
            NonReplayableMessage msg = (NonReplayableMessage) message;
            SealedObject seal = msg.getSeal();
            Cipher desc = Cipher.getInstance("RSA");
            desc.init(Cipher.DECRYPT_MODE, privateKey);
            String dec = (String) seal.getObject(desc);
            String[] messageValues = dec.split(":");
            int number = Integer.parseInt(messageValues[0]) + 1;
            boolean canSendBackMessage = true;
            if (messageValues.length > 2) {
                int nonceReturn = Integer.parseInt(messageValues[2]);
                int indiceOfValues = allClientConnected.indexOf(msg.getClient());
                if (nonceReturn - 1 != randomValues.get(indiceOfValues)) {
                    canSendBackMessage = false;
                    System.out.println("Error operation can't be realized");
                }
            }
            if (canSendBackMessage) {
                String[] login = messageValues[1].split(" ");
                try {
                    String logPseudo = login[0];
                    UserDB.User.validateLogin(logPseudo);
                    if (this.userDB.isRegistered(logPseudo) && this.allClientConnected.contains(msg.getClient())) {
                        SecureRandom random = new SecureRandom();
                        int randomValue = random.nextInt(10);
                        try {
                            randomValues.remove(allClientConnected.indexOf(msg.getClient()));
                        } catch (Exception ignore) {

                        }
                        randomValues.add(allClientConnected.indexOf(msg.getClient()), randomValue);
                        msg.getClient().setLogin(null);
                        msg.setText(number + ":" + "Disconnected" + ":" + randomValue);
                        out.writeObject(msg.getText());
                    } else {
                        out.writeObject("Disconnected fail");
                    }
                } catch (Exception ignored) {
                    out.writeObject("Your login must be in lower case");
                }
            }
        });
        System.out.println("Handlers registered");
    }

    private byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }

    private String hashPBKDF2(String password, String saltValue)
            throws NoSuchAlgorithmException, InvalidKeySpecException {
        byte[] salt = hexStringToByteArray(saltValue);
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 100, 512);
        SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
        byte[] hash = skf.generateSecret(spec).getEncoded();
        return toHex(salt) + toHex(hash) /* saltToString + hashToString */;
    }

    private String toHex(byte[] array) throws NoSuchAlgorithmException {
        StringBuilder result = new StringBuilder();
        for (byte b : array) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }

    private String hashPBKDF2(String password) throws NoSuchAlgorithmException, InvalidKeySpecException {
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[24];
        random.nextBytes(salt);
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 100, 512);
        SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
        byte[] hash = skf.generateSecret(spec).getEncoded();
        return toHex(salt) + toHex(hash);
    }

    @Override
    public void close() throws Exception {
        super.close();
        userDB.close();
    }

    public static void main(String[] args) {
        try (ServerMain server = new ServerMain(42000, "userdb.txt")) {
            server.start();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
