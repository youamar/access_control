package sec.server;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.Base64;
import java.util.NoSuchElementException;
import java.util.StringJoiner;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Pattern;

/**
 * Simple database storing users.
 *
 * The underlying database storage file is encoded in ascii and mostly human
 * readable to ease debugging and manipulation. Each line of the file
 * represents one unique user login. Additional user data fields (byte
 * arrays {@code byte[]}) are stored encoded in Base64 and fields are
 * delimited by commas ':'.
 *
 * This implementation is thread-safe by use of a central read/write lock.
 * Note that there is no implemented mechanism for file-locking on the OS level
 * though.
 */
public class UserDB implements AutoCloseable
{
    protected static final Pattern fieldDelimiter = Pattern.compile(":");
    private final ReentrantReadWriteLock lock =
            new ReentrantReadWriteLock(true);
    private final RandomAccessFile file;

    /**
     * Create an interface to the database stored in the given file.
     *
     * Only one such object should be created at one given time, because
     * total control of the backing file is assumed.
     *
     * The database is opened if the given file exists, and a new file is
     * created for storage if it does not already exist.
     * @param filePath path to the file used as the database store.
     * @throws IOException
     */
    public UserDB(String filePath) throws IOException
    {
        File abstractFile = new File(filePath);
        if (abstractFile.createNewFile())
        {
            System.out.println("Created a new user database file");
        }
        else
        {
            System.out.println(
                    "Opened an already existing user database file");
        }
        file = new RandomAccessFile(abstractFile, "rw");
    }

    /**
     * Add the given user to the database.
     * @param user user object representating the user to add.
     * @return true if user was effectively added, false if was already in
     * the database.
     * @throws IOException
     */
    public boolean add(User user) throws IOException
    {
        lock.writeLock().lock();
        try
        {
            if (isRegistered(user.getLogin()))
            {
                return false;
            }
            long l = file.length();
            file.seek(l);
            String serial = user.asciiSerialize();
            file.writeBytes(serial);
            file.writeByte('\n');
            return true;
        }
        finally
        {
            lock.writeLock().unlock();
        }
    }

    /**
     * Fetch the User model object of the user with the given login from the
     * file database.
     *
     * The login must be lowercase alphabetic.
     * @param login login of the user to fetch.
     * @return a new instance of the User object with data deserialized from
     * the database file.
     * @throws IOException
     */
    public User get(String login) throws IOException
    {
        User.validateLogin(login);
        lock.readLock().lock();
        try
        {
            String serial = findUserSerialization(login);
            if (serial == null)
            {
                throw new NoSuchElementException(
                        "No user with login " + login + " in the database");
            }
            return User.asciiSerialize(serial);
        }
        finally
        {
            lock.readLock().unlock();
        }
    }

    /**
     * Check whether the user login appears in the database.
     * @param login user login to check for registration.
     * @return true if the user already exists, false otherwise.
     * @throws IOException
     */
    public boolean isRegistered(String login) throws IOException
    {
        return findUserSerialization(login) != null;
    }

    private String findUserSerialization(String login) throws IOException
    {
        file.seek(0);
        try
        {
            while (true)
            {
                String line = file.readLine();
                if (line == null)
                {
                    return null;
                }
                String lineLogin = fieldDelimiter.split(line, 2)[0];
                if (lineLogin.equals(login))
                {
                    return line;
                }
            }
        }
        catch (EOFException ex)
        {
            return null;
        }
    }


    /**
     * Closes the database and releases any system resources associated with
     * it.
     * Once the database has been closed, further invocations reading
     * or writing to the database will throw an IOException. Closing a
     * previously closed database has no effect.
     * <p>
     * This method is invoked automatically on objects managed by the
     * try-with-resources statement.
     *
     * @throws Exception
     */
    @Override
    public void close() throws Exception
    {
        lock.writeLock().lock();
        try
        {
            file.close();
        }
        finally
        {
            lock.writeLock().unlock();
        }
    }

    /**
     * Model of a user on the server-side.
     * <p>
     * A User has a login and maybe some additional data fields.
     * <p>
     * The login is a lowercase alphabetic String. The additional data fields
     * are simple arrays of bytes, so any kind of additional data can be
     * encoded there. The number of fields is unspecified.
     */
    static public class User
    {
        static private final Pattern loginPattern = Pattern.compile("[a-z]+");
        private final String login;
        private final byte[][] fields;

        /**
         * Create a user to be stored in the database.
         * <p>
         * Note,  fields argument is a "varargs" (variable arguments). The
         * number of such fields arguments is variable. You may supply none
         * {@code new User("joe")}, or supply many
         * {@code new User("joe", bytefield1, bytefield2, bytefield3)}
         *
         * @param login  a lowercase alphabetic username/login
         * @param fields an arbitrary number of fields, each made of a
         *               byte array {@code byte[]}.
         */
        public User(String login, byte[]... fields)
        {
            validateLogin(login);
            this.login = login;
            this.fields = fields;
        }

        static void validateLogin(String login)
        {
            if (!loginPattern.matcher(login).matches())
            {
                throw new IllegalArgumentException(
                        "login must be lowercase alphabetic");
            }
        }

        public String getLogin()
        {
            return login;
        }

        public int nbFields()
        {
            return fields.length;
        }

        /**
         * Retrieve the data field with given index.
         *
         * @param index index of the data field in the order they were
         *              supplied to the constructor.
         * @return reference to the corresponding data field.
         */
        public byte[] getField(int index)
        {
            return fields[index];
        }

        @Override
        public String toString()
        {
            // Do not display the fields in toString to avoid
            // leakage of critical information in debug logs.
            return login;
        }

        /**
         * Internal ascii serialization.
         *
         * @return ascii representation of this User object to be stored in
         * the database file.
         */
        private String asciiSerialize()
        {
            Base64.Encoder b64 = Base64.getEncoder();
            StringJoiner sj = new StringJoiner(fieldDelimiter.pattern());
            sj.add(login);
            for (byte[] field : fields)
            {
                sj.add(b64.encodeToString(field));
            }
            return sj.toString();
        }

        /**
         * Internal ascii deserialization.
         *
         * @param serializedUser
         * @return
         */
        protected static User asciiSerialize(String serializedUser)
        {
            Base64.Decoder b64 = Base64.getDecoder();
            String[] data = fieldDelimiter.split(serializedUser);
            String login = data[0];
            byte[][] fields =
                    Arrays.stream(data).skip(1).map(b64::decode)
                          .toArray(byte[][]::new);
            return new User(login, fields);
        }
    }
}
